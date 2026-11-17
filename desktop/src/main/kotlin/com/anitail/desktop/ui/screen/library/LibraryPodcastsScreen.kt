package com.anitail.desktop.ui.screen.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.SortHeader
import com.anitail.desktop.ui.component.rememberExpressiveAvatarShape
import com.anitail.desktop.ui.screen.BrowseSongTarget
import com.anitail.desktop.ui.screen.SongMenuHeader
import com.anitail.desktop.ui.screen.buildBrowseSongMenuActions
import com.anitail.desktop.ui.screen.formatTime
import com.anitail.innertube.YouTube as InnertubeYouTube
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.PodcastItem
import com.anitail.innertube.models.SongItem
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPodcastsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    onDeselect: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    var podcastFilter by remember { mutableStateOf(PodcastFilter.EPISODES) }
    var activePodcastId by remember { mutableStateOf<String?>(null) }
    var sePlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var rdpnPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var savedShows by remember { mutableStateOf<List<PodcastItem>>(emptyList()) }
    var savedEpisodes by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var podcastChannels by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val sortType by preferences.songSortType.collectAsState()
    val sortDescending by preferences.songSortDescending.collectAsState()
    val showCached by preferences.showCachedPlaylist.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val visiblePlaylists = remember(playlists, showCached) {
        filterLibraryPlaylists(playlists, showCached)
    }

    var pendingPlaylistTarget by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching {
            val uri = if (url.startsWith("http", ignoreCase = true)) URI(url) else File(url).toURI()
            Desktop.getDesktop().browse(uri)
        }
    }

    suspend fun ensureSongInDatabase(target: BrowseSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }

    suspend fun loadPodcastData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            InnertubeYouTube.newEpisodesPlaylistInfo().onSuccess { rdpnPlaylist = it }
            InnertubeYouTube.library("FEmusic_liked_playlists").onSuccess { page ->
                sePlaylist = page.items
                    .filterIsInstance<PlaylistItem>()
                    .find { it.id == "SE" }
            }
            InnertubeYouTube.savedPodcastShows().onSuccess { shows ->
                savedShows = shows
            }
            InnertubeYouTube.episodesForLater().onSuccess { episodes ->
                savedEpisodes = episodes
            }
            val apiChannels = InnertubeYouTube.libraryPodcastChannels()
                .getOrNull()
                ?.items
                ?.filterIsInstance<ArtistItem>()
                .orEmpty()
            val showChannels = savedShows.mapNotNull { show ->
                val chId = show.channelId ?: show.author?.id ?: return@mapNotNull null
                val chName = show.author?.name?.takeIf { it.isNotBlank() } ?: show.title
                ArtistItem(
                    id = chId,
                    title = chName,
                    thumbnail = show.thumbnail,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                )
            }
            val apiIds = apiChannels.map { it.id }.toSet()
            podcastChannels = apiChannels + showChannels.filter { it.id !in apiIds }.distinctBy { it.id }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadPodcastData()
    }

    pendingPlaylistTarget?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = visiblePlaylists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name, createdAt = LocalDateTime.now())
                scope.launch {
                    database.insertPlaylist(playlist)
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistTarget = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistTarget = null
            },
            onDismiss = { pendingPlaylistTarget = null },
        )
    }

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
    }

    val downloadedEntries = remember(downloadedSongs, songsById, podcastChannels, sortType, sortDescending) {
        val channelNames = podcastChannels.map { it.title.lowercase() }.toSet()
        val matchingSongs = downloadedSongs.mapNotNull { downloaded ->
            val songEntity = songsById[downloaded.songId]
            val matchesChannel = channelNames.isNotEmpty() && channelNames.any {
                downloaded.artist.lowercase().contains(it)
            }
            if (!matchesChannel) return@mapNotNull null
            songEntity ?: SongEntity(
                id = downloaded.songId,
                title = downloaded.title,
                artistName = downloaded.artist,
                duration = downloaded.duration,
                thumbnailUrl = downloaded.thumbnailUrl,
            )
        }
        val sorted = sortSongs(matchingSongs, sortType, sortDescending)
        val byId = downloadedSongs.associateBy { it.songId }
        sorted.mapNotNull { entity ->
            val dl = byId[entity.id] ?: return@mapNotNull null
            val item = LibraryItem(
                id = dl.songId,
                title = dl.title,
                artist = dl.artist,
                artworkUrl = dl.thumbnailUrl,
                playbackUrl = dl.filePath,
                durationMs = dl.duration.toLong() * 1000L,
            )
            Triple(dl, entity, item)
        }
    }

    AnimatedContent(
        targetState = activePodcastId,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.97f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            ) + fadeIn(animationSpec = tween(220)))
                .togetherWith(
                    scaleOut(
                        targetScale = 0.97f,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    ) + fadeOut(animationSpec = tween(160)),
                )
        },
        label = "PodcastDetailTransition",
        modifier = Modifier.fillMaxSize(),
    ) { podcastId ->
        if (podcastId != null) {
            OnlinePodcastDetailView(
                podcastId = podcastId,
                isSubscribedInitial = savedShows.any { it.id == podcastId },
                database = database,
                downloadService = downloadService,
                playerState = playerState,
                songsById = songsById,
                songArtistMaps = songArtistMaps,
                downloadStates = downloadStates,
                downloadedSongs = downloadedSongs,
                onBack = { activePodcastId = null },
                onToggleSaved = { show, shouldSave ->
                    scope.launch(Dispatchers.IO) {
                        InnertubeYouTube.savePodcast(show.id, shouldSave).onSuccess {
                            savedShows = if (shouldSave) {
                                (listOf(show) + savedShows).distinctBy { it.id }
                            } else {
                                savedShows.filterNot { it.id == show.id }
                            }
                        }
                    }
                },
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onRequestPlaylist = { pendingPlaylistTarget = it },
                onShowDetails = { detailsItem = it },
                copyToClipboard = ::copyToClipboard,
            )
            return@AnimatedContent
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "podcast-filter-row") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Spacer(Modifier.width(12.dp))
                        FilterChip(
                            label = { Text(stringResource("filter_podcasts")) },
                            selected = true,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            onClick = onDeselect,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(imageVector = IconAssets.close(), contentDescription = null)
                            },
                        )
                        ChipsRow(
                            chips = listOf(
                                PodcastFilter.EPISODES to stringResource("filter_episodes"),
                                PodcastFilter.CHANNELS to stringResource("filter_channels"),
                                PodcastFilter.DOWNLOADED to stringResource("filter_downloaded"),
                            ),
                            currentValue = podcastFilter,
                            onValueUpdate = { podcastFilter = it },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { scope.launch { loadPodcastData() } },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = IconAssets.refresh(),
                                contentDescription = null,
                            )
                        }
                    }
                }

                when (podcastFilter) {
                    PodcastFilter.EPISODES -> {
                        item(key = "rdpn_playlist") {
                            PodcastAutoPlaylistCard(
                                title = stringResource("new_episodes"),
                                thumbnailUrl = rdpnPlaylist?.thumbnail,
                                episodeCount = rdpnPlaylist?.songCountText,
                                onClick = { onOpenPlaylist("RDPN", strings.get("new_episodes")) },
                            )
                        }

                        item(key = "episodes_for_later") {
                            val fallbackThumb = sePlaylist?.thumbnail ?: savedEpisodes.firstOrNull()?.thumbnail
                            val fallbackCount = sePlaylist?.songCountText ?: if (savedEpisodes.isNotEmpty()) {
                                pluralStringResource("n_episode", savedEpisodes.size, savedEpisodes.size)
                            } else {
                                null
                            }
                            PodcastAutoPlaylistCard(
                                title = stringResource("episodes_for_later"),
                                thumbnailUrl = fallbackThumb,
                                episodeCount = fallbackCount,
                                onClick = { onOpenPlaylist("SE", strings.get("episodes_for_later")) },
                            )
                        }

                        if (isLoading && savedShows.isEmpty()) {
                            item(key = "shows-loading") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else {
                            items(savedShows, key = { it.id }) { podcast ->
                                SavedPodcastShowRow(
                                    podcast = podcast,
                                    onClick = { activePodcastId = podcast.id },
                                    onRemoveFromLibrary = {
                                        scope.launch(Dispatchers.IO) {
                                            InnertubeYouTube.savePodcast(podcast.id, false).onSuccess {
                                                savedShows = savedShows.filterNot { it.id == podcast.id }
                                            }
                                        }
                                    },
                                    onShare = { copyToClipboard(podcast.shareLink) },
                                )
                            }
                        }
                    }

                    PodcastFilter.CHANNELS -> {
                        item(key = "channels_count") {
                            Text(
                                text = pluralStringResource("n_channel", podcastChannels.size, podcastChannels.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        if (isLoading && podcastChannels.isEmpty()) {
                            item(key = "channels-loading") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (podcastChannels.isEmpty()) {
                            item(key = "podcasts-channels-empty") {
                                EmptyPodcastPlaceholder(
                                    title = stringResource("no_subscribed_channels"),
                                    subtitle = stringResource("filter_channels"),
                                )
                            }
                        } else {
                            items(podcastChannels, key = { it.id }) { channel ->
                                val shape = rememberExpressiveAvatarShape(channel.id)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onOpenArtist(channel.id, channel.title) },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        RemoteImage(
                                            url = channel.thumbnail,
                                            shape = shape,
                                            modifier = Modifier.size(52.dp).clip(shape),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = stringResource("filter_channels"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            imageVector = IconAssets.navigateNext(),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PodcastFilter.DOWNLOADED -> {
                        item(key = "downloaded-sort-header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = { preferences.setSongSortType(it) },
                                    onSortDescendingChange = { preferences.setSongSortDescending(it) },
                                    sortTypeText = { type ->
                                        when (type) {
                                            SongSortType.CREATE_DATE -> strings.get("sort_by_create_date")
                                            SongSortType.NAME -> strings.get("sort_by_name")
                                            SongSortType.ARTIST -> strings.get("sort_by_artist")
                                            SongSortType.PLAY_TIME -> strings.get("sort_by_play_time")
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = pluralStringResource("n_episode", downloadedEntries.size, downloadedEntries.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

                        if (downloadedEntries.isEmpty()) {
                            item(key = "podcasts-downloaded-empty") {
                                EmptyPodcastPlaceholder(
                                    title = stringResource("filter_podcasts"),
                                    subtitle = stringResource("filter_downloaded"),
                                )
                            }
                        } else {
                            itemsIndexed(downloadedEntries, key = { _, entry -> entry.first.songId }) { index, (downloaded, songEntity, libraryItem) ->
                                val menuExpanded = remember(downloaded.songId) { mutableStateOf(false) }
                                val menuActions = remember(downloaded.songId, downloadStates, downloadedSongs) {
                                    buildBrowseSongMenuActions(
                                        strings = strings,
                                        libraryItem = libraryItem,
                                        songItem = null,
                                        songsById = songsById,
                                        songArtistMaps = songArtistMaps,
                                        downloadStates = downloadStates,
                                        downloadedSongs = downloadedSongs,
                                        database = database,
                                        downloadService = downloadService,
                                        playerState = playerState,
                                        coroutineScope = scope,
                                        onOpenArtist = onOpenArtist,
                                        onOpenAlbum = onOpenAlbum,
                                        onRequestPlaylist = { pendingPlaylistTarget = it },
                                        onRequestArtists = {},
                                        onShowDetails = { detailsItem = it },
                                        copyToClipboard = ::copyToClipboard,
                                    )
                                }
                                Box {
                                    LibrarySongListItem(
                                        song = songEntity,
                                        showInLibraryIcon = false,
                                        downloaded = true,
                                        isActive = downloaded.songId == playerState.currentItem?.id,
                                        isPlaying = playerState.isPlaying,
                                        isSelected = false,
                                        onClick = {
                                            if (downloaded.songId == playerState.currentItem?.id) {
                                                playerState.togglePlayPause()
                                            } else {
                                                playerState.playQueue(downloadedEntries.map { it.third }, startIndex = index)
                                            }
                                        },
                                        onMenuClick = { menuExpanded.value = true },
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = {
                                                if (downloaded.songId == playerState.currentItem?.id) {
                                                    playerState.togglePlayPause()
                                                } else {
                                                    playerState.playQueue(downloadedEntries.map { it.third }, startIndex = index)
                                                }
                                            },
                                            onLongClick = { menuExpanded.value = true },
                                        ),
                                    )
                                    ItemContextMenu(
                                        expanded = menuExpanded.value,
                                        onDismiss = { menuExpanded.value = false },
                                        item = libraryItem,
                                        actions = menuActions,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (podcastFilter == PodcastFilter.DOWNLOADED && downloadedEntries.isNotEmpty()) {
                HideOnScrollFAB(
                    visible = true,
                    lazyListState = listState,
                    onClick = {
                        playerState.shuffleEnabled = true
                        playerState.playQueue(downloadedEntries.map { it.third }.shuffled(), startIndex = 0)
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun OnlinePodcastDetailView(
    podcastId: String,
    isSubscribedInitial: Boolean,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<com.anitail.desktop.db.entities.SongArtistMap>,
    downloadStates: Map<String, com.anitail.desktop.download.DownloadState>,
    downloadedSongs: List<com.anitail.desktop.download.DownloadedSong>,
    onBack: () -> Unit,
    onToggleSaved: (PodcastItem, Boolean) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onRequestPlaylist: (BrowseSongTarget) -> Unit,
    onShowDetails: (LibraryItem) -> Unit,
    copyToClipboard: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var podcast by remember(podcastId) { mutableStateOf<PodcastItem?>(null) }
    var episodes by remember(podcastId) { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var isLoading by remember(podcastId) { mutableStateOf(true) }
    var error by remember(podcastId) { mutableStateOf<String?>(null) }
    var isSubscribed by remember(podcastId) { mutableStateOf(isSubscribedInitial) }
    var searchQuery by remember(podcastId) { mutableStateOf("") }
    var showSearch by remember(podcastId) { mutableStateOf(false) }

    LaunchedEffect(podcastId) {
        isLoading = true
        error = null
        withContext(Dispatchers.IO) {
            InnertubeYouTube.podcast(podcastId)
                .onSuccess { page ->
                    podcast = page.podcast
                    episodes = page.episodes
                }
                .onFailure { throwable ->
                    error = throwable.message ?: "Failed to load podcast"
                }
        }
        isLoading = false
    }

    val filteredEpisodes = remember(episodes, searchQuery) {
        if (searchQuery.isBlank()) episodes
        else episodes.filter { ep ->
            ep.title.contains(searchQuery, ignoreCase = true) ||
                ep.author?.name?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    val episodeLibraryItems = remember(filteredEpisodes) {
        filteredEpisodes.map { ep ->
            LibraryItem(
                id = ep.id,
                title = ep.title,
                artist = ep.author?.name ?: podcast?.author?.name.orEmpty(),
                artworkUrl = ep.thumbnail,
                playbackUrl = ep.shareLink,
                durationMs = (ep.duration ?: 0) * 1000L,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "podcast-detail-topbar") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = IconAssets.arrowBack(), contentDescription = stringResource("back"))
                }
                Text(
                    text = podcast?.title ?: stringResource("filter_podcasts"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(imageVector = IconAssets.search(), contentDescription = stringResource("search"))
                }
            }
        }

        if (showSearch) {
            item(key = "podcast-detail-search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource("search")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }

        if (isLoading && podcast == null) {
            item(key = "podcast-detail-loading") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(64.dp),
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (error != null && podcast == null) {
            item(key = "podcast-detail-error") {
                EmptyPodcastPlaceholder(
                    title = error.orEmpty(),
                    subtitle = stringResource("filter_podcasts"),
                )
            }
        } else if (podcast != null) {
            val loaded = podcast!!
            item(key = "podcast-detail-header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    RemoteImage(
                        url = loaded.thumbnail,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = loaded.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        loaded.author?.let { author ->
                            Text(
                                text = author.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    author.id?.let { id -> onOpenArtist(id, author.name) }
                                },
                            )
                        }
                        Text(
                            text = loaded.episodeCountText
                                ?: pluralStringResource("n_episode", episodes.size, episodes.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (episodeLibraryItems.isNotEmpty()) {
                                        playerState.playQueue(episodeLibraryItems, startIndex = 0)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = IconAssets.play(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource("play"))
                            }
                            OutlinedButton(
                                onClick = {
                                    if (episodeLibraryItems.isNotEmpty()) {
                                        playerState.shuffleEnabled = true
                                        playerState.playQueue(episodeLibraryItems.shuffled(), startIndex = 0)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = IconAssets.shuffle(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource("shuffle"))
                            }
                            OutlinedButton(
                                onClick = {
                                    val next = !isSubscribed
                                    isSubscribed = next
                                    onToggleSaved(loaded, next)
                                },
                            ) {
                                Text(
                                    text = if (isSubscribed) {
                                        stringResource("subscribed")
                                    } else {
                                        stringResource("subscribe")
                                    },
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(filteredEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                val libraryItem = episodeLibraryItems[index]
                val isCurrent = ep.id == playerState.currentItem?.id
                val menuExpanded = remember(ep.id) { mutableStateOf(false) }
                val menuActions = remember(ep.id, downloadStates, downloadedSongs) {
                    buildBrowseSongMenuActions(
                        strings = strings,
                        libraryItem = libraryItem,
                        songItem = null,
                        songsById = songsById,
                        songArtistMaps = songArtistMaps,
                        downloadStates = downloadStates,
                        downloadedSongs = downloadedSongs,
                        database = database,
                        downloadService = downloadService,
                        playerState = playerState,
                        coroutineScope = scope,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onRequestPlaylist = onRequestPlaylist,
                        onRequestArtists = {},
                        onShowDetails = onShowDetails,
                        copyToClipboard = copyToClipboard,
                    )
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isCurrent) {
                                        playerState.togglePlayPause()
                                    } else {
                                        playerState.playQueue(episodeLibraryItems, startIndex = index)
                                    }
                                },
                                onLongClick = { menuExpanded.value = true },
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                            ) {
                                RemoteImage(
                                    url = ep.thumbnail,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxSize(),
                                )
                                PlayingIndicatorBox(
                                    isActive = isCurrent,
                                    isPlaying = playerState.isPlaying,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = listOfNotNull(
                                    ep.publishDateText?.takeIf { it.isNotBlank() },
                                    ep.duration?.takeIf { it > 0 }?.let { formatTime(it * 1000L) },
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { menuExpanded.value = true }) {
                                Icon(imageVector = IconAssets.moreVert(), contentDescription = null)
                            }
                        }
                    }

                    ItemContextMenu(
                        expanded = menuExpanded.value,
                        onDismiss = { menuExpanded.value = false },
                        item = libraryItem,
                        actions = menuActions,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedPodcastShowRow(
    podcast: PodcastItem,
    onClick: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onShare: () -> Unit,
) {
    val menuExpanded = remember(podcast.id) { mutableStateOf(false) }
    val item = LibraryItem(
        id = podcast.id,
        title = podcast.title,
        artist = podcast.author?.name.orEmpty(),
        artworkUrl = podcast.thumbnail,
        playbackUrl = podcast.shareLink,
    )
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded.value = true },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                RemoteImage(
                    url = podcast.thumbnail,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        podcast.author?.name?.takeIf { it.isNotBlank() },
                        podcast.episodeCountText?.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { menuExpanded.value = true }) {
                    Icon(imageVector = IconAssets.moreVert(), contentDescription = null)
                }
            }
        }
        ItemContextMenu(
            expanded = menuExpanded.value,
            onDismiss = { menuExpanded.value = false },
            item = item,
            actions = listOf(
                ContextMenuAction(
                    label = stringResource("remove_from_library"),
                    icon = IconAssets.close(),
                    onClick = onRemoveFromLibrary,
                ),
                ContextMenuAction(
                    label = stringResource("share"),
                    icon = IconAssets.share(),
                    onClick = onShare,
                ),
            ),
        )
    }
}

@Composable
private fun PodcastAutoPlaylistCard(
    title: String,
    thumbnailUrl: String?,
    episodeCount: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            RemoteImage(
                url = thumbnailUrl,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!episodeCount.isNullOrBlank()) {
                    Text(
                        text = episodeCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = IconAssets.navigateNext(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPodcastPlaceholder(
    title: String,
    subtitle: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = IconAssets.libraryOutlined(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
