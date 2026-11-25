package com.anitail.music.utils

import com.anitail.innertube.models.response.PlayerResponse
import com.anitail.music.constants.AudioQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YTPlayerUtilsTargetItagTest {
    private fun fakeFormat(
        itag: Int,
        mimeType: String,
        bitrate: Int,
    ): PlayerResponse.StreamingData.Format =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = "https://example.com/$itag",
            mimeType = mimeType,
            bitrate = bitrate,
            width = null,
            height = null,
            contentLength = 1_000_000L,
            quality = "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = bitrate,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = "180000",
            audioSampleRate = 44100,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
        )

    private fun fakePlaybackData(
        itag: Int,
        mimeType: String,
        bitrate: Int = 128_000,
    ): YTPlayerUtils.PlaybackData =
        YTPlayerUtils.PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format = fakeFormat(itag = itag, mimeType = mimeType, bitrate = bitrate),
            streamUrl = "https://example.com/$itag",
            streamExpiresInSeconds = 3600,
            streamClient = "TEST",
        )

    private fun fakePlayerResponse(formats: List<PlayerResponse.StreamingData.Format>): PlayerResponse =
        PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(
                formats = null,
                adaptiveFormats = formats,
                expiresInSeconds = 3600,
            ),
            videoDetails = null,
            playbackTracking = null,
        )

    @Test
    fun `resolvePlaybackWithTargetItag preserves normal path when targetItag is zero`() =
        runBlocking {
            val expected = fakePlaybackData(251, "audio/webm; codecs=\"opus\"")
            var legacyCalled = false
            val result = YTPlayerUtils.resolvePlaybackWithTargetItag(
                targetItag = 0,
                requestedContainer = null,
                resolveInnerTubeX = { Result.success(expected) },
                resolveLegacy = { _, _ ->
                    legacyCalled = true
                    Result.failure(IllegalStateException("should not run"))
                },
            )

            assertEquals(expected, result.getOrNull())
            assertFalse(legacyCalled)
        }

    @Test
    fun `resolvePlaybackWithTargetItag returns InnerTubeX immediately on exact itag match`() =
        runBlocking {
            val expected = fakePlaybackData(140, "audio/mp4; codecs=\"mp4a.40.2\"")
            var legacyCalled = false
            val result = YTPlayerUtils.resolvePlaybackWithTargetItag(
                targetItag = 140,
                requestedContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
                resolveInnerTubeX = { Result.success(expected) },
                resolveLegacy = { _, _ ->
                    legacyCalled = true
                    Result.failure(IllegalStateException("should not run"))
                },
            )

            assertEquals(expected, result.getOrNull())
            assertFalse(legacyCalled)
        }

    @Test
    fun `resolvePlaybackWithTargetItag uses exact legacy stream when InnerTubeX returns different itag`() =
        runBlocking {
            val innertubexWrong = fakePlaybackData(251, "audio/webm; codecs=\"opus\"")
            val legacyExact = fakePlaybackData(140, "audio/mp4; codecs=\"mp4a.40.2\"")
            val legacyCalls = mutableListOf<Pair<Boolean, InnerTubeXPlayer.TargetAudioContainer?>>()

            val result = YTPlayerUtils.resolvePlaybackWithTargetItag(
                targetItag = 140,
                requestedContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
                resolveInnerTubeX = { Result.success(innertubexWrong) },
                resolveLegacy = { exact, container ->
                    legacyCalls += exact to container
                    Result.success(legacyExact)
                },
            )

            assertEquals(legacyExact, result.getOrNull())
            assertEquals(
                listOf(true to InnerTubeXPlayer.TargetAudioContainer.MP4),
                legacyCalls,
            )
        }

    @Test
    fun `resolvePlaybackWithTargetItag keeps container-matched InnerTubeX stream if exact legacy fails`() =
        runBlocking {
            val innertubexMp4 = fakePlaybackData(139, "audio/mp4; codecs=\"mp4a.40.5\"", bitrate = 48_000)
            val legacyCalls = mutableListOf<Pair<Boolean, InnerTubeXPlayer.TargetAudioContainer?>>()

            val result = YTPlayerUtils.resolvePlaybackWithTargetItag(
                targetItag = 140,
                requestedContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
                resolveInnerTubeX = { Result.success(innertubexMp4) },
                resolveLegacy = { exact, container ->
                    legacyCalls += exact to container
                    Result.failure(IllegalStateException("no exact match"))
                },
            )

            assertEquals(innertubexMp4, result.getOrNull())
            assertEquals(
                listOf(true to InnerTubeXPlayer.TargetAudioContainer.MP4),
                legacyCalls,
            )
        }

    @Test
    fun `resolvePlaybackWithTargetItag prefers container-matched legacy over wrong-container InnerTubeX`() =
        runBlocking {
            val innertubexWebm = fakePlaybackData(251, "audio/webm; codecs=\"opus\"")
            val legacyContainerMp4 = fakePlaybackData(139, "audio/mp4; codecs=\"mp4a.40.5\"", bitrate = 48_000)
            val legacyCalls = mutableListOf<Pair<Boolean, InnerTubeXPlayer.TargetAudioContainer?>>()

            val result = YTPlayerUtils.resolvePlaybackWithTargetItag(
                targetItag = 140,
                requestedContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
                resolveInnerTubeX = { Result.success(innertubexWebm) },
                resolveLegacy = { exact, container ->
                    legacyCalls += exact to container
                    if (exact) {
                        Result.failure(IllegalStateException("missing exact 140"))
                    } else {
                        Result.success(legacyContainerMp4)
                    }
                },
            )

            assertEquals(legacyContainerMp4, result.getOrNull())
            assertEquals(
                listOf(
                    true to InnerTubeXPlayer.TargetAudioContainer.MP4,
                    false to InnerTubeXPlayer.TargetAudioContainer.MP4,
                ),
                legacyCalls,
            )
        }

    @Test
    fun `findFormat with requireExactTargetItag returns null instead of silently switching to webm`() {
        val webmFormat = fakeFormat(251, "audio/webm; codecs=\"opus\"", 160_000)
        val mp4LowFormat = fakeFormat(139, "audio/mp4; codecs=\"mp4a.40.5\"", 48_000)
        val mp4HighFormat = fakeFormat(140, "audio/mp4; codecs=\"mp4a.40.2\"", 128_000)

        val responseWithout140 = fakePlayerResponse(listOf(webmFormat, mp4LowFormat))
        val exactMissing = YTPlayerUtils.findFormat(
            playerResponse = responseWithout140,
            audioQuality = AudioQuality.AUTO,
            isMetered = false,
            targetItag = 140,
            requireExactTargetItag = true,
            preferredContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
        )
        assertNull(exactMissing)

        val containerMatched = YTPlayerUtils.findFormat(
            playerResponse = responseWithout140,
            audioQuality = AudioQuality.AUTO,
            isMetered = false,
            targetItag = 140,
            requireExactTargetItag = false,
            preferredContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
        )
        assertEquals(139, containerMatched?.itag)

        val responseWith140 = fakePlayerResponse(listOf(webmFormat, mp4HighFormat))
        val exactFound = YTPlayerUtils.findFormat(
            playerResponse = responseWith140,
            audioQuality = AudioQuality.AUTO,
            isMetered = false,
            targetItag = 140,
            requireExactTargetItag = true,
            preferredContainer = InnerTubeXPlayer.TargetAudioContainer.MP4,
        )
        assertEquals(140, exactFound?.itag)
        assertTrue(InnerTubeXPlayer.matchesTargetContainer(exactFound?.mimeType, InnerTubeXPlayer.TargetAudioContainer.MP4))
    }
}

