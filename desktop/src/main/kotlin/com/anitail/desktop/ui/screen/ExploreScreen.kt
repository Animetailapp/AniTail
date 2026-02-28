
package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.extractVideoId
import com.anitail.desktop.db.mapper.toAlbumEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.shimmer.GridItemPlaceholder
import com.anitail.desktop.ui.component.shimmer.ShimmerHost
import com.anitail.desktop.ui.component.shimmer.TextPlaceholder
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.ui.utils.SnapLayoutInfoProvider
import com.anitail.desktop.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.ExplorePage
import com.anitail.shared.model.LibraryItem
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val GridThumbnailSize = 160.dp
private val ThumbnailCornerRadius = 6.dp
private val MoodAndGenresButtonHeight = 48.dp
private const val ActiveBoxAlpha = 0.6f
private val OverlayButtonSize = 36.dp
private val OverlayIconSize = 20.dp
private const val TopMusicVideosTitle = "Top music videos"
private const val QuickPicksTitle = "Quick picks"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    explorePage: ExplorePage?,
    chartsPage: ChartsPage?,
    isLoading: Boolean,
    playerState: PlayerState,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playlists: List<PlaylistEntity>,
    songsById: Map<String, SongEntity>,
    onPlay: (LibraryItem) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onBrowse: (String, String?) -> Unit = { _, _ -> },
    onMoodGreClick: () -> Unit = {},
    onNewReleaseClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadStates by downloadService.downloadStates.collectAsState()
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    var pendingPlaylistItem by remember { mutableStateOf<ExploreSongTarget?>(null) }
    var pendingAlbumPlaylist by remember { mutableStateOf<AlbumItem?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    val strings = LocalStrings.current
    val albumMenuStates = remember { mutableStateMapOf<String, AlbumMenuState>() }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    suspend fun ensureSongInDatabase(target: ExploreSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }

    fun playAlbum(album: AlbumItem) {
        coroutineScope.launch {
            val page = withContext(Dispatchers.IO) {
                YouTube.album(album.browseId).getOrNull()
            } ?: return@launch
            val queue = page.songs.map { song ->
                song.toSongEntity().toLibraryItem()
            }
            if (queue.isNotEmpty()) {
                playerState.playQueue(queue, startIndex = 0)
            }
        }
    }

    suspend fun loadAlbumSongs(album: AlbumItem): List<SongItem> {
        val albumPage = withContext(Dispatchers.IO) {
            YouTube.album(album.browseId).getOrNull()
        }
        if (albumPage != null && albumPage.songs.isNotEmpty()) {
            return albumPage.songs
        }
        return withContext(Dispatchers.IO) {
            YouTube.albumSongs(album.playlistId, album).getOrNull().orEmpty()
        }
    }

    fun ensureAlbumMenuState(album: AlbumItem) {
        if (albumMenuStates.containsKey(album.browseId)) return
        coroutineScope.launch {
            val songs = loadAlbumSongs(album)
            albumMenuStates[album.browseId] = AlbumMenuState(songs)
        }
    }

    fun resolveAlbumDownloadMenuState(albumSongs: List<SongItem>): AlbumDownloadMenuState {
        if (albumSongs.isEmpty()) {
            return AlbumDownloadMenuState(
                label = strings.get("download"),
                action = AlbumDownloadAction.DOWNLOAD,
            )
        }
        val downloadedIds = downloadedSongs.map { it.songId }.toSet()
        val statuses = albumSongs.mapNotNull { song -> downloadStates[song.id]?.status }
        val allDownloaded = albumSongs.all { song -> downloadedIds.contains(song.id) }
        val anyActive = statuses.any { status ->
            status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
        }
        return when {
            allDownloaded -> AlbumDownloadMenuState(
                label = strings.get("remove_download"),
                action = AlbumDownloadAction.REMOVE,
            )
            anyActive -> AlbumDownloadMenuState(
                label = strings.get("downloading_to_device"),
                action = AlbumDownloadAction.CANCEL,
            )
            else -> AlbumDownloadMenuState(
                label = strings.get("download"),
                action = AlbumDownloadAction.DOWNLOAD,
            )
        }
    }

    fun menuActionsForAlbum(
        album: AlbumItem,
        albumSongs: List<SongItem>,
    ): List<ContextMenuAction> {
        val downloadMenu = resolveAlbumDownloadMenuState(albumSongs)
        val artistCandidates = album.artists.orEmpty()
        return buildExploreAlbumMenuActions(
            strings = strings,
            hasArtists = artistCandidates.isNotEmpty(),
            downloadLabel = downloadMenu.label,
            downloadEnabled = true,
            onStartRadio = {
                coroutineScope.launch {
                    val songs = if (albumSongs.isEmpty()) loadAlbumSongs(album) else albumSongs
                    val firstSong = songs.firstOrNull() ?: return@launch
                    val libraryItem = songItemToLibraryItem(firstSong)
                    val result = YouTube.next(WatchEndpoint(videoId = firstSong.id)).getOrNull() ?: return@launch
                    val plan = buildRadioQueuePlan(libraryItem, result)
                    playerState.playQueue(plan.items, plan.startIndex)
                }
            },
            onPlayNext = {
                coroutineScope.launch {
                    val songs = if (albumSongs.isEmpty()) loadAlbumSongs(album) else albumSongs
                    val items = songs.map { songItemToLibraryItem(it) }
                    items.asReversed().forEach { item ->
                        playerState.addToQueue(item, playNext = true)
                    }
                }
            },
            onAddToQueue = {
                coroutineScope.launch {
                    val songs = if (albumSongs.isEmpty()) loadAlbumSongs(album) else albumSongs
                    songs.map { songItemToLibraryItem(it) }.forEach { item ->
                        playerState.addToQueue(item)
                    }
                }
            },
            onAddToPlaylist = { pendingAlbumPlaylist = album },
            onDownload = {
                coroutineScope.launch {
                    val songs = if (albumSongs.isEmpty()) loadAlbumSongs(album) else albumSongs
                    when (downloadMenu.action) {
                        AlbumDownloadAction.DOWNLOAD -> {
                            songs.forEach { song ->
                                downloadService.downloadSong(
                                    songId = song.id,
                                    title = song.title,
                                    artist = song.artists.joinToString { it.name },
                                    album = album.title,
                                    thumbnailUrl = song.thumbnail,
                                    duration = song.duration ?: 0,
                                )
                            }
                        }
                        AlbumDownloadAction.CANCEL -> {
                            songs.forEach { song ->
                                downloadService.cancelDownload(song.id)
                            }
                        }
                        AlbumDownloadAction.REMOVE -> {
                            songs.forEach { song ->
                                downloadService.deleteDownload(song.id)
                            }
                        }
                    }
                }
            },
            onOpenArtist = openArtist@{
                when {
                    artistCandidates.size == 1 -> {
                        val artist = artistCandidates.first()
                        val artistId = artist.id ?: return@openArtist
                        onOpenArtist(artistId, artist.name)
                    }
                    artistCandidates.size > 1 -> {
                        pendingArtists = artistCandidates
                    }
                }
            },
            onShare = { copyToClipboard(album.shareLink) },
        )
    }

    suspend fun addAlbumSongsToPlaylist(
        album: AlbumItem,
        playlistId: String,
    ) {
        val songs = loadAlbumSongs(album)
        if (songs.isEmpty()) return
        songs.forEach { song ->
            val entity = song.toSongEntity(inLibrary = true)
            database.insertSong(entity, song.toSongArtistMaps())
            database.addSongToPlaylist(playlistId, song.id)
        }
    }
    fun menuActionsForSong(
        libraryItem: LibraryItem,
        songItem: SongItem?,
    ): List<ContextMenuAction> {
        val songEntity = songsById[libraryItem.id]
        val artistCandidates = songItem?.artists
            ?.mapNotNull { artist -> artist.id?.let { Artist(artist.name, it) } }
            .orEmpty()
        val libraryArtistId = songEntity?.let { primaryArtistIdForSong(it.id, songArtistMaps) }
        val hasArtist = if (songItem != null) {
            artistCandidates.isNotEmpty()
        } else {
            !libraryArtistId.isNullOrBlank()
        }
        val hasAlbum = if (songItem != null) {
            !songItem.album?.id.isNullOrBlank()
        } else {
            !songEntity?.albumId.isNullOrBlank()
        }
        val canStartRadio = !(songItem?.id ?: extractVideoId(libraryItem.playbackUrl)).isNullOrBlank()
        val availability = HomeSongMenuAvailability(
            canStartRadio = canStartRadio,
            canAddToPlaylist = true,
            hasArtist = hasArtist,
            hasAlbum = hasAlbum,
        )
        val order = buildHomeSongMenuOrder(availability)
        val downloadState = resolveDownloadMenuState(
            strings = strings,
            songId = libraryItem.id,
            downloadStates = downloadStates,
            downloadedSongs = downloadedSongs,
        )
        val isInLibrary = songEntity?.inLibrary != null
        val shareUrl = songItem?.shareLink ?: libraryItem.playbackUrl

        return buildHomeSongMenuActions(
            strings = strings,
            order = order,
            downloadState = downloadState,
            isInLibrary = isInLibrary,
            onStartRadio = startRadio@{
                val videoId = songItem?.id ?: extractVideoId(libraryItem.playbackUrl) ?: return@startRadio
                coroutineScope.launch {
                    val result = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return@launch
                    val plan = buildRadioQueuePlan(libraryItem, result)
                    playerState.playQueue(plan.items, plan.startIndex)
                }
            },
            onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
            onAddToQueue = { playerState.addToQueue(libraryItem) },
            onAddToPlaylist = { pendingPlaylistItem = ExploreSongTarget(libraryItem, songItem) },
            onDownload = {
                val durationSec = ((libraryItem.durationMs ?: 0L) / 1000L).toInt()
                val albumName = songItem?.album?.name ?: songEntity?.albumName
                downloadService.downloadSong(
                    songId = libraryItem.id,
                    title = libraryItem.title,
                    artist = libraryItem.artist,
                    album = albumName,
                    thumbnailUrl = libraryItem.artworkUrl,
                    duration = durationSec,
                )
            },
            onToggleLibrary = {
                coroutineScope.launch {
                    if (songEntity == null) {
                        val entity = songItem?.toSongEntity(inLibrary = true) ?: libraryItem.toSongEntity()
                        if (songItem != null) {
                            database.insertSong(entity, songItem.toSongArtistMaps())
                        } else {
                            database.insertSong(entity)
                        }
                    } else {
                        database.toggleSongInLibrary(libraryItem.id)
                    }
                }
            },
            onOpenArtist = openArtist@{
                when {
                    artistCandidates.size == 1 -> {
                        val artist = artistCandidates.first()
                        val artistId = artist.id ?: return@openArtist
                        onOpenArtist(artistId, artist.name)
                    }
                    artistCandidates.size > 1 -> {
                        pendingArtists = artistCandidates
                    }
                    !libraryArtistId.isNullOrBlank() -> {
                        onOpenArtist(libraryArtistId, songEntity.artistName)
                    }
                }
            },
            onOpenAlbum = openAlbum@{
                when {
                    !songItem?.album?.id.isNullOrBlank() -> {
                        val albumId = songItem.album?.id ?: return@openAlbum
                        onOpenAlbum(albumId, songItem.album?.name)
                    }
                    !songEntity?.albumId.isNullOrBlank() -> {
                        onOpenAlbum(songEntity.albumId, songEntity.albumName)
                    }
                }
            },
            onShare = { copyToClipboard(shareUrl) },
            onDetails = { detailsItem = libraryItem },
        )
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading || chartsPage == null || explorePage == null) {
            ExploreShimmer()
        } else {
            chartsPage.sections.filter { it.title != TopMusicVideosTitle }.forEach { section ->
                NavigationTitle(
                    title = mapExploreChartsTitle(section.title, strings),
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontalLazyGridItemWidthFactor =
                        if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
                    val lazyGridState = rememberLazyGridState()
                    val snapLayoutInfoProvider = remember(lazyGridState, horizontalLazyGridItemWidthFactor) {
                        SnapLayoutInfoProvider(
                            lazyGridState = lazyGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                            },
                        )
                    }

                    LazyHorizontalGrid(
                        state = lazyGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4),
                    ) {
                        items(
                            items = section.items.filterIsInstance<SongItem>(),
                            key = { it.id },
                        ) { song ->
                            val libraryItem = songItemToLibraryItem(song)
                            val isActive = playerState.currentItem?.id == song.id
                            val songEntity = songsById[song.id]
                            val downloadState = downloadStates[song.id]
                            val isDownloaded = downloadedSongs.any { it.songId == song.id }
                            ExploreChartListItem(
                                song = song,
                                libraryItem = libraryItem,
                                itemWidth = horizontalLazyGridItemWidth,
                                isActive = isActive,
                                isPlaying = isActive && playerState.isPlaying,
                                songEntity = songEntity,
                                downloadState = downloadState,
                                isDownloaded = isDownloaded,
                                menuActions = menuActionsForSong(libraryItem, song),
                                menuHeader = { onDismiss ->
                                    SongMenuHeader(
                                        title = song.title,
                                        subtitle = joinByBullet(
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
                                            onDismiss()
                                        },
                                        onPlayNext = {
                                            playerState.addToQueue(libraryItem, playNext = true)
                                        },
                                        onAddToPlaylist = {
                                            pendingPlaylistItem = ExploreSongTarget(libraryItem, song)
                                        },
                                        onShare = { copyToClipboard(song.shareLink) },
                                        onDismiss = onDismiss,
                                    )
                                },
                                onClick = {
                                    if (isActive) {
                                        playerState.togglePlayPause()
                                    } else {
                                        onPlay(libraryItem)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            explorePage.newReleaseAlbums.takeIf { it.isNotEmpty() }?.let { newReleaseAlbums ->
                NavigationTitle(
                    title = stringResource("new_release_albums"),
                    onClick = onNewReleaseClick,
                )
                LazyRow(
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues(),
                ) {
                    items(
                        items = newReleaseAlbums,
                        key = { it.id },
                    ) { album ->
                        val albumLibraryItem = albumItemToLibraryItem(album)
                        val albumEntity by database.album(album.browseId).collectAsState(initial = null)
                        val albumMenuState = albumMenuStates[album.browseId]
                        val albumSongs = albumMenuState?.songs.orEmpty()
                        ExploreGridItem(
                            item = album,
                            isActive = false,
                            isPlaying = false,
                            libraryItem = albumLibraryItem,
                            menuActions = menuActionsForAlbum(album, albumSongs),
                            songEntity = null,
                            downloadState = null,
                            isDownloaded = false,
                            onClick = { onOpenAlbum(album.browseId, album.title) },
                            onMenuOpened = {
                                if (albumEntity == null) {
                                    coroutineScope.launch {
                                        database.insertAlbum(album.toAlbumEntity())
                                    }
                                }
                                ensureAlbumMenuState(album)
                            },
                            menuHeader = { onDismiss ->
                                ExploreAlbumMenuHeader(
                                    album = album,
                                    albumEntity = albumEntity,
                                    onToggleLike = {
                                        coroutineScope.launch {
                                            if (albumEntity == null) {
                                                database.insertAlbum(album.toAlbumEntity())
                                            }
                                            database.toggleAlbumBookmark(album.browseId)
                                        }
                                        onDismiss()
                                    },
                                )
                            },
                            onAlbumPlay = { playAlbum(album) },
                        )
                    }
                }
            }
            chartsPage.sections.find { it.title == TopMusicVideosTitle }?.let { topVideos ->
                NavigationTitle(title = stringResource("top_music_videos"))
                LazyRow(
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues(),
                ) {
                    items(
                        items = topVideos.items.filterIsInstance<SongItem>(),
                        key = { it.id },
                    ) { video ->
                        val libraryItem = songItemToLibraryItem(video)
                        val isActive = playerState.currentItem?.id == video.id
                        val songEntity = songsById[video.id]
                        val downloadState = downloadStates[video.id]
                        val isDownloaded = downloadedSongs.any { it.songId == video.id }
                        ExploreGridItem(
                            item = video,
                            isActive = isActive,
                            isPlaying = isActive && playerState.isPlaying,
                            libraryItem = libraryItem,
                            menuActions = menuActionsForSong(libraryItem, video),
                            songEntity = songEntity,
                            downloadState = downloadState,
                            isDownloaded = isDownloaded,
                            onClick = {
                                if (isActive) {
                                    playerState.togglePlayPause()
                                } else {
                                    onPlay(libraryItem)
                                }
                            },
                            menuHeader = { onDismiss ->
                                SongMenuHeader(
                                    title = video.title,
                                    subtitle = joinByBullet(
                                        video.artists.joinToString { it.name },
                                        video.duration?.let { formatTime(it * 1000L) },
                                    ).orEmpty(),
                                    thumbnailUrl = video.thumbnail,
                                    isLiked = songEntity?.liked == true,
                                    onToggleLike = {
                                        coroutineScope.launch {
                                            if (songEntity == null) {
                                                database.insertSong(
                                                    video.toSongEntity(inLibrary = true).toggleLike(),
                                                    video.toSongArtistMaps(),
                                                )
                                            } else {
                                                database.toggleSongLike(video.id)
                                            }
                                        }
                                        onDismiss()
                                    },
                                    onPlayNext = {
                                        playerState.addToQueue(libraryItem, playNext = true)
                                    },
                                    onAddToPlaylist = {
                                        pendingPlaylistItem = ExploreSongTarget(libraryItem, video)
                                    },
                                    onShare = { copyToClipboard(video.shareLink) },
                                    onDismiss = onDismiss,
                                )
                            },
                        )
                    }
                }
            }

            explorePage.moodAndGenres.takeIf { it.isNotEmpty() }?.let { moodAndGenres ->
                NavigationTitle(
                    title = stringResource("mood_and_genres"),
                    onClick = onMoodGreClick,
                )
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
                ) {
                    items(moodAndGenres) { item ->
                        MoodAndGenresButton(
                            title = item.title,
                            onClick = {
                                val browseId = item.endpoint.browseId
                                if (browseId.isNullOrBlank()) {
                                    onMoodGreClick()
                                } else {
                                    onBrowse(browseId, item.endpoint.params)
                                }
                            },
                            modifier = Modifier
                                .padding(6.dp)
                                .width(180.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingPlaylistItem?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                coroutineScope.launch {
                    database.insertPlaylist(playlist)
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onSelectPlaylist = { playlist ->
                coroutineScope.launch {
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onDismiss = { pendingPlaylistItem = null },
        )
    }

    pendingAlbumPlaylist?.let { album ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name)
                coroutineScope.launch {
                    database.insertPlaylist(playlist)
                    addAlbumSongsToPlaylist(album, playlist.id)
                }
                pendingAlbumPlaylist = null
            },
            onSelectPlaylist = { playlist ->
                coroutineScope.launch {
                    addAlbumSongsToPlaylist(album, playlist.id)
                }
                pendingAlbumPlaylist = null
            },
            onDismiss = { pendingAlbumPlaylist = null },
        )
    }

    ArtistPickerDialog(
        visible = pendingArtists.isNotEmpty(),
        artists = pendingArtists,
        onSelect = selectArtist@{ artist ->
            val artistId = artist.id ?: return@selectArtist
            onOpenArtist(artistId, artist.name)
            pendingArtists = emptyList()
        },
        onDismiss = { pendingArtists = emptyList() },
    )

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreChartListItem(
    song: SongItem,
    libraryItem: LibraryItem,
    itemWidth: Dp,
    isActive: Boolean,
    isPlaying: Boolean,
    songEntity: SongEntity?,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    menuActions: List<ContextMenuAction>,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val menuExpanded = remember(song.id) { mutableStateOf(false) }
    val artistNames = song.artists.joinToString { it.name }
    val durationLabel = song.duration?.let { formatTime(it * 1000L) }
    val subtitle = joinByBullet(artistNames, durationLabel) ?: artistNames
    val showLike = songEntity?.liked == true
    val showLibrary = songEntity?.inLibrary != null
    val showDownload = downloadState != null || isDownloaded

    Row(
        modifier = Modifier
            .width(itemWidth)
            .height(ListItemHeight)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded.value = true },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
            RemoteImage(
                url = song.thumbnail,
                modifier = Modifier.size(ListThumbnailSize),
                shape = RoundedCornerShape(ThumbnailCornerRadius),
            )
            PlayingIndicatorBox(
                isActive = isActive,
                isPlaying = isPlaying,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(ListThumbnailSize)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                    ),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (song.explicit) {
                    Text(
                        text = stringResource("explicit_badge"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showLike) {
                    Icon(
                        imageVector = IconAssets.favorite(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (showLibrary) {
                    Icon(
                        imageVector = IconAssets.libraryMusic(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (showDownload) {
                    Icon(
                        imageVector = IconAssets.download(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuExpanded.value = true }) {
                Icon(IconAssets.moreVert(), contentDescription = null)
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreGridItem(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    libraryItem: LibraryItem?,
    menuActions: List<ContextMenuAction>,
    songEntity: SongEntity?,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onMenuOpened: (() -> Unit)? = null,
    menuHeader: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onAlbumPlay: (() -> Unit)? = null,
) {
    val menuExpanded = remember(item.id) { mutableStateOf(false) }
    val subtitle = when (item) {
        is SongItem -> joinByBullet(
            item.artists.joinToString { it.name },
            item.duration?.let { formatTime(it * 1000L) }
        )
        is AlbumItem -> joinByBullet(
            item.artists?.joinToString { it.name },
            item.year?.toString()
        )
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        is ArtistItem -> null
        else -> null
    }

    Box {
        Column(
            modifier = Modifier
                .width(GridThumbnailSize)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (menuActions.isNotEmpty() && libraryItem != null) {
                            onMenuOpened?.invoke()
                            menuExpanded.value = true
                        }
                    },
                )
                .padding(4.dp),
        ) {
            Box(modifier = Modifier.size(GridThumbnailSize)) {
                val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(8.dp)
                RemoteImage(
                    url = item.thumbnail,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = ActiveBoxAlpha), shape),
                    )
                    PlayingIndicatorBox(
                        isActive = isActive,
                        isPlaying = isPlaying,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (item is SongItem) {
                    OverlayPlayButton(visible = !isActive)
                }
                if (item is AlbumItem) {
                    AlbumPlayButton(visible = !isActive, onClick = { onAlbumPlay?.invoke() })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (item is SongItem) {
                ExploreBadgesRow(
                    songEntity = songEntity,
                    isExplicit = item.explicit,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded,
                )
            }
        }

        if (menuActions.isNotEmpty() && libraryItem != null) {
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
@Composable
private fun ExploreShimmer() {
    ShimmerHost {
        TextPlaceholder(
            height = 36.dp,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(0.5f),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

            LazyHorizontalGrid(
                rows = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight * 4),
            ) {
                items(4) {
                    Row(
                        modifier = Modifier
                            .width(horizontalLazyGridItemWidth)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ListItemHeight - 16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.height(ListItemHeight - 16.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(120.dp)
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(12.dp)
                                    .width(80.dp)
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                }
            }
        }

        TextPlaceholder(
            height = 36.dp,
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .width(250.dp),
        )
        Row {
            repeat(2) {
                GridItemPlaceholder()
            }
        }

        TextPlaceholder(
            height = 36.dp,
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .width(250.dp),
        )
        Row {
            repeat(2) {
                GridItemPlaceholder()
            }
        }

        TextPlaceholder(
            height = 36.dp,
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .width(250.dp),
        )
        repeat(4) {
            Row {
                repeat(2) {
                    TextPlaceholder(
                        height = MoodAndGenresButtonHeight,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(180.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreBadgesRow(
    songEntity: SongEntity?,
    isExplicit: Boolean,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
) {
    val showLiked = songEntity?.liked == true
    val showLibrary = songEntity?.inLibrary != null
    val showDownload = shouldShowDownloadIcon(downloadState, isDownloaded)
    if (!showLiked && !isExplicit && !showLibrary && !showDownload) return

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLiked) {
            Icon(
                imageVector = IconAssets.favorite(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (isExplicit) {
            Text(
                text = stringResource("explicit_badge"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showLibrary) {
            Icon(
                imageVector = IconAssets.libraryMusic(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (showDownload) {
            Icon(
                imageVector = IconAssets.download(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ExploreAlbumMenuHeader(
    album: AlbumItem,
    albumEntity: AlbumEntity?,
    onToggleLike: () -> Unit,
) {
    val isLiked = albumEntity?.bookmarkedAt != null
    val artists = album.artists?.joinToString { it.name }.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RemoteImage(
            url = album.thumbnail,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artists.isNotBlank()) {
                Text(
                    text = artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) IconAssets.favorite() else IconAssets.favoriteBorder(),
                contentDescription = null,
                tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun albumItemToLibraryItem(album: AlbumItem): LibraryItem {
    return LibraryItem(
        id = album.browseId,
        title = album.title,
        artist = album.artists?.joinToString { it.name } ?: "",
        artworkUrl = album.thumbnail,
        playbackUrl = album.shareLink,
        durationMs = null,
    )
}

@Composable
private fun MoodAndGenresButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(MoodAndGenresButtonHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BoxScope.OverlayPlayButton(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(OverlayButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha)),
        ) {
            Icon(
                imageVector = IconAssets.play(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(OverlayIconSize),
            )
        }
    }
}

@Composable
private fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(OverlayButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = IconAssets.play(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(OverlayIconSize),
            )
        }
    }
}

private data class ExploreSongTarget(
    val item: LibraryItem,
    val songItem: SongItem?,
)

private data class AlbumMenuState(
    val songs: List<SongItem>,
)

private enum class AlbumDownloadAction {
    DOWNLOAD,
    CANCEL,
    REMOVE,
}

private data class AlbumDownloadMenuState(
    val label: String,
    val action: AlbumDownloadAction,
)

private fun mapExploreChartsTitle(title: String?, strings: StringResolver): String {
    return when (title) {
        "Trending" -> strings.get("trending")
        else -> title ?: strings.get("charts")
    }
}

private fun joinByBullet(first: String?, second: String?): String? {
    val parts = listOfNotNull(first?.takeIf { it.isNotBlank() }, second?.takeIf { it.isNotBlank() })
    return if (parts.isEmpty()) null else parts.joinToString(" • ")
}
