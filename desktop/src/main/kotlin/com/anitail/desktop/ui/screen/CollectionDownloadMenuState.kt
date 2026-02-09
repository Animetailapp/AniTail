package com.anitail.desktop.ui.screen

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong

enum class CollectionDownloadAction {
    DOWNLOAD,
    CANCEL,
    REMOVE,
}

data class CollectionDownloadMenuState(
    val label: String,
    val action: CollectionDownloadAction,
)

fun resolveCollectionDownloadMenuState(
    songIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    showWhenEmpty: Boolean,
): CollectionDownloadMenuState? {
    if (songIds.isEmpty()) {
        return if (showWhenEmpty) {
            CollectionDownloadMenuState(label = "Descargar", action = CollectionDownloadAction.DOWNLOAD)
        } else {
            null
        }
    }

    val downloadedIds = downloadedSongs.map { it.songId }.toSet()
    val statuses = songIds.mapNotNull { id -> downloadStates[id]?.status }
    val allDownloaded = songIds.all { id -> downloadedIds.contains(id) }
    val anyActive = statuses.any { status ->
        status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
    }

    return when {
        allDownloaded -> CollectionDownloadMenuState(
            label = "Eliminar descarga",
            action = CollectionDownloadAction.REMOVE,
        )
        anyActive -> CollectionDownloadMenuState(
            label = "Descargando",
            action = CollectionDownloadAction.CANCEL,
        )
        else -> CollectionDownloadMenuState(
            label = "Descargar",
            action = CollectionDownloadAction.DOWNLOAD,
        )
    }
}

