package com.anitail.desktop.player

import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.response.PlayerResponse
import kotlin.test.Test
import kotlin.test.assertNull

class StreamUrlResolutionTest {
    @Test
    fun resolveStreamUrlDoesNotOverrideUserAgent() {
        val resolver = CapturingResolver()
        val format = sampleFormat()

        StreamUrlResolution.resolveStreamUrl(resolver, format, "videoId", YouTubeClient.IOS)

        assertNull(resolver.lastUserAgentOverride)
    }

    private fun sampleFormat() = PlayerResponse.StreamingData.Format(
        itag = 140,
        url = "https://example.com/audio",
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

    private class CapturingResolver : StreamUrlResolver {
        var lastUserAgentOverride: String? = null

        override fun resolve(
            format: PlayerResponse.StreamingData.Format,
            videoId: String,
            userAgentOverride: String?
        ): Result<String> {
            lastUserAgentOverride = userAgentOverride
            return Result.success("https://example.com/stream")
        }
    }
}
