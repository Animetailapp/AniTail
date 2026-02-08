package com.anitail.desktop.ui.screen

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong

internal enum class SelectionDownloadState {
    NONE,
    COMPLETED,
    DOWNLOADING,
    NOT_DOWNLOADED,
}

internal fun computeSelectionDownloadState(
    selectedIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
): SelectionDownloadState {
    if (selectedIds.isEmpty()) return SelectionDownloadState.NONE

    val downloadedIds = downloadedSongs.map { it.songId }.toSet()
    if (selectedIds.all { it in downloadedIds }) return SelectionDownloadState.COMPLETED

    val allDownloadingOrCompleted = selectedIds.all { id ->
        id in downloadedIds ||
            downloadStates[id]?.status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.COMPLETED,
            )
    }

    return if (allDownloadingOrCompleted) {
        SelectionDownloadState.DOWNLOADING
    } else {
        SelectionDownloadState.NOT_DOWNLOADED
    }
}
