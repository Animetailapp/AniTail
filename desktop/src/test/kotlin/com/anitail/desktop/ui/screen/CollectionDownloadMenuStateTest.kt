package com.anitail.desktop.ui.screen

import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.testStringResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionDownloadMenuStateTest {
    @Test
    fun returnsNullWhenEmptyAndHidden() {
        val result = resolveCollectionDownloadMenuState(
            strings = testStringResolver(),
            songIds = emptyList(),
            downloadStates = emptyMap(),
            downloadedSongs = emptyList(),
            showWhenEmpty = false,
        )

        assertNull(result)
    }

    @Test
    fun returnsDownloadWhenEmptyAndShown() {
        val result = resolveCollectionDownloadMenuState(
            strings = testStringResolver(),
            songIds = emptyList(),
            downloadStates = emptyMap(),
            downloadedSongs = emptyList(),
            showWhenEmpty = true,
        )

        assertEquals("Descargar", result?.label)
        assertEquals(CollectionDownloadAction.DOWNLOAD, result?.action)
    }

    @Test
    fun returnsRemoveWhenAllDownloaded() {
        val songIds = listOf("a", "b")
        val result = resolveCollectionDownloadMenuState(
            strings = testStringResolver(),
            songIds = songIds,
            downloadStates = emptyMap(),
            downloadedSongs = songIds.map { id ->
                DownloadedSong(
                    songId = id,
                    title = "t",
                    artist = "a",
                    album = null,
                    thumbnailUrl = null,
                    duration = 0,
                    filePath = "x",
                    fileSize = 1L,
                    bitrate = null,
                    downloadedAt = 0L,
                )
            },
            showWhenEmpty = true,
        )

        assertEquals("Eliminar descarga", result?.label)
        assertEquals(CollectionDownloadAction.REMOVE, result?.action)
    }

    @Test
    fun returnsCancelWhenAnyActive() {
        val songIds = listOf("a", "b")
        val downloadStates = mapOf(
            "a" to DownloadState(
                songId = "a",
                title = "t",
                artist = "a",
                thumbnailUrl = null,
                status = DownloadStatus.DOWNLOADING,
                progress = 0.2f,
                downloadedBytes = 1L,
                totalBytes = 10L,
                filePath = null,
                errorMessage = null,
            )
        )

        val result = resolveCollectionDownloadMenuState(
            strings = testStringResolver(),
            songIds = songIds,
            downloadStates = downloadStates,
            downloadedSongs = emptyList(),
            showWhenEmpty = true,
        )

        assertEquals("Descargando", result?.label)
        assertEquals(CollectionDownloadAction.CANCEL, result?.action)
    }
}
