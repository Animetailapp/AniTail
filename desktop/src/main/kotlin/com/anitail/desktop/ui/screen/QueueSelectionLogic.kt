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

    val downloadedIds = downloadedSongs.mapTo(HashSet(downloadedSongs.size)) { it.songId }
    return computeSelectionDownloadState(
        selectedIds = selectedIds,
        downloadStates = downloadStates,
        downloadedIds = downloadedIds,
    )
}

internal fun computeSelectionDownloadState(
    selectedIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedIds: Set<String>,
): SelectionDownloadState {
    if (selectedIds.isEmpty()) return SelectionDownloadState.NONE
    if (selectedIds.all { it in downloadedIds }) return SelectionDownloadState.COMPLETED

    val allDownloadingOrCompleted = selectedIds.all { id ->
        if (id in downloadedIds) return@all true
        when (downloadStates[id]?.status) {
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.COMPLETED -> true
            else -> false
        }
    }

    return if (allDownloadingOrCompleted) {
        SelectionDownloadState.DOWNLOADING
    } else {
        SelectionDownloadState.NOT_DOWNLOADED
    }
}

internal fun computeSingleDownloadState(
    songId: String,
    downloadStates: Map<String, DownloadState>,
    downloadedIds: Set<String>,
): SelectionDownloadState {
    if (songId in downloadedIds) return SelectionDownloadState.COMPLETED
    return when (downloadStates[songId]?.status) {
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.COMPLETED -> SelectionDownloadState.DOWNLOADING
        else -> SelectionDownloadState.NOT_DOWNLOADED
    }
}
