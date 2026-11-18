package com.anitail.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.anitail.desktop.YouTube
import com.anitail.desktop.player.ffmpeg.FFmpegFrameGrabber
import com.anitail.desktop.player.ffmpeg.FFmpegLogger
import com.anitail.desktop.player.ffmpeg.Frame
import com.anitail.desktop.player.ffmpeg.HttpMediaStatusException
import com.anitail.desktop.player.ffmpeg.HttpRangeInputStream
import com.anitail.desktop.player.ffmpeg.LoopingVideoInput
import com.anitail.desktop.player.ffmpeg.PlaybackPositionInput
import com.anitail.desktop.player.ffmpeg.SeekableMediaSource
import com.anitail.desktop.player.ffmpeg.createDirectMediaHttpClient
import com.anitail.desktop.player.ffmpeg.isLiveStreamUrl
import com.anitail.desktop.player.ffmpeg.isTrustedDirectMediaEndpoint
import com.anitail.desktop.ui.component.VideoShaderEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT2020_CL
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT2020_NCL
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT470BG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_SMPTE170M
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_SMPTE240M
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV21
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class VideoPixelFormat {
    NV12,
    YUV420P,
    BGRA,
}

enum class VideoColorSpace {
    BT709,
    BT601,
    BT2020,
}

enum class VideoColorRange {
    LIMITED,
    FULL,
}

class VideoFrame(
    val width: Int,
    val height: Int,
    val format: VideoPixelFormat,
    val colorSpace: VideoColorSpace = VideoColorSpace.BT709,
    val colorRange: VideoColorRange = VideoColorRange.LIMITED,
    val planeY: Bitmap? = null,
    val planeU: Bitmap? = null,
    val planeV: Bitmap? = null,
    val planeUV: Bitmap? = null,
    val bgraBitmap: Bitmap? = null,
    val timestampMs: Long = 0L,
    private val imageLock: Any = Any(),
) {
    private var closed = false

    fun drawSafely(block: VideoFrame.() -> Unit): Boolean =
        synchronized(imageLock) {
            if (closed) {
                false
            } else {
                block()
                true
            }
        }

    fun close() {
        synchronized(imageLock) {
            if (closed) return
            closed = true
        }
    }
}

