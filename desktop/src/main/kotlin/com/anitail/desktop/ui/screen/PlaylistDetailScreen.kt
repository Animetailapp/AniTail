package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import androidx.compose.ui.unit.sp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toPlaylistEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.indexPrimaryArtistIdBySongId
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerBox
import com.anitail.desktop.ui.component.ShimmerListItem
import com.anitail.desktop.ui.screen.library.isSpecialRemotePlaylist
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.Album
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.PlaylistPage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de detalle de playlist para Desktop - Idéntica a Android.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAlbumClick: (String, String) -> Unit = { _, _ -> },
    database: DesktopDatabase = DesktopDatabase.getInstance(),
    downloadService: DesktopDownloadService = DesktopDownloadService(),
) {
    var playlistPage by remember { mutableStateOf<PlaylistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()

    val songsById by database.songsById.collectAsState()
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val primaryArtistBySongId = remember(songArtistMaps) { indexPrimaryArtistIdBySongId(songArtistMaps) }
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val visiblePlaylists = remember(playlists) { playlists.filterNot { isSpecialRemotePlaylist(it) } }
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadedSongIds = remember(downloadedSongs) {
        downloadedSongs.mapTo(HashSet(downloadedSongs.size)) { it.songId }
    }
    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var pendingCollectionTargets by remember { mutableStateOf<List<BrowseSongTarget>?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var headerMenuExpanded by remember { mutableStateOf(false) }

    val topBarHeight = 56.dp
    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }

    // Local playlist observables
    val localPlaylist by database.playlist(playlistId).collectAsState(initial = null)
    val localPlaylistId = localPlaylist?.id ?: playlistId
    val localSongs by database.songsInPlaylist(localPlaylistId).collectAsState(initial = emptyList())
    val remoteBrowseId = remember(playlistId, localPlaylist?.browseId) {
        localPlaylist?.browseId?.takeIf { it.isNotBlank() }
            ?: playlistId.takeIf { !it.startsWith("LP") }
    }
    val isPurelyLocalPlaylist = localPlaylist != null && remoteBrowseId == null

    LaunchedEffect(playlistId, isPurelyLocalPlaylist, remoteBrowseId) {
        if (isPurelyLocalPlaylist || remoteBrowseId == null) {
            isLoading = false
            error = null
            playlistPage = null
            return@LaunchedEffect
        }

        val targetId = remoteBrowseId
        isLoading = true
        error = null
        try {
            val (initialPage, initialError) = fetchInitialPlaylistPage(
                playlistId = targetId,
                playlistName = playlistName,
                strings = strings,
            )
            playlistPage = initialPage
            error = initialError
            isLoading = false

            if (initialPage != null) {
                val accumulatedSongs = initialPage.songs.toMutableList()
                var continuation = initialPage.songsContinuation ?: initialPage.continuation
                var pages = 0
                while (!continuation.isNullOrBlank() && pages < 100) {
                    val currentToken = continuation
                    val nextPage = YouTube.playlistContinuation(currentToken).getOrNull() ?: break
                    if (nextPage.songs.isNotEmpty()) {
                        accumulatedSongs += nextPage.songs
                    }
                    val nextToken = nextPage.continuation?.takeIf { it != currentToken }
                    continuation = nextToken
                    playlistPage = initialPage.copy(
                        songs = accumulatedSongs.toList(),
                        songsContinuation = null,
                        continuation = nextToken,
                    )
                    pages += 1
                }

                val normalizedId = targetId.removePrefix("VL")
                if (accumulatedSongs.isNotEmpty() && normalizedId != "LM" && normalizedId != "SE") {
                    withContext(Dispatchers.IO) {
                        val syncedLocal = database.playlist(targetId).first() ?: localPlaylist
                        if (syncedLocal != null) {
                            val batch = accumulatedSongs.map { song ->
                                song.toSongEntity(inLibrary = false) to song.toSongArtistMaps()
                            }
                            database.insertSongsBatch(batch)
                            database.replacePlaylistSongs(syncedLocal.id, accumulatedSongs.map { it.id })
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: strings.get("error_loading_playlist")
        } finally {
            isLoading = false
        }
    }

    // Filtrar canciones remotas
    val filteredSongs: List<Pair<Int, SongItem>> = remember(playlistPage?.songs, searchQuery) {
        val songs: List<SongItem> = playlistPage?.songs ?: emptyList()
        if (searchQuery.isEmpty()) songs.mapIndexed { idx, s -> idx to s }
        else songs.mapIndexed { idx, s -> idx to s }
            .filter { (_, s) -> s.title.contains(searchQuery, ignoreCase = true) || s.artists.any { it.name.contains(searchQuery, ignoreCase = true) } }
    }

    // Filtrar canciones locales
    val filteredLocalSongs: List<Pair<Int, com.anitail.desktop.db.entities.SongEntity>> = remember(localSongs, searchQuery) {
        if (searchQuery.isBlank()) localSongs.mapIndexed { idx, s -> idx to s }
        else localSongs.mapIndexed { idx, s -> idx to s }
            .filter { (_, s) -> s.title.contains(searchQuery, ignoreCase = true) || (s.artistName?.contains(searchQuery, ignoreCase = true) == true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = topBarHeight),
            modifier = Modifier.fillMaxSize(),
        ) {
            val remotePage = playlistPage
            if (remotePage != null) {
                if (!isSearching) {
                    item(key = "header") {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp))
                                ) {
                                    RemoteImage(
                                        url = remotePage.playlist.thumbnail,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = remotePage.playlist.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 22.sp,
                                    )

                                    remotePage.playlist.author?.let { artist ->
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (artist.id != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.clickable(enabled = artist.id != null) { artist.id?.let { onArtistClick(it, artist.name) } },
                                        )
                                    }

                                    remotePage.playlist.songCountText?.let { songCountText ->
                                        Text(text = songCountText, style = MaterialTheme.typography.titleMedium)
                                    } ?: Text(text = pluralStringResource("n_song", remotePage.songs.size, remotePage.songs.size), style = MaterialTheme.typography.titleMedium)

                                    Row {
                                        val isPlaylistLiked = localPlaylist?.bookmarkedAt != null
                                        val togglePlaylistLike: () -> Unit = {
                                            coroutineScope.launch {
                                                val existing = localPlaylist
                                                if (existing == null) {
                                                    database.insertPlaylist(
                                                        remotePage.playlist.toPlaylistEntity().localToggleLike()
                                                    )
                                                } else {
                                                    database.togglePlaylistBookmark(existing.id)
                                                }
                                            }
                                        }
                                        val collectionTargets = remember(remotePage.songs) {
                                            remotePage.songs.map { BrowseSongTarget(songItemToLibraryItem(it), it) }
                                        }
                                        val downloadMenu = resolveCollectionDownloadMenuState(
                                            strings = strings,
                                            songIds = remotePage.songs.map { it.id },
                                            downloadStates = downloadStates,
                                            downloadedSongIds = downloadedSongIds,
                                            showWhenEmpty = false,
                                        )

                                        if (remotePage.playlist.id != "LM") {
                                            IconButton(onClick = togglePlaylistLike) {
                                                Icon(
                                                    imageVector = if (isPlaylistLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                                    contentDescription = null,
                                                    tint = if (isPlaylistLiked) MaterialTheme.colorScheme.error else LocalContentColor.current
                                                )
                                            }
                                        }

                                        Box {
                                            IconButton(onClick = { headerMenuExpanded = true }) {
                                                Icon(IconAssets.moreVert(), contentDescription = stringResource("more_options"))
                                            }
                                            ItemContextMenu(
                                                expanded = headerMenuExpanded,
                                                onDismiss = { headerMenuExpanded = false },
                                                item = remotePage.playlist.toPlaylistEntity().toLibraryItem(),
                                                actions = buildPlaylistHeaderMenuActions(
                                                    strings = strings,
                                                    canPlay = remotePage.songs.isNotEmpty(),
                                                    canShuffle = remotePage.playlist.shuffleEndpoint != null || remotePage.songs.isNotEmpty(),
                                                    canRadio = remotePage.playlist.radioEndpoint != null || remotePage.songs.isNotEmpty(),
                                                    downloadMenu = downloadMenu,
                                                    targets = collectionTargets,
                                                    songsById = songsById,
                                                    playerState = playerState,
                                                    downloadService = downloadService,
                                                    coroutineScope = coroutineScope,
                                                    shareUrl = remotePage.playlist.shareLink,
                                                    onRequestAddToPlaylist = { pendingCollectionTargets = collectionTargets },
                                                ),
                                                headerContent = {
                                                    CollectionMenuHeader(
                                                        title = remotePage.playlist.title,
                                                        subtitle = remotePage.playlist.songCountText
                                                            ?: pluralStringResource("n_song", remotePage.songs.size, remotePage.songs.size),
                                                        thumbnailUrl = remotePage.playlist.thumbnail,
                                                        showLike = remotePage.playlist.id != "LM",
                                                        isLiked = isPlaylistLiked,
                                                        onToggleLike = togglePlaylistLike,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (remotePage.playlist.shuffleEndpoint != null || remotePage.songs.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            val songs = remotePage.songs.shuffled().map { songItemToLibraryItem(it) }
                                            if (songs.isNotEmpty()) {
                                                playerState.shuffleEnabled = true
                                                playerState.playQueue(songs, 0)
                                            }
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(IconAssets.shuffle(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource("shuffle"))
                                    }
                                }

                                remotePage.playlist.radioEndpoint?.let {
                                    OutlinedButton(
                                        onClick = {
                                            val firstSong = remotePage.songs.firstOrNull() ?: return@OutlinedButton
                                            coroutineScope.launch {
                                                val result = YouTube.next(WatchEndpoint(videoId = firstSong.id)).getOrNull()
                                                    ?: return@launch
                                                val plan = buildRadioQueuePlan(songItemToLibraryItem(firstSong), result)
                                                if (plan.items.isNotEmpty()) {
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                            }
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(IconAssets.radio(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource("radio"))
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredSongs.isEmpty() && error == null) {
                    item(key = "empty-playlist") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                        ) {
                            Text(
                                text = stringResource("sound_capsule_no_data"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(items = filteredSongs, key = { (index, song) -> "${index}_${song.id}" }) { (originalIndex, song) ->
                    val libraryItem = remember(song) { songItemToLibraryItem(song) }
                    val songEntity = songsById[song.id]
                    val songDownloadState = downloadStates[song.id]
                    val isSongDownloaded = song.id in downloadedSongIds
                    val canRemoveFromPlaylist =
                        (localPlaylist != null && (isPurelyLocalPlaylist || localPlaylist?.isEditable == true)) ||
                            (remotePage.playlist.isEditable && song.setVideoId != null)
                    val menuActions = remember(
                        song.id,
                        songEntity,
                        primaryArtistBySongId[song.id],
                        songDownloadState,
                        isSongDownloaded,
                        canRemoveFromPlaylist,
                        strings,
                    ) {
                        val base = buildBrowseSongMenuActions(
                            strings = strings,
                            libraryItem = libraryItem,
                            songItem = song,
                            songsById = songsById,
                            primaryArtistBySongId = primaryArtistBySongId,
                            downloadStates = downloadStates,
                            downloadedSongIds = downloadedSongIds,
                            database = database,
                            downloadService = downloadService,
                            playerState = playerState,
                            coroutineScope = coroutineScope,
                            onOpenArtist = { id, name -> onArtistClick(id, name.orEmpty()) },
                            onOpenAlbum = { id, name -> onAlbumClick(id, name.orEmpty()) },
                            onRequestPlaylist = { pendingPlaylistItem = it },
                            onRequestArtists = { pendingArtists = it },
                            onShowDetails = { detailsItem = it },
                            copyToClipboard = ::copyTextToClipboard,
                        )
                        if (canRemoveFromPlaylist) {
                            base + ContextMenuAction(
                                label = strings.get("remove_from_playlist"),
                                icon = IconAssets.delete(),
                                onClick = {
                                    coroutineScope.launch {
                                        if (localPlaylist != null) {
                                            database.removeSongFromPlaylist(localPlaylistId, song.id)
                                        }
                                        val setVideoId = song.setVideoId
                                        if (remoteBrowseId != null && setVideoId != null) {
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    YouTube.removeFromPlaylist(
                                                        remoteBrowseId.removePrefix("VL"),
                                                        song.id,
                                                        setVideoId,
                                                    )
                                                }
                                            }
                                        }
                                        playlistPage = playlistPage?.let { current ->
                                            current.copy(
                                                songs = current.songs.filterIndexed { idx, _ -> idx != originalIndex }
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            base
                        }
                    }

                    YouTubeListItem(
                        item = song,
                        libraryItem = libraryItem,
                        isActive = playerState.currentItem?.id == song.id,
                        isPlaying = playerState.isPlaying,
                        menuActions = menuActions,
                        menuHeader = { onDismiss ->
                            SongMenuHeader(
                                title = song.title,
                                subtitle = formatBulletSubtitle(
                                    song.artists.joinToString { it.name },
                                    song.duration?.let { formatTime(it * 1000L) },
                                ).orEmpty(),
                                thumbnailUrl = song.thumbnail,
                                isLiked = songEntity?.liked == true,
                                onToggleLike = {
                                    coroutineScope.launch {
                                        if (songEntity == null) {
                                            database.insertSong(
                                                song.toSongEntity(inLibrary = true).toggleLike(),
                                                song.toSongArtistMaps(),
                                            )
                                        } else {
                                            database.toggleSongLike(song.id)
                                        }
                                    }
                                },
                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(libraryItem, song) },
                                onShare = { copyTextToClipboard(song.shareLink) },
                                onDismiss = onDismiss,
                            )
                        },
                        downloadState = songDownloadState,
                        isDownloaded = isSongDownloaded,
                        onClick = {
                            if (playerState.currentItem?.id == song.id) {
                                playerState.togglePlayPause()
                            } else {
                                val songs = remotePage.songs.map { songItemToLibraryItem(it) }
                                playerState.playQueue(songs, originalIndex)
                            }
                        },
                        onArtistClick = onArtistClick,
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            } else if (localPlaylist != null && (isPurelyLocalPlaylist || (!isLoading && localSongs.isNotEmpty()))) {
                val currentLocalPlaylist = localPlaylist!!
                if (!isSearching) {
                    item(key = "header") {
                        val collectionTargets = remember(localSongs) {
                            localSongs.map { BrowseSongTarget(it.toLibraryItem(), null) }
                        }
                        val downloadMenu = resolveCollectionDownloadMenuState(
                            strings = strings,
                            songIds = localSongs.map { it.id },
                            downloadStates = downloadStates,
                            downloadedSongIds = downloadedSongIds,
                            showWhenEmpty = false,
                        )
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp))
                                ) {
                                    RemoteImage(
                                        url = currentLocalPlaylist.thumbnailUrl?.takeIf { it.isNotBlank() }
                                            ?: localSongs.firstOrNull()?.thumbnailUrl
                                            ?: "",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = currentLocalPlaylist.name.ifBlank { playlistName },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 22.sp,
                                    )

                                    Text(
                                        text = pluralStringResource("n_song", localSongs.size, localSongs.size),
                                        style = MaterialTheme.typography.titleMedium,
                                    )

                                    Row {
                                        Box {
                                            IconButton(onClick = { headerMenuExpanded = true }) {
                                                Icon(IconAssets.moreVert(), contentDescription = stringResource("more_options"))
                                            }
                                            ItemContextMenu(
                                                expanded = headerMenuExpanded,
                                                onDismiss = { headerMenuExpanded = false },
                                                item = currentLocalPlaylist.toLibraryItem(),
                                                actions = buildPlaylistHeaderMenuActions(
                                                    strings = strings,
                                                    canPlay = localSongs.isNotEmpty(),
                                                    canShuffle = localSongs.isNotEmpty(),
                                                    canRadio = localSongs.isNotEmpty(),
                                                    downloadMenu = downloadMenu,
                                                    targets = collectionTargets,
                                                    songsById = songsById,
                                                    playerState = playerState,
                                                    downloadService = downloadService,
                                                    coroutineScope = coroutineScope,
                                                    shareUrl = currentLocalPlaylist.shareLink.orEmpty(),
                                                    onRequestAddToPlaylist = { pendingCollectionTargets = collectionTargets },
                                                ),
                                                headerContent = {
                                                    CollectionMenuHeader(
                                                        title = currentLocalPlaylist.name,
                                                        subtitle = pluralStringResource("n_song", localSongs.size, localSongs.size),
                                                        thumbnailUrl = currentLocalPlaylist.thumbnailUrl?.takeIf { it.isNotBlank() }
                                                            ?: localSongs.firstOrNull()?.thumbnailUrl,
                                                        showLike = !currentLocalPlaylist.isEditable && currentLocalPlaylist.browseId != null,
                                                        isLiked = currentLocalPlaylist.bookmarkedAt != null,
                                                        onToggleLike = {
                                                            coroutineScope.launch {
                                                                database.togglePlaylistBookmark(currentLocalPlaylist.id)
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            if (localSongs.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val songs = localSongs.shuffled().map { it.toLibraryItem() }
                                        if (songs.isNotEmpty()) {
                                            playerState.shuffleEnabled = true
                                            playerState.playQueue(songs, 0)
                                        }
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(IconAssets.shuffle(), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource("shuffle"))
                                }
                            }
                        }
                    }
                }

                if (filteredLocalSongs.isEmpty() && !isLoading) {
                    item(key = "empty-local-playlist") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                        ) {
                            Text(
                                text = stringResource("sound_capsule_no_data"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(items = filteredLocalSongs, key = { (index, song) -> "${index}_${song.id}" }) { (originalIndex, song) ->
                    val libraryItem = remember(song) { song.toLibraryItem() }
                    val primaryArtistId = primaryArtistBySongId[song.id]
                    val songEntity = songsById[song.id] ?: song
                    val songDownloadState = downloadStates[song.id]
                    val isSongDownloaded = song.id in downloadedSongIds
                    val item = remember(song, primaryArtistId) {
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = listOf(Artist(song.artistName.orEmpty(), primaryArtistId)),
                            album = song.albumId?.let { Album(song.albumName.orEmpty(), it) },
                            duration = song.duration.takeIf { it > 0 },
                            chartPosition = null,
                            chartChange = null,
                            thumbnail = song.thumbnailUrl.orEmpty(),
                            explicit = song.explicit,
                            endpoint = null,
                            setVideoId = null,
                        )
                    }
                    val canRemoveFromLocal = isPurelyLocalPlaylist || currentLocalPlaylist.isEditable
                    val menuActions = remember(
                        song.id,
                        songEntity,
                        primaryArtistId,
                        songDownloadState,
                        isSongDownloaded,
                        canRemoveFromLocal,
                        strings,
                    ) {
                        val base = buildBrowseSongMenuActions(
                            strings = strings,
                            libraryItem = libraryItem,
                            songItem = null,
                            songsById = songsById,
                            primaryArtistBySongId = primaryArtistBySongId,
                            downloadStates = downloadStates,
                            downloadedSongIds = downloadedSongIds,
                            database = database,
                            downloadService = downloadService,
                            playerState = playerState,
                            coroutineScope = coroutineScope,
                            onOpenArtist = { id, name -> onArtistClick(id, name.orEmpty()) },
                            onOpenAlbum = { id, name -> onAlbumClick(id, name.orEmpty()) },
                            onRequestPlaylist = { pendingPlaylistItem = it },
                            onRequestArtists = { pendingArtists = it },
                            onShowDetails = { detailsItem = it },
                            copyToClipboard = ::copyTextToClipboard,
                        )
                        if (canRemoveFromLocal) {
                            base + ContextMenuAction(
                                label = strings.get("remove_from_playlist"),
                                icon = IconAssets.delete(),
                                onClick = {
                                    coroutineScope.launch {
                                        database.removeSongFromPlaylist(localPlaylistId, song.id)
                                    }
                                },
                            )
                        } else {
                            base
                        }
                    }

                    YouTubeListItem(
                        item = item,
                        libraryItem = libraryItem,
                        isActive = playerState.currentItem?.id == song.id,
                        isPlaying = playerState.isPlaying,
                        menuActions = menuActions,
                        menuHeader = { onDismiss ->
                            SongMenuHeader(
                                title = song.title,
                                subtitle = formatBulletSubtitle(
                                    song.artistName,
                                    song.duration.takeIf { it > 0 }?.let { formatTime(it * 1000L) },
                                ).orEmpty(),
                                thumbnailUrl = song.thumbnailUrl,
                                isLiked = songEntity.liked,
                                onToggleLike = {
                                    coroutineScope.launch {
                                        database.toggleSongLike(song.id)
                                    }
                                },
                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(libraryItem, null) },
                                onShare = { copyTextToClipboard(libraryItem.playbackUrl) },
                                onDismiss = onDismiss,
                            )
                        },
                        downloadState = songDownloadState,
                        isDownloaded = isSongDownloaded,
                        onClick = {
                            if (playerState.currentItem?.id == song.id) {
                                playerState.togglePlayPause()
                            } else {
                                val songs = localSongs.map { it.toLibraryItem() }
                                playerState.playQueue(songs, originalIndex)
                            }
                        },
                        onArtistClick = onArtistClick,
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isLoading && playlistPage == null) {
                item { PlaylistShimmerPlaceholder() }
            }

            if (localSongs.isEmpty()) {
                error?.let { errorMsg ->
                    item {
                        Text(text = errorMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }

        // TopAppBar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(topBarHeight).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = {
                if (isSearching) {
                    isSearching = false
                    searchQuery = ""
                } else {
                    onBack()
                }
            }) {
                Icon(IconAssets.arrowBack(), contentDescription = stringResource("back"))
            }

            if (isSearching) {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text(stringResource("search") + "...") }, singleLine = true, modifier = Modifier.weight(1f))
            } else if (showTopBarTitle) {
                Text(text = playlistPage?.playlist?.title ?: playlistName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (!isSearching) {
                IconButton(onClick = { isSearching = true }) {
                    Icon(IconAssets.search(), contentDescription = stringResource("search"))
                }
            }
        }

        CollectionSongDialogs(
            pendingPlaylistItem = pendingPlaylistItem,
            pendingCollectionTargets = pendingCollectionTargets,
            pendingArtists = pendingArtists,
            detailsItem = detailsItem,
            playlists = visiblePlaylists,
            songsById = songsById,
            database = database,
            coroutineScope = coroutineScope,
            onDismissPendingPlaylistItem = { pendingPlaylistItem = null },
            onDismissPendingCollectionTargets = { pendingCollectionTargets = null },
            onSelectArtist = onArtistClick,
            onDismissArtists = { pendingArtists = emptyList() },
            onDismissDetails = { detailsItem = null },
        )
    }
}

private fun buildPlaylistHeaderMenuActions(
    strings: StringResolver,
    canPlay: Boolean,
    canShuffle: Boolean,
    canRadio: Boolean,
    downloadMenu: CollectionDownloadMenuState?,
    targets: List<BrowseSongTarget>,
    songsById: Map<String, SongEntity>,
    playerState: PlayerState,
    downloadService: DesktopDownloadService,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    shareUrl: String,
    onRequestAddToPlaylist: () -> Unit,
): List<ContextMenuAction> {
    val items = targets.map { it.item }
    return buildBrowsePlaylistMenuActions(
        strings = strings,
        canPlay = canPlay,
        canShuffle = canShuffle,
        canRadio = canRadio,
        showDownload = downloadMenu != null,
        downloadLabel = downloadMenu?.label ?: strings.get("download"),
        downloadEnabled = true,
        onPlay = {
            if (items.isNotEmpty()) {
                playerState.playQueue(items, startIndex = 0)
            }
        },
        onShuffle = {
            if (items.isNotEmpty()) {
                playerState.shuffleEnabled = true
                playerState.playQueue(items.shuffled(), startIndex = 0)
            }
        },
        onStartRadio = {
            val firstSong = items.firstOrNull() ?: return@buildBrowsePlaylistMenuActions
            coroutineScope.launch {
                val result = YouTube.next(WatchEndpoint(videoId = firstSong.id)).getOrNull() ?: return@launch
                val plan = buildRadioQueuePlan(firstSong, result)
                if (plan.items.isNotEmpty()) {
                    playerState.playQueue(plan.items, plan.startIndex)
                }
            }
        },
        onPlayNext = {
            items.asReversed().forEach { playerState.addToQueue(it, playNext = true) }
        },
        onAddToQueue = {
            items.forEach { playerState.addToQueue(it) }
        },
        onAddToPlaylist = onRequestAddToPlaylist,
        onDownload = {
            val menu = downloadMenu ?: return@buildBrowsePlaylistMenuActions
            performCollectionDownloadAction(
                action = menu.action,
                targets = targets,
                songsById = songsById,
                fallbackAlbumName = null,
                downloadService = downloadService,
                coroutineScope = coroutineScope,
            )
        },
        onShare = { copyTextToClipboard(shareUrl) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeListItem(
    item: SongItem,
    libraryItem: LibraryItem,
    isActive: Boolean,
    isPlaying: Boolean,
    menuActions: List<ContextMenuAction>,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    val menuExpanded = remember(item.id) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (menuActions.isNotEmpty()) {
                        menuExpanded.value = true
                    }
                },
            )
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            RemoteImage(url = item.thumbnail, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
            PlayingIndicatorBox(
                isActive = isActive,
                isPlaying = isPlaying,
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row {
                if (item.explicit) {
                    Text(text = stringResource("explicit_badge") + " ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.artists.forEachIndexed { index, artist ->
                    Text(text = artist.name, style = MaterialTheme.typography.bodySmall, color = if (artist.id != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(enabled = artist.id != null) { artist.id?.let { onArtistClick(it, artist.name) } })
                    if (index < item.artists.lastIndex) {
                        Text(text = stringResource("list_separator"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        DownloadStatusTrailingIcon(downloadState = downloadState, isDownloaded = isDownloaded)

        item.duration?.let { duration ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box {
            IconButton(
                onClick = {
                    if (menuActions.isNotEmpty()) {
                        menuExpanded.value = true
                    }
                },
            ) {
                Icon(
                    IconAssets.moreVert(),
                    contentDescription = stringResource("more_options"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (menuActions.isNotEmpty()) {
                ItemContextMenu(
                    expanded = menuExpanded.value,
                    onDismiss = { menuExpanded.value = false },
                    item = libraryItem,
                    actions = menuActions,
                    headerContent = menuHeader?.let { header ->
                        { header { menuExpanded.value = false } }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistShimmerPlaceholder() {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)))

            Spacer(Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.Center) {
                ShimmerBox(modifier = Modifier.width(200.dp).height(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(150.dp).height(20.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(100.dp).height(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp))
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        repeat(10) {
            ShimmerListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private suspend fun fetchInitialPlaylistPage(
    playlistId: String,
    playlistName: String,
    strings: StringResolver,
): Pair<PlaylistPage?, String?> {
    val normalizedId = playlistId.removePrefix("VL")
    return when (normalizedId) {
        "RDPN", "FEmusic_new_episodes" -> {
            val info = YouTube.newEpisodesPlaylistInfo().getOrNull() ?: PlaylistItem(
                id = "RDPN",
                title = playlistName.ifBlank { strings.get("new_episodes") },
                author = null,
                songCountText = null,
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            val episodesResult = YouTube.newEpisodes()
            var fetchError: String? = null
            episodesResult.onFailure { throwable ->
                println("[PODCAST_UI] RDPN newEpisodes failed: ${throwable.message}")
                fetchError = throwable.message ?: strings.get("error_loading_playlist")
            }
            val songs = episodesResult.getOrNull().orEmpty().distinctBy { it.id }
            val enrichedInfo = if (info.thumbnail == null && songs.isNotEmpty()) {
                info.copy(thumbnail = songs.first().thumbnail)
            } else {
                info
            }
            PlaylistPage(
                playlist = enrichedInfo,
                songs = songs,
                songsContinuation = null,
                continuation = null,
            ) to fetchError
        }

        "SE" -> {
            val episodesResult = YouTube.episodesForLater()
            var fetchError: String? = null
            episodesResult.onFailure { throwable ->
                println("[PODCAST_UI] SE episodesForLater failed: ${throwable.message}")
                fetchError = throwable.message ?: strings.get("error_loading_playlist")
            }
            val episodes = episodesResult.getOrNull().orEmpty().distinctBy { it.id }
            PlaylistPage(
                playlist = PlaylistItem(
                    id = "SE",
                    title = playlistName.ifBlank { strings.get("episodes_for_later") },
                    author = null,
                    songCountText = if (episodes.isNotEmpty()) {
                        strings.plural("n_episode", episodes.size, episodes.size)
                    } else {
                        null
                    },
                    thumbnail = episodes.firstOrNull()?.thumbnail,
                    playEndpoint = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                ),
                songs = episodes,
                songsContinuation = null,
                continuation = null,
            ) to fetchError
        }

        else -> {
            val result = YouTube.playlist(playlistId)
            val page = result.getOrNull()
            if (page != null) {
                page to null
            } else {
                val podcastResult = if (normalizedId.startsWith("MPSP")) {
                    YouTube.podcast(normalizedId).getOrNull()
                } else {
                    null
                }
                if (podcastResult != null) {
                    val mappedSongs = podcastResult.episodes.map { it.asSongItem() }
                    PlaylistPage(
                        playlist = PlaylistItem(
                            id = podcastResult.podcast.id,
                            title = podcastResult.podcast.title,
                            author = podcastResult.podcast.author,
                            songCountText = podcastResult.podcast.episodeCountText,
                            thumbnail = podcastResult.podcast.thumbnail,
                            playEndpoint = podcastResult.podcast.playEndpoint,
                            shuffleEndpoint = podcastResult.podcast.shuffleEndpoint,
                            radioEndpoint = null,
                        ),
                        songs = mappedSongs,
                        songsContinuation = null,
                        continuation = null,
                    ) to null
                } else {
                    null to (result.exceptionOrNull()?.message ?: strings.get("error_loading_playlist"))
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
