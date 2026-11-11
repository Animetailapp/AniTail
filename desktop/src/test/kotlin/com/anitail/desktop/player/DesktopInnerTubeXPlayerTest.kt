package com.anitail.desktop.player

import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.response.PlayerResponse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopInnerTubeXPlayerTest {

    @Test
    fun innerTubeXResolverPrefersDirectUrl() {
        val format = PlayerResponse.StreamingData.Format(
            itag = 140,
            url = "https://rr---sn.googlevideo.com/videoplayback?expire=123",
            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
            bitrate = 128000,
            width = null,
            height = null,
            contentLength = 1000,
            quality = "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = 128000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = "1000",
            audioSampleRate = 44100,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
        )

        val result = InnerTubeXStreamUrlResolver.resolve(format, "testVideoId", null)
        assertTrue(result.isSuccess)
        assertEquals("https://rr---sn.googlevideo.com/videoplayback?expire=123", result.getOrNull())
    }

    @Test
    fun desktopInnerTubeXPlayerLoadsBundledPlayerConfigs() {
        // Verify bundled player_configs.json is reachable on Desktop classpath
        val stream = DesktopInnerTubeXPlayer::class.java.classLoader?.getResourceAsStream("player_configs.json")
        assertNotNull(stream, "player_configs.json should be present on Desktop classpath")
        val text = stream.bufferedReader().use { it.readText() }
        assertTrue(text.isNotBlank(), "player_configs.json should not be blank")
        assertTrue(text.contains("players"), "player_configs.json should contain Faraday players registry")
    }

    @Test
    fun streamClientFailureTracking() {
        DesktopInnerTubeXPlayer.clearStreamClientFailures()
        DesktopInnerTubeXPlayer.markStreamClientFailed("vid123", "WEB_REMIX")
        // No exceptions and failure was recorded
        DesktopInnerTubeXPlayer.clearStreamClientFailures()
    }
}