class VideoPlayer(
    private val onFrame: (VideoFrame) -> Unit = {},
    private val onEnded: () -> Unit = {},
    private val onSeekLanded: ((Long) -> Unit)? = null,
    private val onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    private val onRenderedFirstFrame: () -> Unit = {},
    private val looping: Boolean = false,
) {
    var currentFrame by mutableStateOf<VideoFrame?>(null, policy = neverEqualPolicy())
        private set

    var frameSequence by mutableStateOf(0L)
        private set

    var isBuffering by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var aspectRatio by mutableStateOf(16f / 9f)
        private set

    var currentUrl: String? = null
        private set

    var onStreamExpired: ((Int?) -> Unit)? = null
    var audioPtsProvider: (() -> Long?)? = null

    private var grabber: FFmpegFrameGrabber? = null
    private var seekableSource: SeekableMediaSource? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isPaused = false

    @Volatile
    private var pendingSeekMs: Long? = null

    @Volatile
    private var positionProvider: () -> Long = { currentSyncedClockMs() }

    @Volatile
    private var playbackSpeed = 1.0f

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val clockLock = Object()

    @Volatile
    private var pausedPositionMs = 0L

    @Volatile
    private var syncedPositionMs = 0L

    @Volatile
    private var syncedPositionAtNanos = System.nanoTime()

    @Volatile
    private var syncedIsPlaying = true

    @Volatile
    private var lastAutoSeekNanos = 0L

    @Volatile
    private var initialVideoFrameRenderedFlag = false

    @Volatile
    private var loopClock = LoopClockAnchor()

    private val loopingTimeline = LoopingVideoTimeline()
    private val liveTimeline = LiveVideoTimeline()

    private val isLiveStream: Boolean
        get() = currentUrl?.let { isLiveStreamUrl(it) } == true

    private var decodeThread: Thread? = null

    @Volatile
    private var targetWidth = 0

    @Volatile
    private var targetHeight = 0

    @Volatile
    private var nativeVideoWidth = 0

    @Volatile
    private var nativeVideoHeight = 0

    @Volatile
    private var requestedTargetWidth = 0

    @Volatile
    private var requestedTargetHeight = 0

    @Volatile
    private var requestedFillViewport = false

    @Volatile
    private var loadJobId = 0L

    @Volatile
    private var startGeneration = 0L

    @Volatile
    private var lastVideoPtsMs = 0L

    @Volatile
    private var cachedDurationMs = 0L

    @Volatile
    private var lastPrepareFailure: Throwable? = null

    private val lifecycleLock = Any()
    private val prepareLock = Any()
    private var startJob: Job? = null
    private var teardownJob: Job? = null
    private var disposed = false
    private val videoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val frameHolders = Array(8) { FrameBitmapHolder() }
    private var nextFrameHolderIndex = 0
    private var cachedW = 0
    private var cachedH = 0

    fun start(
        url: String,
        headers: Map<String, String> = emptyMap(),
        positionMs: Long = 0L,
        contentLengthBytes: Long? = null,
        positionProvider: (() -> Long)? = null,
    ) {
        val generation: Long
        val previousStart: Job?
        synchronized(lifecycleLock) {
            if (disposed) return
            previousStart = startJob
            generation = ++startGeneration
            synchronized(clockLock) {
                syncedPositionMs = positionMs.coerceAtLeast(0L)
                syncedPositionAtNanos = System.nanoTime()
                pausedPositionMs = syncedPositionMs
                syncedIsPlaying = true
                lastAutoSeekNanos = 0L
                initialVideoFrameRenderedFlag = false
                clockLock.notifyAll()
            }
        }
        previousStart?.cancel()
        Snapshot.withMutableSnapshot {
            currentFrame = null
            frameSequence++
            isBuffering = true
            errorMessage = null
        }

        val launched =
            videoScope.launch {
                previousStart?.join()
                if (!isActive || startGeneration != generation) return@launch
                val prepared =
                    load(
                        url = url,
                        headers = headers,
                        contentLengthBytes = contentLengthBytes,
                        positionProvider = positionProvider ?: { currentSyncedClockMs() },
                    )
                if (!isActive || startGeneration != generation) {
                    if (prepared) closeAsync()
                    return@launch
                }
                if (prepared) {
                    val startPaused = synchronized(clockLock) { !syncedIsPlaying }
                    play(startPaused = startPaused)
                } else {
                    val message = lastPrepareFailure?.message ?: "No se pudo abrir el stream de video"
                    Snapshot.withMutableSnapshot {
                        isBuffering = false
                        errorMessage = message
                    }
                }
            }
        synchronized(lifecycleLock) {
            if (startGeneration == generation) {
                startJob = launched
            } else {
                launched.cancel()
            }
        }
    }

    fun syncPosition(
        positionMs: Long,
        isPlaying: Boolean,
    ) {
        val clamped = positionMs.coerceAtLeast(0L)
        synchronized(clockLock) {
            syncedIsPlaying = isPlaying
            syncedPositionMs = clamped
            syncedPositionAtNanos = System.nanoTime()
            pausedPositionMs = clamped
            clockLock.notifyAll()
        }
        if (decodeThread?.isAlive == true) {
            if (isPlaying && isPaused) {
                resume()
            } else if (!isPlaying && !isPaused) {
                pause()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val seekTargetMs = positionMs.coerceAtLeast(0L)
        seekableSource?.updatePlaybackPosition(seekTargetMs)
        synchronized(clockLock) {
            syncedPositionMs = seekTargetMs
            syncedPositionAtNanos = System.nanoTime()
            pausedPositionMs = seekTargetMs
            pendingSeekMs = seekTargetMs
            lastAutoSeekNanos = System.nanoTime()
            clockLock.notifyAll()
        }
    }

    fun stop() {
        val pendingStart: Job?
        synchronized(lifecycleLock) {
            startGeneration++
            pendingStart = startJob
            startJob = null
        }
        pendingStart?.cancel()
        Snapshot.withMutableSnapshot {
            currentFrame = null
            frameSequence++
            isBuffering = false
            errorMessage = null
        }
        closeAsync()
    }

    private fun currentSyncedClockMs(): Long =
        synchronized(clockLock) {
            if (!syncedIsPlaying) {
                syncedPositionMs
            } else {
                val delta = scaledClockElapsedMs(System.nanoTime() - syncedPositionAtNanos, playbackSpeed).coerceIn(0L, 250L)
                syncedPositionMs + delta
            }
        }

    fun getBufferedPositionMs(overrideDurationMs: Long = 0L): Long {
        val dur = overrideDurationMs.takeIf { it > 0L } ?: cachedDurationMs
        val source = seekableSource
        if (source != null) {
            val totalSize = source.mediaSize ?: 0L
            if (totalSize > 0L && dur > 0L) {
                val bufferedBytes = source.bufferedBytePosition
                val fraction = (bufferedBytes.toDouble() / totalSize.toDouble()).coerceIn(0.0, 1.0)
                return (fraction * dur).toLong().coerceIn(0L, dur)
            }
        }
        val videoPts = lastVideoPtsMs
        return if (dur > 0L) videoPts.coerceIn(0L, dur) else videoPts
    }

    private fun normalizeTimelinePts(rawPtsMs: Long): Long {
        if (looping) return loopingTimeline.normalize(rawPtsMs)
        if (!isLiveStream) return rawPtsMs
        val audioPts = audioPtsProvider?.invoke()
        if (audioPts != null && audioPts > 0L) {
            return liveTimeline.normalizeWithAudioPts(rawPtsMs, audioPts, audioClock())
        }
        if (liveTimeline.isTracking || abs(rawPtsMs - audioClock()) > 5000L) {
            return liveTimeline.normalize(rawPtsMs, audioClock())
        }
        return rawPtsMs
    }

    private fun resetLoopClock(positionMs: Long) {
        loopClock = LoopClockAnchor(positionMs.coerceAtLeast(0L), System.nanoTime())
    }

    private fun resetMasterClock(initialMs: Long = 0L) {
        synchronized(clockLock) {
            pausedPositionMs = initialMs
        }
    }

    internal fun load(
        url: String,
        headers: Map<String, String> = emptyMap(),
        contentLengthBytes: Long? = null,
        positionProvider: () -> Long = { currentSyncedClockMs() },
    ): Boolean {
        if (url.startsWith("sabr-video://", ignoreCase = true)) {
            return false
        }
        val useRangeStream = shouldUseRangeStreamForVideoUrl(url)
        var rangeInput: HttpRangeInputStream? = null
        val grabberInput: SeekableMediaSource? =
            if (useRangeStream) {
                val clen =
                    contentLengthBytes?.takeIf { it > 0L }
                        ?: Regex("[?&]clen=([0-9]+)")
                            .find(url)
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull()
                HttpRangeInputStream(
                    httpClient = createDirectMediaHttpClient(YouTube.proxy),
                    url = url,
                    requestHeaders = headers,
                    mediaSize = clen ?: 0L,
                    chunkSize = if (url.contains("pot=")) 4 * 1024 * 1024 else 262_144,
                    requestTimeoutMs = 35_000L,
                    prefetchAhead = true,
                    targetRequestDurationMs = 4_000L,
                    onStreamExpired = { status ->
                        synchronized(lifecycleLock) {
                            if (seekableSource === rangeInput) onStreamExpired?.invoke(status)
                        }
                    },
                ).also { rangeInput = it }
            } else if (looping && url.toHttpUrlOrNull()?.encodedPath?.let {
                    it.endsWith(".mp4", ignoreCase = true) || it.endsWith(".webm", ignoreCase = true) ||
                        it.endsWith(".m3u8", ignoreCase = true)
                } == true
            ) {
                LoopingVideoInput(url, headers)
            } else {
                null
            }

        val prepared =
            prepare(positionProvider, grabberInput, url) {
                val useInput = grabberInput != null && (grabberInput !is LoopingVideoInput || grabberInput.prepare())
                if (useInput) {
                    FFmpegFrameGrabber(grabberInput as InputStream).apply {
                        options["probesize"] = "500000"
                        options["analyzeduration"] = "500000"
                        videoOptions["threads"] = OPTIMAL_VIDEO_THREADS
                        videoOptions["flags"] = "low_delay"
                        imageScalingFlags = 1
                    }
                } else {
                    createDirectUrlGrabber(url, headers)
                }
            }

        if (!prepared && useRangeStream && (lastPrepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode !in setOf(401, 403, 404, 410)) {
            return prepare(positionProvider, null, url) {
                createDirectUrlGrabber(url, headers)
            }
        }
        return prepared
    }

    private fun createDirectUrlGrabber(
        url: String,
        headers: Map<String, String>,
    ): FFmpegFrameGrabber =
        FFmpegFrameGrabber(url).apply {
            videoOptions["threads"] = OPTIMAL_VIDEO_THREADS
            videoOptions["flags"] = "low_delay"
            imageScalingFlags = 1
            if (url.startsWith("http://") || url.startsWith("https://")) {
                options["reconnect"] = "1"
                options["reconnect_streamed"] = "1"
                options["reconnect_delay_max"] = "5"
                options["timeout"] = "15000000"
                options["rw_timeout"] = "15000000"
                options["user_agent"] =
                    headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                headers
                    .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.entries
                    ?.joinToString(separator = "\r\n", postfix = "\r\n") { "${it.key}: ${it.value}" }
                    ?.let { options["headers"] = it }

                options["probesize"] = "500000"
                options["analyzeduration"] = "500000"
            }
        }

    private fun prepare(
        positionProvider: () -> Long,
        source: SeekableMediaSource?,
        mediaUrl: String?,
        createGrabber: () -> FFmpegFrameGrabber,
    ): Boolean =
        synchronized(prepareLock) {
            val retirement: Job
            val currentLoadJobId =
                synchronized(lifecycleLock) {
                    if (disposed) {
                        runCatching { source?.close() }
                        return false
                    }
                    retirement = closeAsync()
                    loadJobId = nextLoadJobId(loadJobId)
                    seekableSource = source
                    loadJobId
                }
            runBlocking { retirement.join() }
            synchronized(lifecycleLock) {
                if (loadJobId != currentLoadJobId) return false
                this.positionProvider = positionProvider
                lastPrepareFailure = null
                resetMasterClock(0L)
                resetLoopClock(0L)
                loopingTimeline.reset()
                liveTimeline.reset()
                lastVideoPtsMs = 0L
                initialVideoFrameRenderedFlag = false
            }
            var preparedGrabber: FFmpegFrameGrabber? = null
            return try {
                val g = createGrabber().also { preparedGrabber = it }
                if (loadJobId != currentLoadJobId) return false
                g.apply {
                    pixelFormat = AV_PIX_FMT_BGRA
                    audioStream = -2
                    start()
                }
                if (loadJobId != currentLoadJobId) {
                    return false
                }
                if (!g.hasVideo()) {
                    FFmpegLogger.w("VideoPlayer", "prepare failed: stream has no video")
                    lastPrepareFailure = IllegalStateException("Stream contains no video")
                    return false
                }
                val nativeWidth = g.imageWidth
                val nativeHeight = g.imageHeight
                val computedAspectRatio =
                    if (nativeWidth > 0 && nativeHeight > 0) {
                        nativeWidth.toFloat() / nativeHeight.toFloat()
                    } else {
                        16f / 9f
                    }
                val accepted =
                    synchronized(lifecycleLock) {
                        if (loadJobId != currentLoadJobId) {
                            false
                        } else {
                            targetWidth = nativeWidth
                            targetHeight = nativeHeight
                            nativeVideoWidth = nativeWidth
                            nativeVideoHeight = nativeHeight
                            cachedDurationMs = g.getLengthInTime() / 1000L
                            grabber = g
                            currentUrl = mediaUrl
                            true
                        }
                    }
                if (!accepted) return false
                preparedGrabber = null
                Snapshot.withMutableSnapshot {
                    aspectRatio = computedAspectRatio
                }
                synchronized(lifecycleLock) {
                    if (loadJobId != currentLoadJobId || grabber !== g) return false
                    onVideoSizeChanged(nativeWidth, nativeHeight)
                }
                FFmpegLogger.i(
                    "VideoPlayer",
                    "prepare success: codec=${g.videoCodecName.orEmpty()} res=${nativeWidth}x$nativeHeight fps=${g.frameRate}",
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loadJobId == currentLoadJobId) {
                    lastPrepareFailure = e
                    FFmpegLogger.w("VideoPlayer", "prepare failed", e)
                }
                synchronized(lifecycleLock) {
                    if (loadJobId == currentLoadJobId) closeAsync()
                }
                false
            } finally {
                runCatching { preparedGrabber?.release() }
            }
        }

    fun play(startPaused: Boolean = false): Unit =
        synchronized(lifecycleLock) {
            if (decodeThread?.isAlive == true) return
            val g = grabber ?: return
            if (looping) {
                resetLoopClock(0L)
                loopingTimeline.reset()
                resetMasterClock(0L)
            } else {
                liveTimeline.reset()
            }
            val playbackLoadJobId = loadJobId
            val playbackSource = seekableSource
            val currentPositionProvider = positionProvider
            isRunning = true
            isPaused = startPaused
            decodeThread =
                Thread {
                    var initialVideoFrameRendered = false
                    try {
                        val startMs = currentPositionProvider()
                        FFmpegLogger.i("VideoPlayer", "decodeThread started: isPaused=$isPaused startMs=${startMs}ms")
                        if (isRunning) {
                            playbackSource?.updatePlaybackPosition(startMs)
                            if (startMs > INITIAL_SEEK_MIN_MS) {
                                try {
                                    val target = (startMs - INITIAL_SEEK_LEAD_MS).coerceAtLeast(0L)
                                    g.setTimestamp(target * 1000, false)
                                    val landedMs = (g.timestamp / 1000).takeIf { it > 0 } ?: target
                                    synchronized(lifecycleLock) {
                                        if (loadJobId == playbackLoadJobId) onSeekLanded?.invoke(landedMs)
                                    }
                                } catch (e: Exception) {
                                    FFmpegLogger.w("VideoPlayer", "Initial seek failed", e)
                                }
                            }
                            if (looping) resetLoopClock(startMs)
                            resetMasterClock(startMs)
                        }
                        while (isRunning) {
                            val seekTarget =
                                synchronized(clockLock) {
                                    while (isRunning && isPaused && initialVideoFrameRendered && pendingSeekMs == null) {
                                        clockLock.wait()
                                    }
                                    pendingSeekMs.also { pendingSeekMs = null }
                                }
                            if (!isRunning) break
                            if (seekTarget != null) {
                                playbackSource?.updatePlaybackPosition(seekTarget)
                                var landedMs = seekTarget
                                try {
                                    g.setTimestamp(seekTarget * 1000, false)
                                    landedMs = (g.timestamp / 1000).takeIf { it > 0 } ?: seekTarget
                                } catch (e: Exception) {
                                    FFmpegLogger.w("VideoPlayer", "Seek failed", e)
                                }
                                synchronized(lifecycleLock) {
                                    if (loadJobId == playbackLoadJobId) onSeekLanded?.invoke(landedMs)
                                }
                                if (looping) resetLoopClock(landedMs)
                                liveTimeline.reset()
                                resetMasterClock(landedMs)
                                initialVideoFrameRendered = false
                                initialVideoFrameRenderedFlag = false
                            }
                            var frame = g.grabFrame(false, true, false, false, false)
                            if (frame == null) {
                                if (looping && isRunning) {
                                    try {
                                        playbackSource?.updatePlaybackPosition(0L)
                                        frame = restartLoop(g)
                                        resetLoopClock(0L)
                                        loopingTimeline.reset()
                                        resetMasterClock(0L)
                                        initialVideoFrameRendered = false
                                        initialVideoFrameRenderedFlag = false
                                    } catch (e: Exception) {
                                        FFmpegLogger.w("VideoPlayer", "Loop restart failed", e)
                                    }
                                }
                                if (frame == null && isRunning && !isPaused) {
                                    val startWaitMs = System.currentTimeMillis()
                                    while (frame == null &&
                                        isRunning &&
                                        !isPaused &&
                                        (System.currentTimeMillis() - startWaitMs) < MAX_WAIT_MS
                                    ) {
                                        val streamFailure = (playbackSource as? PlaybackPositionInput)?.streamFailure
                                        if (streamFailure != null) break
                                        try {
                                            Thread.sleep(25)
                                        } catch (_: InterruptedException) {
                                            Thread.currentThread().interrupt()
                                            break
                                        }
                                        frame = g.grabFrame(false, true, false, false, false)
                                    }
                                }
                                if (frame == null) {
                                    break
                                }
                            }
                            playbackSource?.updatePlaybackPosition(currentPositionProvider())
                            when {
                                frame.image != null || frame.type == Frame.Type.VIDEO -> {
                                    val rawPtsMs = frame.timestamp / 1000
                                    val framePtsMs = normalizeTimelinePts(rawPtsMs)
                                    if (!initialVideoFrameRendered) {
                                        val clock = audioClock()
                                        if (clock > 300L && framePtsMs < (clock - 80L) && (clock - framePtsMs) <= 2_500L) {
                                            continue
                                        }
                                    }
                                    val force = isPaused || !initialVideoFrameRendered
                                    val rendered = renderVideo(frame, forceRender = force, isInitialFrame = !initialVideoFrameRendered)
                                    if (rendered) {
                                        if (!initialVideoFrameRendered) {
                                            initialVideoFrameRendered = true
                                            initialVideoFrameRenderedFlag = true
                                            Snapshot.withMutableSnapshot {
                                                isBuffering = false
                                            }
                                            synchronized(lifecycleLock) {
                                                if (loadJobId == playbackLoadJobId) onRenderedFirstFrame()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: InterruptedException) {
                        FFmpegLogger.d("VideoPlayer", "Decode loop interrupted")
                    } catch (e: Exception) {
                        FFmpegLogger.e("VideoPlayer", "Decode loop failed", e)
                        Snapshot.withMutableSnapshot {
                            isBuffering = false
                            errorMessage = e.message ?: "Error al decodificar video"
                        }
                    } finally {
                        Snapshot.withMutableSnapshot {
                            isBuffering = false
                        }
                        synchronized(lifecycleLock) {
                            if (loadJobId == playbackLoadJobId) {
                                isRunning = false
                                onEnded()
                            }
                        }
                    }
                }.apply {
                    priority = Thread.NORM_PRIORITY + 1
                    name = "VideoDecode-Thread"
                    isDaemon = true
                    start()
                }
        }

    fun pause() {
        synchronized(clockLock) {
            val currentPosition = audioClock()
            isPaused = true
            pausedPositionMs = currentPosition
            clockLock.notifyAll()
        }
    }

    fun resume() {
        if (decodeThread?.isAlive != true) {
            play(startPaused = false)
        } else {
            synchronized(clockLock) {
                val currentPosition = if (looping) pausedPositionMs else positionProvider()
                if (looping) resetLoopClock(currentPosition)
                pausedPositionMs = currentPosition
                isPaused = false
                clockLock.notifyAll()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        synchronized(clockLock) {
            val currentPosition = audioClock()
            if (looping) resetLoopClock(currentPosition)
            pausedPositionMs = currentPosition
            playbackSpeed = speed.coerceIn(0.5f, 2.0f)
            clockLock.notifyAll()
        }
    }

    fun setTargetSize(
        width: Int,
        height: Int,
        fillViewport: Boolean = false,
    ) {
        val viewportWidth = width.coerceAtLeast(0)
        val viewportHeight = height.coerceAtLeast(0)
        synchronized(lifecycleLock) {
            requestedTargetWidth = viewportWidth
            requestedTargetHeight = viewportHeight
            requestedFillViewport = fillViewport
        }
    }

    fun close() {
        stop()
    }

    fun dispose() {
        disposeAsync()
    }

    internal fun disposeAsync(): Job {
        val pendingStart: Job?
        val retirement: Job
        synchronized(lifecycleLock) {
            disposed = true
            startGeneration++
            pendingStart = startJob
            startJob = null
            retirement = closeAsync()
        }
        pendingStart?.cancel()
        Snapshot.withMutableSnapshot {
            currentFrame = null
            frameSequence++
            isBuffering = false
        }
        return cleanupScope.launch {
            retirement.join()
            synchronized(prepareLock) { }
            videoScope.cancel()
        }
    }

    internal fun closeAsync(): Job =
        synchronized(lifecycleLock) {
            loadJobId = nextLoadJobId(loadJobId)
            currentUrl = null
            isRunning = false
            isPaused = false
            nativeVideoWidth = 0
            nativeVideoHeight = 0
            initialVideoFrameRenderedFlag = false
            val activeSource = seekableSource
            seekableSource = null
            val activeGrabber = grabber
            grabber = null
            val activeDecodeThread = decodeThread
            decodeThread = null
            pendingSeekMs = null
            liveTimeline.reset()
            activeDecodeThread?.interrupt()
            val previousTeardown = teardownJob
            cleanupScope
                .launch {
                    previousTeardown?.join()
                    runCatching { activeSource?.close() }
                    var interrupted = false
                    while (activeDecodeThread?.isAlive == true) {
                        try {
                            activeDecodeThread.join()
                        } catch (_: InterruptedException) {
                            interrupted = true
                        }
                    }
                    // A decoder or an in-progress draw may still own these pixels until the join completes.
                    runCatching { activeGrabber?.release() }
                    frameHolders.forEach { it.reset() }
                    nextFrameHolderIndex = 0
                    cachedW = 0
                    cachedH = 0
                    if (interrupted) Thread.currentThread().interrupt()
                }.also { teardownJob = it }
        }

    private fun renderVideo(
        frame: Frame,
        forceRender: Boolean = false,
        isInitialFrame: Boolean = false,
    ): Boolean {
        val rawFrameMs = frame.timestamp / 1000
        val frameMs = normalizeTimelinePts(rawFrameMs)
        val clock = audioClock()
        if (!forceRender) {
            val diff = clock - frameMs
            if (diff > DROP_THRESHOLD_MS && !isInitialFrame) {
                return false
            }
        }

        val g = grabber ?: return false
        val (currentTargetW, currentTargetH) =
            synchronized(lifecycleLock) {
                val srcW = nativeVideoWidth.takeIf { it > 0 } ?: frame.imageWidth.takeIf { it > 0 } ?: g.imageWidth
                val srcH = nativeVideoHeight.takeIf { it > 0 } ?: frame.imageHeight.takeIf { it > 0 } ?: g.imageHeight
                if (VideoShaderEffects.isYuvShaderAvailable && srcW > 0 && srcH > 0) {
                    srcW to srcH
                } else {
                    videoOutputSize(
                        srcW,
                        srcH,
                        requestedTargetWidth,
                        requestedTargetHeight,
                        requestedFillViewport,
                    )
                }
            }
        if (currentTargetW <= 0 || currentTargetH <= 0) return false
        cachedW = currentTargetW
        cachedH = currentTargetH
        targetWidth = currentTargetW
        targetHeight = currentTargetH

        val holder = frameHolders[nextFrameHolderIndex]
        holder.prepareForReuse()
        val planarInfo =
            if (VideoShaderEffects.isYuvShaderAvailable) {
                holder.allocate(cachedW, cachedH)
                if (holder.yAddr == 0L) return false
                g.processPlanarImageToAddresses(
                    yAddress = holder.yAddr,
                    yStride = holder.yStride,
                    uAddress = holder.uAddr,
                    uStride = holder.uStride,
                    vAddress = holder.vAddr,
                    vStride = holder.vStride,
                    destinationWidth = cachedW,
                    destinationHeight = cachedH,
                )
            } else {
                null
            }

        val videoFrame: VideoFrame?
        if (planarInfo != null) {
            val colorSpace =
                when (planarInfo.colorSpace) {
                    AVCOL_SPC_BT709 -> VideoColorSpace.BT709
                    AVCOL_SPC_BT470BG, AVCOL_SPC_SMPTE170M, AVCOL_SPC_SMPTE240M -> VideoColorSpace.BT601
                    AVCOL_SPC_BT2020_NCL, AVCOL_SPC_BT2020_CL -> VideoColorSpace.BT2020
                    else -> if (nativeVideoHeight >= 720) VideoColorSpace.BT709 else VideoColorSpace.BT601
                }
            val colorRange = if (planarInfo.colorRange == AVCOL_RANGE_JPEG) VideoColorRange.FULL else VideoColorRange.LIMITED

            when (planarInfo.pixelFormat) {
                AV_PIX_FMT_YUV420P, AV_PIX_FMT_YUVJ420P, AV_PIX_FMT_NV12, AV_PIX_FMT_NV21 -> {
                    holder.planeY.notifyPixelsChanged()
                    holder.planeU.notifyPixelsChanged()
                    holder.planeV.notifyPixelsChanged()
                    videoFrame =
                        VideoFrame(
                            width = currentTargetW,
                            height = currentTargetH,
                            format = VideoPixelFormat.YUV420P,
                            colorSpace = colorSpace,
                            colorRange = colorRange,
                            planeY = holder.planeY,
                            planeU = holder.planeU,
                            planeV = holder.planeV,
                            timestampMs = frameMs,
                            imageLock = holder.imageLock,
                        )
                }

                else -> {
                    videoFrame = null
                }
            }
        } else {
            holder.ensureBgra(currentTargetW, currentTargetH)
            val success =
                g.processImageToAddress(
                    dstAddress = holder.bgraAddr,
                    dstStride = holder.bgraStride,
                    destinationWidth = currentTargetW,
                    destinationHeight = currentTargetH,
                )
            if (success) {
                holder.bgraBitmap.notifyPixelsChanged()
                videoFrame =
                    VideoFrame(
                        width = currentTargetW,
                        height = currentTargetH,
                        format = VideoPixelFormat.BGRA,
                        bgraBitmap = holder.bgraBitmap,
                        timestampMs = frameMs,
                        imageLock = holder.imageLock,
                    )
            } else {
                videoFrame = null
            }
        }

        if (videoFrame == null) {
            return false
        }

        if (!forceRender) {
            val currentClock = audioClock()
            if (currentClock < frameMs) {
                if (!waitForAudioClock(frameMs)) {
                    videoFrame.close()
                    return false
                }
            }
        }

        holder.currentFrame = videoFrame
        nextFrameHolderIndex = (nextFrameHolderIndex + 1) % frameHolders.size
        synchronized(lifecycleLock) {
            if (!isRunning || grabber !== g) {
                videoFrame.close()
                return false
            }
            Snapshot.withMutableSnapshot {
                currentFrame = videoFrame
                frameSequence++
                onFrame(videoFrame)
            }
        }
        lastVideoPtsMs = frameMs
        return true
    }

    private fun audioClock(): Long =
        synchronized(clockLock) {
            if (isPaused) {
                return@synchronized pausedPositionMs
            }
            if (looping) {
                val anchor = loopClock
                advancedClockPositionMs(anchor.positionMs, System.nanoTime() - anchor.startedNanos, playbackSpeed)
            } else {
                positionProvider()
            }
        }

    private fun waitForAudioClock(frameMs: Long): Boolean =
        synchronized(clockLock) {
            var previousClock: Long? = null
            while (isRunning && !isPaused) {
                if (pendingSeekMs != null || Thread.currentThread().isInterrupted) return@synchronized false
                val clock = audioClock()
                val remainingMs = frameMs - clock
                if (remainingMs <= 0L) return@synchronized true
                val limitNanos = if (previousClock == clock) 10_000_000L else 100_000_000L
                previousClock = clock
                TimeUnit.NANOSECONDS.timedWait(clockLock, frameDelayNanos(remainingMs, playbackSpeed).coerceAtMost(limitNanos))
            }
            isRunning
        }

    private class FrameBitmapHolder {
        val imageLock = Any()
        var currentFrame: VideoFrame? = null
        val planeY = Bitmap()
        val planeU = Bitmap()
        val planeV = Bitmap()
        val bgraBitmap = Bitmap()

        var yAddr: Long = 0L
        var uAddr: Long = 0L
        var vAddr: Long = 0L
        var bgraAddr: Long = 0L

        var yStride: Int = 0
        var uStride: Int = 0
        var vStride: Int = 0
        var bgraStride: Int = 0

        fun prepareForReuse() {
            synchronized(imageLock) {
                currentFrame?.close()
                currentFrame = null
            }
        }

        fun allocate(
            width: Int,
            height: Int,
        ) {
            val uvW = (width + 1) / 2
            val uvH = (height + 1) / 2

            if (planeY.width != width || planeY.height != height) {
                bgraBitmap.reset()
                bgraAddr = 0L
                bgraStride = 0
                planeY.allocVideoPixels(ImageInfo(width, height, ColorType.ALPHA_8, ColorAlphaType.PREMUL))
                yAddr = planeY.peekPixels()?.use { it.addr } ?: 0L
                yStride = planeY.rowBytes
            }
            if (planeU.width != uvW || planeU.height != uvH) {
                planeU.allocVideoPixels(ImageInfo(uvW, uvH, ColorType.ALPHA_8, ColorAlphaType.PREMUL))
                uAddr = planeU.peekPixels()?.use { it.addr } ?: 0L
                uStride = planeU.rowBytes
            }
            if (planeV.width != uvW || planeV.height != uvH) {
                planeV.allocVideoPixels(ImageInfo(uvW, uvH, ColorType.ALPHA_8, ColorAlphaType.PREMUL))
                vAddr = planeV.peekPixels()?.use { it.addr } ?: 0L
                vStride = planeV.rowBytes
            }
        }

        fun ensureBgra(
            width: Int,
            height: Int,
        ) {
            if (bgraBitmap.width != width || bgraBitmap.height != height) {
                bgraBitmap.allocVideoPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB))
                bgraAddr = bgraBitmap.peekPixels()?.use { it.addr } ?: 0L
                bgraStride = bgraBitmap.rowBytes
            }
        }

        fun reset() {
            synchronized(imageLock) {
                currentFrame?.close()
                currentFrame = null
                planeY.reset()
                planeU.reset()
                planeV.reset()
                bgraBitmap.reset()
                yAddr = 0L
                uAddr = 0L
                vAddr = 0L
                bgraAddr = 0L
                yStride = 0
                uStride = 0
                vStride = 0
                bgraStride = 0
            }
        }
    }

    private companion object {
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val OPTIMAL_VIDEO_THREADS = minOf(8, maxOf(4, Runtime.getRuntime().availableProcessors())).toString()
        const val DROP_THRESHOLD_MS = 200L
        const val MAX_WAIT_MS = 20_000L
        const val INITIAL_SEEK_MIN_MS = 3_500L
        const val INITIAL_SEEK_LEAD_MS = 300L
    }
}

internal fun Bitmap.allocVideoPixels(info: ImageInfo): Boolean =
    // FFmpeg's SIMD scaler needs padded rows, including chroma planes smaller than a vector.
    allocPixels(info, (info.minRowBytes + 31) / 32 * 32)

private data class LoopClockAnchor(
    val positionMs: Long = 0L,
    val startedNanos: Long = 0L,
)

internal fun scaledClockElapsedMs(
    elapsedNanos: Long,
    playbackSpeed: Float,
): Long = (elapsedNanos / 1_000_000.0 * playbackSpeed.coerceAtLeast(0f)).toLong()

internal fun frameDelayNanos(
    mediaDeltaMs: Long,
    playbackSpeed: Float,
): Long = (mediaDeltaMs * 1_000_000.0 / playbackSpeed.coerceAtLeast(0.01f)).toLong()

internal fun advancedClockPositionMs(
    anchorPositionMs: Long,
    elapsedNanos: Long,
    playbackSpeed: Float,
): Long = anchorPositionMs + scaledClockElapsedMs(elapsedNanos, playbackSpeed)

internal fun shouldUseRangeStreamForVideoUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let(::isTrustedDirectMediaEndpoint) == true

internal fun restartLoop(grabber: FFmpegFrameGrabber): Frame? {
    try {
        grabber.setTimestamp(0L, true)
        grabber.grabFrame(false, true, false, false, false)?.let { return it }
    } catch (_: Exception) {
    }
    if (grabber.mediaInputStream != null) return null
    grabber.restart()
    return grabber.grabFrame(false, true, false, false, false)
}

internal fun nextLoadJobId(current: Long): Long = if (current == Long.MAX_VALUE) 1L else current + 1L

internal class LoopingVideoTimeline {
    private var firstFrameTimestampMs: Long? = null

    fun normalize(frameTimestampMs: Long): Long {
        val firstTimestamp = firstFrameTimestampMs ?: frameTimestampMs.also { firstFrameTimestampMs = it }
        return (frameTimestampMs - firstTimestamp).coerceAtLeast(0L)
    }

    fun reset() {
        firstFrameTimestampMs = null
    }
}

internal class LiveVideoTimeline {
    private var ptsOffset: Long? = null

    val isTracking: Boolean
        get() = ptsOffset != null

    fun normalizeWithAudioPts(
        videoPtsMs: Long,
        audioPtsMs: Long,
        audioClockMs: Long,
    ): Long {
        val delta = videoPtsMs - audioPtsMs
        val normalized = audioClockMs + delta
        ptsOffset = videoPtsMs - normalized
        return normalized.coerceAtLeast(0L)
    }

    fun normalize(
        rawPtsMs: Long,
        audioClockMs: Long,
    ): Long {
        val currentOffset = ptsOffset
        val offset =
            if (currentOffset == null || abs(audioClockMs - (rawPtsMs - currentOffset)) > 5000L) {
                (rawPtsMs - audioClockMs).also { ptsOffset = it }
            } else {
                currentOffset
            }
        return (rawPtsMs - offset).coerceAtLeast(0L)
    }

    fun reset() {
        ptsOffset = null
    }
}

internal fun videoOutputSize(
    nativeWidth: Int,
    nativeHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fillViewport: Boolean,
): Pair<Int, Int> {
    if (nativeWidth <= 0 || nativeHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return nativeWidth to nativeHeight
    }
    val widthScale = viewportWidth.toDouble() / nativeWidth
    val heightScale = viewportHeight.toDouble() / nativeHeight
    val scale = (if (fillViewport) maxOf(widthScale, heightScale) else minOf(widthScale, heightScale)).coerceAtMost(1.0)
    if (scale == 1.0) return nativeWidth to nativeHeight

    fun scaledEven(size: Int): Int = ((size * scale).toInt() / 2 * 2).coerceAtLeast(minOf(2, size))
    return scaledEven(nativeWidth) to scaledEven(nativeHeight)
}

