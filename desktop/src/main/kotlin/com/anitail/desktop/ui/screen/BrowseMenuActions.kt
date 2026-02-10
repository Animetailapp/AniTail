package com.anitail.desktop.ui.screen

import androidx.compose.runtime.Composable
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.extractVideoId
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.models.YTItem
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class BrowseSongTarget(
    val item: LibraryItem,
    val songItem: SongItem?,
)

@Composable
fun buildBrowseSongMenuActions(
    libraryItem: LibraryItem,
    songItem: SongItem?,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    coroutineScope: CoroutineScope,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onRequestPlaylist: (BrowseSongTarget) -> Unit,
    onRequestArtists: (List<Artist>) -> Unit,
    onShowDetails: (LibraryItem) -> Unit,
    copyToClipboard: (String) -> Unit,
): List<ContextMenuAction> = buildBrowseSongMenuActions(
    strings = LocalStrings.current,
    libraryItem = libraryItem,
    songItem = songItem,
    songsById = songsById,
    songArtistMaps = songArtistMaps,
    downloadStates = downloadStates,
    downloadedSongs = downloadedSongs,
    database = database,
    downloadService = downloadService,
    playerState = playerState,
    coroutineScope = coroutineScope,
    onOpenArtist = onOpenArtist,
    onOpenAlbum = onOpenAlbum,
    onRequestPlaylist = onRequestPlaylist,
    onRequestArtists = onRequestArtists,
    onShowDetails = onShowDetails,
    copyToClipboard = copyToClipboard,
)

fun buildBrowseSongMenuActions(
    strings: StringResolver,
    libraryItem: LibraryItem,
    songItem: SongItem?,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    coroutineScope: CoroutineScope,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onRequestPlaylist: (BrowseSongTarget) -> Unit,
    onRequestArtists: (List<Artist>) -> Unit,
    onShowDetails: (LibraryItem) -> Unit,
    copyToClipboard: (String) -> Unit,
): List<ContextMenuAction> {
    val songEntity = songsById[libraryItem.id]
    val artistCandidates = songItem?.artists
        ?.mapNotNull { artist -> artist.id?.let { Artist(artist.name, it) } }
        .orEmpty()
    val libraryArtistId = songEntity?.let { primaryArtistIdForSong(libraryItem.id, songArtistMaps) }
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
        onAddToPlaylist = { onRequestPlaylist(BrowseSongTarget(libraryItem, songItem)) },
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
                    onRequestArtists(artistCandidates)
                }
                !libraryArtistId.isNullOrBlank() -> {
                    val artistId = libraryArtistId ?: return@openArtist
                    onOpenArtist(artistId, songEntity?.artistName)
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
                    val albumId = songEntity.albumId ?: return@openAlbum
                    onOpenAlbum(albumId, songEntity.albumName)
                }
            }
        },
        onShare = { copyToClipboard(shareUrl) },
        onDetails = { onShowDetails(libraryItem) },
    )
}

fun buildBrowseAlbumMenuActions(
    strings: StringResolver,
    hasArtists: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onOpenArtist: () -> Unit,
    onShare: () -> Unit,
): List<ContextMenuAction> {
    val actions = mutableListOf(
        ContextMenuAction(
            label = strings.get("start_radio"),
            icon = IconAssets.radio(),
            onClick = onStartRadio,
        ),
        ContextMenuAction(
            label = strings.get("play_next"),
            icon = IconAssets.playlistPlay(),
            onClick = onPlayNext,
        ),
        ContextMenuAction(
            label = strings.get("add_to_queue"),
            icon = IconAssets.queueMusic(),
            onClick = onAddToQueue,
        ),
        ContextMenuAction(
            label = strings.get("add_to_playlist"),
            icon = IconAssets.playlistAdd(),
            onClick = onAddToPlaylist,
        ),
        ContextMenuAction(
            label = downloadLabel,
            icon = IconAssets.download(),
            onClick = onDownload,
            enabled = downloadEnabled,
        ),
    )

    if (hasArtists) {
        actions.add(
            ContextMenuAction(
                label = strings.get("view_artist"),
                icon = IconAssets.artist(),
                onClick = onOpenArtist,
            ),
        )
    }

    actions.add(
        ContextMenuAction(
            label = strings.get("share"),
            icon = IconAssets.share(),
            onClick = onShare,
        ),
    )

    return actions
}

