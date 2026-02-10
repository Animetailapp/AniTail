package com.anitail.desktop.ui.screen

import androidx.compose.runtime.Composable

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver

enum class CollectionDownloadAction {
    DOWNLOAD,
    CANCEL,
    REMOVE,
}

data class CollectionDownloadMenuState(
    val label: String,
    val action: CollectionDownloadAction,
)

@Composable
fun resolveCollectionDownloadMenuState(
    songIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    showWhenEmpty: Boolean,
): CollectionDownloadMenuState? = resolveCollectionDownloadMenuState(
    strings = LocalStrings.current,
    songIds = songIds,
    downloadStates = downloadStates,
    downloadedSongs = downloadedSongs,
    showWhenEmpty = showWhenEmpty,
)

fun resolveCollectionDownloadMenuState(
    strings: StringResolver,
    songIds: List<String>,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    showWhenEmpty: Boolean,
): CollectionDownloadMenuState? {
    if (songIds.isEmpty()) {
        return if (showWhenEmpty) {
            CollectionDownloadMenuState(label = strings.get("download"), action = CollectionDownloadAction.DOWNLOAD)
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
            label = strings.get("remove_download"),
            action = CollectionDownloadAction.REMOVE,
        )
        anyActive -> CollectionDownloadMenuState(
            label = strings.get("downloading_to_device"),
            action = CollectionDownloadAction.CANCEL,
        )
        else -> CollectionDownloadMenuState(
            label = strings.get("download"),
            action = CollectionDownloadAction.DOWNLOAD,
        )
    }
}
