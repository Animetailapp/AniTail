package com.anitail.music.utils

import com.anitail.music.constants.AudioQuality
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.PlayerConfig
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.YtConfigParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InnerTubeXPlayerTest {
    @After
    fun tearDown() {
        InnerTubeXPlayer.clearStreamClientFailures()
        InnerTubeXPlayer.clearObservedFormatMimeTypes()
    }

    @Test
    fun `failed stream clients accumulate per video and expire`() {
        InnerTubeXPlayer.markStreamClientFailed("song", "VISIONOS", nowMs = 1_000L)
        InnerTubeXPlayer.markStreamClientFailed("song", "WEB_REMIX", nowMs = 2_000L)

        assertEquals(
            setOf("VISIONOS", "WEB_REMIX"),
            InnerTubeXPlayer.failedStreamClients("song", nowMs = 2_000L),
        )
        assertTrue(InnerTubeXPlayer.failedStreamClients("other", nowMs = 2_000L).isEmpty())
        assertTrue(InnerTubeXPlayer.failedStreamClients("song", nowMs = 302_000L).isEmpty())
    }

    @Test
    fun `exhausted alternatives retry rejected client at low quality`() =
        runBlocking {
            val attempts = mutableListOf<Pair<Set<String>, Boolean>>()
            val result = InnerTubeXPlayer.resolveWithClientFallback(setOf("WEB_REMIX")) { clients, lowQuality ->
                attempts += clients to lowQuality
                if (lowQuality) "recovered" else null
            }

            assertEquals("recovered", result)
            assertEquals(
                listOf(setOf("WEB_REMIX") to false, emptySet<String>() to true),
                attempts,
            )
        }

    @Test
    fun `no playable stream exception reopens rejected client`() =
        runBlocking {
            val attempts = mutableListOf<Pair<Set<String>, Boolean>>()
            val result = InnerTubeXPlayer.resolveWithClientFallback(setOf("WEB_REMIX")) { clients, lowQuality ->
                attempts += clients to lowQuality
                if (!lowQuality) {
                    throw StreamResolveException(StreamResolveException.Reason.NO_PLAYABLE_STREAM, "no playable stream")
                }
                "recovered"
            }

            assertEquals("recovered", result)
            assertEquals(
                listOf(setOf("WEB_REMIX") to false, emptySet<String>() to true),
                attempts,
            )
        }

    @Test
    fun `network and restriction errors do not reopen rejected clients`() =
        runBlocking {
            for (reason in StreamResolveException.Reason.entries.filterNot {
                it == StreamResolveException.Reason.NO_PLAYABLE_STREAM
            }) {
                val expected = StreamResolveException(reason, "resolution failed")
                var attempts = 0
                try {
                    InnerTubeXPlayer.resolveWithClientFallback(setOf("WEB_REMIX")) { _, _ ->
                        attempts++
                        throw expected
                    }
                    fail("Expected $reason to propagate")
                } catch (actual: StreamResolveException) {
                    assertSame(expected, actual)
                }
                assertEquals(1, attempts)
            }
        }

    @Test
    fun `working alternate client is kept without retrying rejected one`() =
        runBlocking {
            var attempts = 0
            val result = InnerTubeXPlayer.resolveWithClientFallback(setOf("WEB_REMIX")) { clients, lowQuality ->
                attempts++
                assertEquals(setOf("WEB_REMIX"), clients)
                assertEquals(false, lowQuality)
                "alternate"
            }

            assertEquals("alternate", result)
            assertEquals(1, attempts)
        }

    @Test
    fun `normal resolution does not downgrade quality or repeat`() =
        runBlocking {
            for (stream in listOf("normal", null)) {
                var attempts = 0
                val result = InnerTubeXPlayer.resolveWithClientFallback(emptySet()) { clients, lowQuality ->
                    attempts++
                    assertTrue(clients.isEmpty())
                    assertEquals(false, lowQuality)
                    stream
                }
                assertEquals(stream, result)
                assertEquals(1, attempts)
            }
        }

    @Test
    fun `no playable stream without rejected clients is not retried`() =
        runBlocking {
            val expected = StreamResolveException(StreamResolveException.Reason.NO_PLAYABLE_STREAM, "no playable stream")
            var attempts = 0
            try {
                InnerTubeXPlayer.resolveWithClientFallback(emptySet()) { _, _ ->
                    attempts++
                    throw expected
                }
                fail("Expected the resolution error to propagate")
            } catch (actual: StreamResolveException) {
                assertSame(expected, actual)
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `failed low quality fallback is not retried again`() =
        runBlocking {
            var attempts = 0
            val result = InnerTubeXPlayer.resolveWithClientFallback<String>(setOf("WEB_REMIX")) { _, _ ->
                attempts++
                null
            }
            assertNull(result)
            assertEquals(2, attempts)

            val expected = StreamResolveException(StreamResolveException.Reason.NO_PLAYABLE_STREAM, "no playable stream")
            attempts = 0
            try {
                InnerTubeXPlayer.resolveWithClientFallback(setOf("WEB_REMIX")) { _, _ ->
                    attempts++
                    throw expected
                }
                fail("Expected the fallback error to propagate")
            } catch (actual: StreamResolveException) {
                assertSame(expected, actual)
            }
            assertEquals(2, attempts)
        }

    @Test
    fun `cancellation propagates from either extraction attempt`() =
        runBlocking {
            for (cancelOnAttempt in 1..2) {
                val expected = CancellationException("cancelled")
                var attempts = 0
                try {
                    InnerTubeXPlayer.resolveWithClientFallback<String>(setOf("WEB_REMIX")) { _, _ ->
                        attempts++
                        if (attempts == cancelOnAttempt) throw expected
                        null
                    }
                    fail("Expected cancellation to propagate")
                } catch (actual: CancellationException) {
                    assertSame(expected, actual)
                }
                assertEquals(cancelOnAttempt, attempts)
            }
        }

    @Test
    fun `failed watch page falls back to anonymous embedded config`() =
        runBlocking {
            val expected = PlayerConfig("embedded", 123, null, null)
            var embeddedUsesLogin = true
            val parser =
                object : YtConfigParser {
                    override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean): PlayerConfig =
                        error("HTTP 302")

                    override suspend fun fetchEmbeddedConfig(videoId: String, useLoginCookies: Boolean): PlayerConfig {
                        embeddedUsesLogin = useLoginCookies
                        return expected
                    }
                }

            assertEquals(expected, InnerTubeXPlayer.run { parser.withEmbeddedConfigFallback() }.fetchConfig("song", true))
            assertEquals(false, embeddedUsesLogin)
        }

    @Test
    fun `target itag container resolution uses observed mime types and known itag fallback`() {
        assertNull(InnerTubeXPlayer.targetAudioContainerForItag(0))
        assertEquals(
            InnerTubeXPlayer.TargetAudioContainer.MP4,
            InnerTubeXPlayer.targetAudioContainerForItag(140),
        )
        assertEquals(
            InnerTubeXPlayer.TargetAudioContainer.WEBM,
            InnerTubeXPlayer.targetAudioContainerForItag(251),
        )

        InnerTubeXPlayer.rememberFormatMimeType(999, "audio/mp4; codecs=\"mp4a.40.2\"")
        InnerTubeXPlayer.rememberFormatMimeType(998, "audio/webm; codecs=\"opus\"")
        assertEquals(
            InnerTubeXPlayer.TargetAudioContainer.MP4,
            InnerTubeXPlayer.targetAudioContainerForItag(999),
        )
        assertEquals(
            InnerTubeXPlayer.TargetAudioContainer.WEBM,
            InnerTubeXPlayer.targetAudioContainerForItag(998),
        )
    }

    @Test
    fun `resolveInnerTubeXAudioQuality maps target itags to matching extraction quality`() {
        assertEquals(
            InnerTubeXAudioQuality.AUTO,
            InnerTubeXPlayer.resolveInnerTubeXAudioQuality(AudioQuality.AUTO, null, 0),
        )
        assertEquals(
            InnerTubeXAudioQuality.MP4,
            InnerTubeXPlayer.resolveInnerTubeXAudioQuality(AudioQuality.AUTO, null, 140),
        )
        assertEquals(
            InnerTubeXAudioQuality.LOW,
            InnerTubeXPlayer.resolveInnerTubeXAudioQuality(AudioQuality.HIGH, null, 139),
        )
        assertEquals(
            InnerTubeXAudioQuality.AUTO,
            InnerTubeXPlayer.resolveInnerTubeXAudioQuality(AudioQuality.HIGH, null, 251),
        )
    }

    @Test
    fun `matchesTargetContainer distinguishes mp4 and webm audio streams`() {
        assertTrue(InnerTubeXPlayer.matchesTargetContainer("audio/webm; codecs=\"opus\"", null))
        assertTrue(
            InnerTubeXPlayer.matchesTargetContainer(
                "audio/mp4; codecs=\"mp4a.40.2\"",
                InnerTubeXPlayer.TargetAudioContainer.MP4,
            ),
        )
        assertFalse(
            InnerTubeXPlayer.matchesTargetContainer(
                "audio/webm; codecs=\"opus\"",
                InnerTubeXPlayer.TargetAudioContainer.MP4,
            ),
        )
        assertTrue(
            InnerTubeXPlayer.matchesTargetContainer(
                "audio/webm; codecs=\"opus\"",
                InnerTubeXPlayer.TargetAudioContainer.WEBM,
            ),
        )
        assertFalse(
            InnerTubeXPlayer.matchesTargetContainer(
                "audio/mp4; codecs=\"mp4a.40.2\"",
                InnerTubeXPlayer.TargetAudioContainer.WEBM,
            ),
        )
    }
}

