package com.anitail.music.playback.video

import com.metrolist.innertubex.sabr.SabrChunk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SabrInputStreamTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `read does not time out while active sabr producer is pacing ahead of playback`() {
        val allowSecondChunk = CompletableDeferred<Unit>()
        val stream =
            SabrInputStream(
                mediaSize = 2_000L,
                streamFactory = { _, _ ->
                    flow {
                        emit(SabrChunk(ByteArray(10) { 1 }, startRange = 0L, isInitialization = true, sequenceNumber = null, startMs = null, durationMs = null))
                        emit(SabrChunk(ByteArray(10) { 2 }, startRange = 10L, isInitialization = false, sequenceNumber = 0, startMs = 0L, durationMs = 15_000L))
                        allowSecondChunk.await()
                        emit(SabrChunk(ByteArray(10) { 3 }, startRange = 20L, isInitialization = false, sequenceNumber = 1, startMs = 15_000L, durationMs = 5_000L))
                        awaitCancellation()
                    }
                },
                startupTimeoutMs = 500L,
                readTimeoutMs = 120L,
                tempDirectory = tempFolder.newFolder(),
            )
        try {
            val buf = ByteArray(20)
            var total = 0
            while (total < 20) {
                val read = stream.readAtMediaPosition(total.toLong(), buf, total, 20 - total)
                assertTrue(read > 0)
                total += read
            }

            // Spawn delayed unblocking beyond readTimeoutMs (120ms); playback stays at 0ms so buffered end (15s) > 5s headroom.
            val releaser =
                Thread {
                    Thread.sleep(300L)
                    allowSecondChunk.complete(Unit)
                }.apply { start() }

            val nextBuf = ByteArray(10)
            val nextRead = stream.readAtMediaPosition(20L, nextBuf, 0, 10)
            releaser.join(2_000L)
            assertEquals(10, nextRead)
            assertArrayEquals(ByteArray(10) { 3 }, nextBuf)

            // Once playback catches up within 5s of buffered frontier (20_000ms), true stall must time out.
            stream.updatePlaybackPosition(18_000L)
            try {
                stream.readAtMediaPosition(30L, ByteArray(10), 0, 10)
                fail("Expected SocketTimeoutException once playback caught up to buffered frontier")
            } catch (error: IOException) {
                assertTrue(error.cause is SocketTimeoutException)
            }
        } finally {
            stream.close()
        }
    }

    @Test
    fun `suspendProducer prevents timeout and resumeProducer restarts near cached frontier`() {
        val startedAtMs = Collections.synchronizedList(mutableListOf<Long>())
        val stream =
            SabrInputStream(
                mediaSize = 2_000L,
                streamFactory = { initialPlayerTimeMs, _ ->
                    startedAtMs += initialPlayerTimeMs
                    flow {
                        if (initialPlayerTimeMs == 0L) {
                            emit(SabrChunk(ByteArray(10) { 1 }, startRange = 0L, isInitialization = true, sequenceNumber = null, startMs = null, durationMs = null))
                            emit(SabrChunk(ByteArray(10) { 2 }, startRange = 10L, isInitialization = false, sequenceNumber = 0, startMs = 0L, durationMs = 20_000L))
                            awaitCancellation()
                        } else {
                            emit(SabrChunk(ByteArray(10) { 4 }, startRange = 20L, isInitialization = false, sequenceNumber = 1, startMs = 20_000L, durationMs = 10_000L))
                            awaitCancellation()
                        }
                    }
                },
                startupTimeoutMs = 500L,
                readTimeoutMs = 120L,
                tempDirectory = tempFolder.newFolder(),
            )
        try {
            val headBuf = ByteArray(20)
            var headRead = 0
            while (headRead < 20) {
                val read = stream.readAtMediaPosition(headRead.toLong(), headBuf, headRead, 20 - headRead)
                assertTrue(read > 0)
                headRead += read
            }

            stream.suspendProducer()

            val readerDone = CountDownLatch(1)
            val readerError = AtomicReference<Throwable?>(null)
            val tailBuf = ByteArray(10)
            val tailBytesRead = AtomicReference(0)
            val reader =
                Thread {
                    try {
                        tailBytesRead.set(stream.readAtMediaPosition(20L, tailBuf, 0, 10))
                    } catch (t: Throwable) {
                        readerError.set(t)
                    } finally {
                        readerDone.countDown()
                    }
                }.apply { start() }

            // Wait well past readTimeoutMs (120ms) while suspended; read must stay blocked rather than timing out.
            assertFalse(readerDone.await(300L, TimeUnit.MILLISECONDS))

            stream.resumeProducer(5_000L)
            assertTrue(readerDone.await(2_000L, TimeUnit.MILLISECONDS))
            reader.join(1_000L)

            assertEquals(null, readerError.get())
            assertEquals(10, tailBytesRead.get())
            assertArrayEquals(ByteArray(10) { 4 }, tailBuf)
            assertEquals(listOf(0L, 19_000L), startedAtMs.toList())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `seek backsteps initial player time when first media chunk overshoots requested byte offset`() {
        val startedAtMs = Collections.synchronizedList(mutableListOf<Long>())
        val stream =
            SabrInputStream(
                mediaSize = 5_000L,
                streamFactory = { initialPlayerTimeMs, _ ->
                    startedAtMs += initialPlayerTimeMs
                    flow {
                        emit(SabrChunk(ByteArray(100) { 1 }, startRange = 0L, isInitialization = true, sequenceNumber = null, startMs = null, durationMs = null))
                        when {
                            initialPlayerTimeMs == 0L -> awaitCancellation()
                            initialPlayerTimeMs > 30_000L -> {
                                // Overshoots requested byte 1_500L.
                                emit(SabrChunk(ByteArray(500) { 9 }, startRange = 2_000L, isInitialization = false, sequenceNumber = 10, startMs = 45_000L, durationMs = 5_000L))
                                awaitCancellation()
                            }
                            else -> {
                                emit(SabrChunk(ByteArray(200) { 0x42 }, startRange = 1_400L, isInitialization = false, sequenceNumber = 5, startMs = 25_000L, durationMs = 5_000L))
                                awaitCancellation()
                            }
                        }
                    }
                },
                startupTimeoutMs = 1_000L,
                readTimeoutMs = 1_000L,
                seekPrerollMs = 15_000L,
                tempDirectory = tempFolder.newFolder(),
            )
        try {
            assertEquals(100, stream.readAtMediaPosition(0L, ByteArray(100), 0, 100))

            stream.beginPlaybackSeek(60_000L)
            stream.endPlaybackSeek(60_000L)
            stream.seekToMediaPosition(1_500L)

            val targetBuf = ByteArray(100)
            val count = stream.readAtMediaPosition(1_500L, targetBuf, 0, 100)
            assertEquals(100, count)
            assertArrayEquals(ByteArray(100) { 0x42 }, targetBuf)
            assertEquals(listOf(0L, 45_000L, 30_000L), startedAtMs.toList())
        } finally {
            stream.close()
        }
    }
}

