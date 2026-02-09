package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionDownloadStateTest {
    @Test
    fun resolvesDownloadedWhenAllItemsDownloaded() {
        val songIds = listOf("a", "b")
        val downloaded = listOf(
            DownloadedSong("a", "A", "Artist", null, null, 0, "a.mp3", 100, null, 0),
            DownloadedSong("b", "B", "Artist", null, null, 0, "b.mp3", 100, null, 0),
        )

        val result = resolveSelectionDownloadState(songIds, emptyMap(), downloaded)

        assertEquals(SelectionDownloadState.DOWNLOADED, result)
    }

    @Test
    fun resolvesDownloadingWhenAnyItemDownloading() {
        val songIds = listOf("a", "b")
        val states = mapOf(
            "a" to DownloadState("a", "A", "Artist", null, DownloadStatus.DOWNLOADING, 0.4f, 40, 100, null),
        )
        val downloaded = emptyList<DownloadedSong>()

        val result = resolveSelectionDownloadState(songIds, states, downloaded)

        assertEquals(SelectionDownloadState.DOWNLOADING, result)
    }

    @Test
    fun resolvesNotDownloadedWhenNothingDownloadedOrDownloading() {
        val songIds = listOf("a", "b")
        val result = resolveSelectionDownloadState(songIds, emptyMap(), emptyList())

        assertEquals(SelectionDownloadState.NOT_DOWNLOADED, result)
    }
}
