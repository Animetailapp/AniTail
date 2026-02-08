package com.anitail.desktop.ui.screen

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueSelectionLogicTest {
    @Test
    fun returnsCompletedWhenAllDownloaded() {
        val selectedIds = listOf("a", "b")
        val downloadedSongs = listOf(
            DownloadedSong("a", "A", "Artist", null, null, 0, "a.mp3", 1, null, 0),
            DownloadedSong("b", "B", "Artist", null, null, 0, "b.mp3", 1, null, 0),
        )

        val state = computeSelectionDownloadState(
            selectedIds = selectedIds,
            downloadStates = emptyMap(),
            downloadedSongs = downloadedSongs,
        )

        assertEquals(SelectionDownloadState.COMPLETED, state)
    }

    @Test
    fun returnsDownloadingWhenAllQueuedOrDownloading() {
        val selectedIds = listOf("a", "b")
        val downloadStates = mapOf(
            "a" to DownloadState("a", "A", "Artist", null, DownloadStatus.DOWNLOADING, 0.5f, 50, 100, null),
            "b" to DownloadState("b", "B", "Artist", null, DownloadStatus.QUEUED, 0f, 0, 100, null),
        )

        val state = computeSelectionDownloadState(
            selectedIds = selectedIds,
            downloadStates = downloadStates,
            downloadedSongs = emptyList(),
        )

        assertEquals(SelectionDownloadState.DOWNLOADING, state)
    }

    @Test
    fun returnsNotDownloadedWhenMissingStates() {
        val selectedIds = listOf("a", "b")
        val downloadStates = mapOf(
            "a" to DownloadState("a", "A", "Artist", null, DownloadStatus.DOWNLOADING, 0.5f, 50, 100, null),
        )

        val state = computeSelectionDownloadState(
            selectedIds = selectedIds,
            downloadStates = downloadStates,
            downloadedSongs = emptyList(),
        )

        assertEquals(SelectionDownloadState.NOT_DOWNLOADED, state)
    }
}
