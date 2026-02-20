package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong

enum class SelectionDownloadState {
    DOWNLOADED,
    DOWNLOADING,
    NOT_DOWNLOADED,
}

fun resolveSelectionDownloadState(
    songIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
): SelectionDownloadState {
    if (songIds.isEmpty()) return SelectionDownloadState.NOT_DOWNLOADED

    val downloadedIds = downloadedSongs.map { it.songId }.toSet()
    val allDownloaded = songIds.all { it in downloadedIds }
    if (allDownloaded) return SelectionDownloadState.DOWNLOADED

    val anyDownloading = songIds.any { id ->
        when (downloadStates[id]?.status) {
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            -> true
            else -> false
        }
    }

    return if (anyDownloading) SelectionDownloadState.DOWNLOADING else SelectionDownloadState.NOT_DOWNLOADED
}
