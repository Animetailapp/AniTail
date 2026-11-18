package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import androidx.compose.ui.unit.sp
import com.anitail.desktop.YouTube
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.mapper.toAlbumEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.indexPrimaryArtistIdBySongId
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.i18n.LocalStrings
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
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.AlbumPage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.launch

/**
 * Pantalla de detalle de álbum para Desktop - Idéntica a Android.
 * Basada en AlbumScreen.kt de la app Android.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    database: DesktopDatabase = DesktopDatabase.getInstance(),
    downloadService: DesktopDownloadService = DesktopDownloadService(),
) {
    var albumPage by remember { mutableStateOf<AlbumPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()

    val albumEntity by database.album(albumId).collectAsState(initial = null)
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

    LaunchedEffect(albumId) {
        isLoading = true
        error = null
        YouTube.album(albumId).onSuccess { page ->
            albumPage = page
        }.onFailure { e ->
            error = e.message ?: strings.get("error_loading_album")
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
        // TopAppBar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(IconAssets.arrowBack(), contentDescription = stringResource("back"))
                }
                Text(
                    text = albumPage?.album?.title ?: albumName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        albumPage?.let { page ->
            // Header con portada e info - como Android AlbumScreen
            item {
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Portada del álbum (AlbumThumbnailSize = 160.dp en Android)
                        RemoteImage(
                            url = page.album.thumbnail,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // Título del álbum con AutoResizeText
                            Text(
                                text = page.album.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 22.sp,
                            )

                            // Artistas clickeables
                            page.album.artists?.let { artists ->
                                Row {
                                    artists.forEachIndexed { index, artist ->
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                artist.id?.let { onArtistClick(it, artist.name) }
                                            },
                                        )
                                        if (index != artists.lastIndex) {
                                            Text(
                                                text = stringResource("list_separator"),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                }
                            }

                            // Año
                            page.album.year?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }

                            // Iconos de acción: Favorito, Descargar, Más
                            val isAlbumLiked = albumEntity?.bookmarkedAt != null
                            val toggleAlbumLike: () -> Unit = {
                                coroutineScope.launch {
                                    if (albumEntity == null) {
                                        database.insertAlbum(page.album.toAlbumEntity())
                                    }
                                    database.toggleAlbumBookmark(albumId)
                                }
                            }
                            val collectionTargets = remember(page.songs) {
                                page.songs.map { BrowseSongTarget(songItemToLibraryItem(it), it) }
                            }
                            val downloadMenu = resolveCollectionDownloadMenuState(
                                strings = strings,
                                songIds = page.songs.map { it.id },
                                downloadStates = downloadStates,
                                downloadedSongIds = downloadedSongIds,
                                showWhenEmpty = true,
                            ) ?: CollectionDownloadMenuState(
                                label = strings.get("download"),
                                action = CollectionDownloadAction.DOWNLOAD,
                            )
                            val artistCandidates = page.album.artists.orEmpty()

                            Row {
                                IconButton(onClick = toggleAlbumLike) {
                                    Icon(
                                        imageVector = if (isAlbumLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                                        contentDescription = null,
                                        tint = if (isAlbumLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        performCollectionDownloadAction(
                                            action = downloadMenu.action,
                                            targets = collectionTargets,
                                            songsById = songsById,
                                            fallbackAlbumName = page.album.title,
                                            downloadService = downloadService,
                                            coroutineScope = coroutineScope,
                                        )
                                    },
                                ) {
                                    when (downloadMenu.action) {
                                        CollectionDownloadAction.REMOVE -> {
                                            Icon(IconAssets.offline(), contentDescription = downloadMenu.label)
                                        }
                                        CollectionDownloadAction.CANCEL -> {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        CollectionDownloadAction.DOWNLOAD -> {
                                            Icon(IconAssets.download(), contentDescription = downloadMenu.label)
                                        }
                                    }
                                }

                                Box {
                                    IconButton(onClick = { headerMenuExpanded = true }) {
                                        Icon(
                                            IconAssets.moreVert(),
                                            contentDescription = stringResource("more_options"),
                                        )
                                    }
                                    ItemContextMenu(
                                        expanded = headerMenuExpanded,
                                        onDismiss = { headerMenuExpanded = false },
                                        item = LibraryItem(
                                            id = page.album.browseId,
                                            title = page.album.title,
                                            artist = page.album.artists?.joinToString { it.name }.orEmpty(),
                                            artworkUrl = page.album.thumbnail,
                                            playbackUrl = page.album.shareLink,
                                        ),
                                        actions = buildBrowseAlbumMenuActions(
                                            strings = strings,
                                            hasArtists = artistCandidates.isNotEmpty(),
                                            downloadLabel = downloadMenu.label,
                                            downloadEnabled = page.songs.isNotEmpty(),
                                            onStartRadio = {
                                                val firstSong = page.songs.firstOrNull() ?: return@buildBrowseAlbumMenuActions
                                                val librarySeed = songItemToLibraryItem(firstSong)
                                                coroutineScope.launch {
                                                    val result = YouTube.next(WatchEndpoint(videoId = firstSong.id))
                                                        .getOrNull() ?: return@launch
                                                    val plan = buildRadioQueuePlan(librarySeed, result)
                                                    if (plan.items.isNotEmpty()) {
                                                        playerState.playQueue(plan.items, plan.startIndex)
                                                    }
                                                }
                                            },
                                            onPlayNext = {
                                                page.songs
                                                    .asReversed()
                                                    .map { songItemToLibraryItem(it) }
                                                    .forEach { playerState.addToQueue(it, playNext = true) }
                                            },
                                            onAddToQueue = {
                                                page.songs
                                                    .map { songItemToLibraryItem(it) }
                                                    .forEach { playerState.addToQueue(it) }
                                            },
                                            onAddToPlaylist = { pendingCollectionTargets = collectionTargets },
                                            onDownload = {
                                                performCollectionDownloadAction(
                                                    action = downloadMenu.action,
                                                    targets = collectionTargets,
                                                    songsById = songsById,
                                                    fallbackAlbumName = page.album.title,
                                                    downloadService = downloadService,
                                                    coroutineScope = coroutineScope,
                                                )
                                            },
                                            onOpenArtist = openArtist@{
                                                when {
                                                    artistCandidates.size == 1 -> {
                                                        val artist = artistCandidates.first()
                                                        val artistId = artist.id ?: return@openArtist
                                                        onArtistClick(artistId, artist.name)
                                                    }
                                                    artistCandidates.size > 1 -> pendingArtists = artistCandidates
                                                }
                                            },
                                            onShare = { copyTextToClipboard(page.album.shareLink) },
                                        ),
                                        headerContent = {
                                            CollectionMenuHeader(
                                                title = page.album.title,
                                                subtitle = page.album.artists?.joinToString { it.name }.orEmpty(),
                                                thumbnailUrl = page.album.thumbnail,
                                                showLike = true,
                                                isLiked = isAlbumLiked,
                                                onToggleLike = toggleAlbumLike,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Botones Play y Shuffle
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val songs = page.songs.map { songItemToLibraryItem(it) }
                                if (songs.isNotEmpty()) {
                                    playerState.playQueue(songs, 0)
                                }
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                IconAssets.play(),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource("play"))
                        }

                        OutlinedButton(
                            onClick = {
                                val songs = page.songs.shuffled().map { songItemToLibraryItem(it) }
                                if (songs.isNotEmpty()) {
                                    playerState.shuffleEnabled = true
                                    playerState.playQueue(songs, 0)
                                }
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                IconAssets.shuffle(),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource("shuffle"))
                        }
                    }
                }
            }

            // Lista de canciones con número de track
            itemsIndexed(
                items = page.songs,
                key = { _, song -> song.id }
            ) { index, song ->
                val libraryItem = remember(song) { songItemToLibraryItem(song) }
                val songEntity = songsById[song.id]
                val songDownloadState = downloadStates[song.id]
                val isSongDownloaded = song.id in downloadedSongIds
                val menuActions = remember(
                    song.id,
                    songEntity,
                    primaryArtistBySongId[song.id],
                    songDownloadState,
                    isSongDownloaded,
                    strings,
                ) {
                    buildBrowseSongMenuActions(
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
                        onOpenAlbum = { _, _ -> },
                        onRequestPlaylist = { pendingPlaylistItem = it },
                        onRequestArtists = { pendingArtists = it },
                        onShowDetails = { detailsItem = it },
                        copyToClipboard = ::copyTextToClipboard,
                    )
                }

                SongListItem(
                    song = song,
                    libraryItem = libraryItem,
                    albumIndex = index + 1,
                    isActive = song.id == playerState.currentItem?.id,
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
                        if (song.id == playerState.currentItem?.id) {
                            playerState.togglePlayPause()
                        } else {
                            val songs = page.songs.map { songItemToLibraryItem(it) }
                            playerState.playQueue(songs, index)
                        }
                    },
                )
            }

            // Otras versiones (si existen)
            if (page.otherVersions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource("other_versions"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        items(
                            items = page.otherVersions,
                            key = { it.id }
                        ) { album ->
                            AlbumGridItem(
                                thumbnail = album.thumbnail,
                                title = album.title,
                                year = album.year,
                                onClick = { /* Navigate to album */ }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (isLoading) {
            item { AlbumShimmerPlaceholder() }
        }

        error?.let { errorMsg ->
            item {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItem(
    song: SongItem,
    libraryItem: LibraryItem,
    albumIndex: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    menuActions: List<ContextMenuAction>,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
) {
    val menuExpanded = remember(song.id) { mutableStateOf(false) }
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
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Número de track
        Text(
            text = albumIndex.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )

        // Thumbnail pequeño
        Box(modifier = Modifier.size(48.dp)) {
            RemoteImage(
                url = song.thumbnail,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
            )
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

        // Título y artista
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DownloadStatusTrailingIcon(downloadState = downloadState, isDownloaded = isDownloaded)

        // Duración
        song.duration?.let { duration ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Botón más opciones
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
private fun AlbumGridItem(
    thumbnail: String,
    title: String,
    year: Int?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        RemoteImage(
            url = thumbnail,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        year?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumShimmerPlaceholder() {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                ShimmerBox(modifier = Modifier.width(200.dp).height(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(150.dp).height(20.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.width(80.dp).height(16.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp)
            )
            ShimmerBox(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        repeat(8) {
            ShimmerListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
