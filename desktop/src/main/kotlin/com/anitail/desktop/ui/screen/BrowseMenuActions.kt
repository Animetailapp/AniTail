package com.anitail.desktop.ui.screen

import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.extractVideoId
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
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

fun buildBrowseSongMenuActions(
    libraryItem: LibraryItem,
    songItem: SongItem?,
    songsById: Map<String, SongEntity>,
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
    val hasArtist = if (songItem != null) {
        artistCandidates.isNotEmpty()
    } else {
        !songEntity?.artistId.isNullOrBlank()
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
        songId = libraryItem.id,
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
    )
    val isInLibrary = songEntity?.inLibrary != null
    val shareUrl = songItem?.shareLink ?: libraryItem.playbackUrl

    return buildHomeSongMenuActions(
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
                    database.insertSong(entity)
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
                !songEntity?.artistId.isNullOrBlank() -> {
                    val artistId = songEntity.artistId ?: return@openArtist
                    onOpenArtist(artistId, songEntity.artistName)
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

fun buildBrowseCollectionMenuActions(
    item: YTItem,
    onOpen: () -> Unit,
    copyToClipboard: (String) -> Unit,
): List<ContextMenuAction> {
    val shareUrl = item.shareLink
    return listOf(
        ContextMenuAction(
            label = "Abrir",
            icon = IconAssets.info(),
            onClick = onOpen,
        ),
        ContextMenuAction(
            label = "Copiar enlace",
            icon = IconAssets.link(),
            onClick = { copyToClipboard(shareUrl) },
        ),
        ContextMenuAction(
            label = "Compartir",
            icon = IconAssets.share(),
            onClick = { copyToClipboard(shareUrl) },
        ),
    )
}

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