@Composable
fun buildBrowseAlbumMenuActions(
    hasArtists: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onOpenArtist: () -> Unit,
    onShare: () -> Unit,
): List<ContextMenuAction> = buildBrowseAlbumMenuActions(
    strings = LocalStrings.current,
    hasArtists = hasArtists,
    downloadLabel = downloadLabel,
    downloadEnabled = downloadEnabled,
    onStartRadio = onStartRadio,
    onPlayNext = onPlayNext,
    onAddToQueue = onAddToQueue,
    onAddToPlaylist = onAddToPlaylist,
    onDownload = onDownload,
    onOpenArtist = onOpenArtist,
    onShare = onShare,
)

fun buildBrowsePlaylistMenuActions(
    strings: StringResolver,
    canPlay: Boolean,
    canShuffle: Boolean,
    canRadio: Boolean,
    showDownload: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
): List<ContextMenuAction> {
    val actions = mutableListOf<ContextMenuAction>()

    if (canPlay) {
        actions.add(
            ContextMenuAction(
                label = strings.get("play"),
                icon = IconAssets.play(),
                onClick = onPlay,
            ),
        )
    }
    if (canShuffle) {
        actions.add(
            ContextMenuAction(
                label = strings.get("shuffle"),
                icon = IconAssets.shuffle(),
                onClick = onShuffle,
            ),
        )
    }
    if (canRadio) {
        actions.add(
            ContextMenuAction(
                label = strings.get("start_radio"),
                icon = IconAssets.radio(),
                onClick = onStartRadio,
            ),
        )
    }

    actions.add(
        ContextMenuAction(
            label = strings.get("play_next"),
            icon = IconAssets.playlistPlay(),
            onClick = onPlayNext,
        ),
    )
    actions.add(
        ContextMenuAction(
            label = strings.get("add_to_queue"),
            icon = IconAssets.queueMusic(),
            onClick = onAddToQueue,
        ),
    )
    actions.add(
        ContextMenuAction(
            label = strings.get("add_to_playlist"),
            icon = IconAssets.playlistAdd(),
            onClick = onAddToPlaylist,
        ),
    )
    if (showDownload) {
        actions.add(
            ContextMenuAction(
                label = downloadLabel,
                icon = IconAssets.download(),
                onClick = onDownload,
                enabled = downloadEnabled,
            ),
        )
    }
    actions.add(
        ContextMenuAction(
            label = strings.get("share"),
            icon = IconAssets.share(),
            onClick = onShare,
        ),
    )

    return actions
}

@Composable
fun buildBrowsePlaylistMenuActions(
    canPlay: Boolean,
    canShuffle: Boolean,
    canRadio: Boolean,
    showDownload: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
): List<ContextMenuAction> = buildBrowsePlaylistMenuActions(
    strings = LocalStrings.current,
    canPlay = canPlay,
    canShuffle = canShuffle,
    canRadio = canRadio,
    showDownload = showDownload,
    downloadLabel = downloadLabel,
    downloadEnabled = downloadEnabled,
    onPlay = onPlay,
    onShuffle = onShuffle,
    onStartRadio = onStartRadio,
    onPlayNext = onPlayNext,
    onAddToQueue = onAddToQueue,
    onAddToPlaylist = onAddToPlaylist,
    onDownload = onDownload,
    onShare = onShare,
)

fun buildBrowseArtistMenuActions(
    strings: StringResolver,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
): List<ContextMenuAction> {
    return listOf(
        ContextMenuAction(
            label = if (isSubscribed) strings.get("subscribed") else strings.get("subscribe"),
            icon = if (isSubscribed) IconAssets.subscribed() else IconAssets.subscribe(),
            onClick = onToggleSubscribe,
        ),
    )
}

@Composable
fun buildBrowseArtistMenuActions(
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
): List<ContextMenuAction> = buildBrowseArtistMenuActions(
    strings = LocalStrings.current,
    isSubscribed = isSubscribed,
    onToggleSubscribe = onToggleSubscribe,
)

fun shouldShowDownloadIcon(
    downloadState: DownloadState?,
    isDownloaded: Boolean,
): Boolean {
    if (isDownloaded) return true
    return when (downloadState?.status) {
        DownloadStatus.DOWNLOADING,
        DownloadStatus.QUEUED,
        DownloadStatus.COMPLETED,
        DownloadStatus.FAILED,
        DownloadStatus.PAUSED,
        DownloadStatus.CANCELLED,
        -> true
        null -> false
    }
}
