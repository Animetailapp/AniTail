package com.anitail.music.playback

import com.anitail.music.utils.MediaStoreHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreDownloadUtilsTest {
    @Test
    fun `extensionFromMimeType uses weba for audio webm and preserves audio extensions`() {
        assertEquals("weba", MediaStoreDownloadManager.extensionFromMimeType("audio/webm"))
        assertEquals("weba", MediaStoreDownloadManager.extensionFromMimeType("AUDIO/WEBM"))
        assertEquals("m4a", MediaStoreDownloadManager.extensionFromMimeType("audio/mp4"))
        assertEquals("mp3", MediaStoreDownloadManager.extensionFromMimeType("audio/mpeg"))
        assertEquals("ogg", MediaStoreDownloadManager.extensionFromMimeType("audio/ogg"))
        assertEquals("aac", MediaStoreDownloadManager.extensionFromMimeType("audio/aac"))
        assertEquals("flac", MediaStoreDownloadManager.extensionFromMimeType("audio/flac"))
        assertEquals("m4a", MediaStoreDownloadManager.extensionFromMimeType("application/octet-stream"))
    }

    @Test
    fun `buildDownloadRequestHeaders forwards stream headers and replaces range`() {
        val headers = MediaStoreDownloadManager.buildDownloadRequestHeaders(
            streamHeaders = mapOf(
                "User-Agent" to "CustomClient/1.0",
                "Origin" to "https://music.youtube.com",
                "Range" to "bytes=0-1",
            ),
            rangeHeader = "bytes=100-200",
        )

        assertEquals("CustomClient/1.0", headers["User-Agent"])
        assertEquals("https://music.youtube.com", headers["Origin"])
        assertEquals("*/*", headers["Accept"])
        assertEquals("en-US,en;q=0.9", headers["Accept-Language"])
        assertEquals("bytes=100-200", headers["Range"])
    }

    @Test
    fun `normalizeAudioFileName rewrites webm suffix to weba only for audio webm`() {
        assertEquals(
            "Imagine Dragons - Natural.weba",
            MediaStoreHelper.normalizeAudioFileName("Imagine Dragons - Natural.webm", "audio/webm"),
        )
        assertEquals(
            "Imagine Dragons - Natural.weba",
            MediaStoreHelper.normalizeAudioFileName("Imagine Dragons - Natural.WEBM", "audio/webm; codecs=\"opus\""),
        )
        assertEquals(
            "Imagine Dragons - Natural.m4a",
            MediaStoreHelper.normalizeAudioFileName("Imagine Dragons - Natural.m4a", "audio/mp4"),
        )
    }
}

