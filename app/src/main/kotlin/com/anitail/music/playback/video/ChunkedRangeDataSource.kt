@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anitail.music.playback.video

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException

/**
 * Splits unbounded HTTP reads into bounded byte-range requests so direct googlevideo.com streams
 * avoid server throttling and recover from oversized-range 403 rejections by halving chunk size.
 */
@UnstableApi
class ChunkedRangeDataSource(
    private val upstream: DataSource,
    initialChunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
    private val expectedContentLength: Long? = null,
) : BaseDataSource(true) {
    private var baseSpec: DataSpec? = null
    private var opened = false
    private var upstreamOpened = false
    private var currentPosition = 0L
    private var totalRemaining = C.LENGTH_UNSET.toLong()
    private var chunkRemaining = 0L
    private var currentChunkRequestedLength = 0L
    private var currentChunkBytesRead = 0L
    private var currentChunkSize = initialChunkSizeBytes.coerceAtLeast(MIN_CHUNK_SIZE_BYTES)
    private var latestUri: Uri? = null

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES,
        private val expectedContentLength: Long? = null,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedRangeDataSource(
                upstream = upstreamFactory.createDataSource(),
                initialChunkSizeBytes = chunkSizeBytes,
                expectedContentLength = expectedContentLength,
            )
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        baseSpec = dataSpec
        latestUri = dataSpec.uri
        currentPosition = dataSpec.position
        chunkRemaining = 0L
        currentChunkRequestedLength = 0L
        currentChunkBytesRead = 0L

        val requestedLength = dataSpec.length
        if (requestedLength == 0L) {
            totalRemaining = 0L
            opened = true
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return 0L
        }

        val initialTarget =
            if (requestedLength == C.LENGTH_UNSET.toLong()) {
                currentChunkSize
            } else {
                minOf(currentChunkSize, requestedLength)
            }

        transferInitializing(dataSpec)
        val firstChunkLength =
            try {
                openNextChunk(initialTarget)
            } catch (error: Throwable) {
                closeUpstream()
                throw error
            }

        val knownTotalLength = parseContentRangeTotalLength(upstream.responseHeaders) ?: expectedContentLength
        totalRemaining =
            when {
                requestedLength != C.LENGTH_UNSET.toLong() ->
                    knownTotalLength?.let { total ->
                        minOf(requestedLength, (total - currentPosition).coerceAtLeast(0L))
                    } ?: requestedLength

                knownTotalLength != null -> (knownTotalLength - currentPosition).coerceAtLeast(0L)

                firstChunkLength != C.LENGTH_UNSET.toLong() && firstChunkLength < currentChunkRequestedLength ->
                    firstChunkLength

                else -> C.LENGTH_UNSET.toLong()
            }

        if (totalRemaining != C.LENGTH_UNSET.toLong() &&
            chunkRemaining != C.LENGTH_UNSET.toLong() &&
            chunkRemaining > totalRemaining
        ) {
            chunkRemaining = totalRemaining
        }

        opened = true
        transferStarted(dataSpec)
        return totalRemaining
    }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (totalRemaining == 0L) return C.RESULT_END_OF_INPUT

        while (true) {
            if (chunkRemaining == 0L) {
                if (totalRemaining == 0L) return C.RESULT_END_OF_INPUT
                val nextTarget =
                    if (totalRemaining == C.LENGTH_UNSET.toLong()) {
                        currentChunkSize
                    } else {
                        minOf(currentChunkSize, totalRemaining)
                    }
                val openedChunk =
                    try {
                        openNextChunk(nextTarget)
                    } catch (invalidCode: HttpDataSource.InvalidResponseCodeException) {
                        if (invalidCode.responseCode == 416 && totalRemaining == C.LENGTH_UNSET.toLong()) {
                            closeUpstream()
                            totalRemaining = 0L
                            return C.RESULT_END_OF_INPUT
                        }
                        throw invalidCode
                    }
                if (openedChunk == 0L) {
                    closeUpstream()
                    return C.RESULT_END_OF_INPUT
                }
                if (totalRemaining == C.LENGTH_UNSET.toLong()) {
                    parseContentRangeTotalLength(upstream.responseHeaders)?.let { knownTotal ->
                        totalRemaining = (knownTotal - currentPosition).coerceAtLeast(0L)
                    }
                    if (totalRemaining == C.LENGTH_UNSET.toLong() &&
                        openedChunk != C.LENGTH_UNSET.toLong() &&
                        openedChunk < currentChunkRequestedLength
                    ) {
                        totalRemaining = openedChunk
                    }
                }
            }

            val toRead =
                if (chunkRemaining == C.LENGTH_UNSET.toLong()) {
                    length
                } else {
                    minOf(length.toLong(), chunkRemaining).toInt()
                }
            val read = upstream.read(buffer, offset, toRead)
            if (read == C.RESULT_END_OF_INPUT) {
                closeUpstream()
                val reachedUnknownTotalEof =
                    totalRemaining == C.LENGTH_UNSET.toLong() &&
                        currentChunkBytesRead < currentChunkRequestedLength
                val reachedEndOfStream =
                    currentChunkBytesRead == 0L ||
                        totalRemaining == 0L ||
                        reachedUnknownTotalEof
                chunkRemaining = 0L
                if (reachedEndOfStream) {
                    totalRemaining = 0L
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            currentPosition += read
            currentChunkBytesRead += read
            if (chunkRemaining != C.LENGTH_UNSET.toLong()) {
                chunkRemaining = (chunkRemaining - read).coerceAtLeast(0L)
            }
            if (totalRemaining != C.LENGTH_UNSET.toLong()) {
                totalRemaining = (totalRemaining - read).coerceAtLeast(0L)
            }
            bytesTransferred(read)
            return read
        }
    }

    override fun close() {
        latestUri = null
        baseSpec = null
        closeUpstream()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: latestUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    private fun openNextChunk(targetLength: Long): Long {
        closeUpstream()
        val spec = checkNotNull(baseSpec) { "ChunkedRangeDataSource opened without DataSpec" }
        var chunkLength = minOf(targetLength, currentChunkSize).coerceAtLeast(1L)
        var retriedAfter403 = false

        while (true) {
            val chunkSpec =
                spec.buildUpon()
                    .setPosition(currentPosition)
                    .setLength(chunkLength)
                    .build()
            try {
                val openedLength = upstream.open(chunkSpec)
                upstreamOpened = true
                latestUri = upstream.uri ?: spec.uri
                currentChunkRequestedLength = chunkLength
                currentChunkBytesRead = 0L
                chunkRemaining = openedLength
                return openedLength
            } catch (invalidCode: HttpDataSource.InvalidResponseCodeException) {
                closeUpstream()
                if (!retriedAfter403 &&
                    invalidCode.responseCode == 403 &&
                    currentChunkSize > MIN_CHUNK_SIZE_BYTES
                ) {
                    retriedAfter403 = true
                    currentChunkSize = (currentChunkSize / 2L).coerceAtLeast(MIN_CHUNK_SIZE_BYTES)
                    chunkLength = minOf(chunkLength, currentChunkSize).coerceAtLeast(1L)
                    continue
                }
                throw invalidCode
            }
        }
    }

    private fun closeUpstream() {
        if (upstreamOpened) {
            upstreamOpened = false
            try {
                upstream.close()
            } catch (_: Throwable) {}
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 2 * 1024 * 1024L
        const val MIN_CHUNK_SIZE_BYTES = 256 * 1024L
    }
}

internal fun parseContentRangeTotalLength(headers: Map<String, List<String>>): Long? {
    val value =
        headers.entries
            .firstOrNull { (key, _) -> key.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return null
    val totalPart = value.substringAfter('/', missingDelimiterValue = "").trim()
    if (totalPart.isEmpty() || totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it >= 0L }
}

