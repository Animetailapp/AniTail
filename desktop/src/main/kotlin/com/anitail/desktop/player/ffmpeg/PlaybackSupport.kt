package com.anitail.desktop.player.ffmpeg

import com.anitail.desktop.YouTube
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

internal object FFmpegLogger {
    private val sensitiveQueryParamRegex =
        Regex("([?&](?:token|pot|key|sig|lsig|s|sp|auth|authorization|cookie)=)([^&\\s]+)", RegexOption.IGNORE_CASE)
    private val sensitiveHeaderRegex =
        Regex("((?:Authorization|Cookie|X-Goog-Visitor-Id)\\s*:\\s*)([^\\r\\n]+)", RegexOption.IGNORE_CASE)

    fun redactDiagnosticText(text: String): String =
        text
            .replace(sensitiveQueryParamRegex, "$1<redacted>")
            .replace(sensitiveHeaderRegex, "$1<redacted>")

    fun d(tag: String, message: String) {
        println("[$tag] ${redactDiagnosticText(message)}")
    }

    fun i(tag: String, message: String) {
        println("[$tag] ${redactDiagnosticText(message)}")
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        val suffix = error?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
        System.err.println("[$tag][WARN] ${redactDiagnosticText(message + suffix)}")
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val suffix = error?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
        System.err.println("[$tag][ERROR] ${redactDiagnosticText(message + suffix)}")
    }
}

internal interface SeekableMediaSource {
    val mediaSize: Long?
    val mediaPosition: Long
    val bufferedBytePosition: Long get() = mediaPosition

    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun readAtMediaPosition(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun seekToMediaPosition(position: Long): Long

    fun updatePlaybackPosition(positionMs: Long)

    fun close()
}

internal interface SeekableMediaInput : SeekableMediaSource

internal interface PlaybackPositionInput {
    val streamFailure: Throwable?

    fun updatePlaybackPosition(positionMs: Long)

    fun beginPlaybackSeek(positionMs: Long) = updatePlaybackPosition(positionMs)

    fun endPlaybackSeek(positionMs: Long) = updatePlaybackPosition(positionMs)
}

internal interface PlaybackHttpStatusCarrier {
    val playbackHttpStatusCode: Int
}

internal class InstantSilenceDetector(
    private val sampleRate: Int,
) {
    private var silentFrames = 0L
    val isSilent: Boolean get() = silentFrames >= sampleRate * 2L

    fun frame(peak: Int) {
        silentFrames = if (peak < 256) silentFrames + 1 else 0
    }

    fun reset() {
        silentFrames = 0
    }
}

internal fun instantSilenceSeekTarget(
    positionMs: Long,
    durationMs: Long,
): Long? {
    if (durationMs <= 15_000L) return null
    return minOf(positionMs + 15_000L, durationMs - 500L).takeIf { it > positionMs }
}

internal fun normalizationBoostPcm16(
    sample: Short,
    gain: Float,
): Short {
    if (gain <= 1f) return sample
    val boosted = sample / 32768.0 * gain
    val magnitude = abs(boosted)
    val limited = if (magnitude <= 0.9) boosted else sign(boosted) * (0.9 + 0.1 * tanh((magnitude - 0.9) / 0.1))
    return (limited * 32768.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

internal fun isLiveStreamUrl(url: String): Boolean =
    url.contains("yt_live_broadcast", ignoreCase = true) ||
        url.contains("/live/1", ignoreCase = true) ||
        url.contains("live=1", ignoreCase = true) ||
        url.contains("/hls_playlist/", ignoreCase = true) ||
        url.contains("/hls_variant/", ignoreCase = true)

internal val sharedMediaOkHttpClient: OkHttpClient by lazy {
    OkHttpClient
        .Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()
}

internal fun createDirectMediaHttpClient(proxy: Proxy? = YouTube.proxy): OkHttpClient =
    sharedMediaOkHttpClient
        .newBuilder()
        .apply {
            if (proxy != null) {
                proxy(proxy)
            }
        }
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(DirectMediaRedirectInterceptor)
        .retryOnConnectionFailure(true)
        .build()

internal enum class FilterType { PK, LSC, HSC, LPQ, HPQ }

internal data class ParametricEqBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val filterType: FilterType = FilterType.PK,
    val enabled: Boolean = true,
)

internal fun ParametricEqBand.isIdentity(): Boolean =
    !enabled || (gain == 0.0 && (filterType == FilterType.PK || filterType == FilterType.LSC || filterType == FilterType.HSC))

internal data class EqualizerProfile(
    val id: String,
    val name: String,
    val bands: List<ParametricEqBand>,
    val preamp: Double = 0.0,
)

internal data class EqualizerConfiguration(
    val profile: EqualizerProfile?,
    val preampDb: Double,
)

internal const val MIN_AUDIO_PREAMP_DB = -12.0
internal const val MAX_AUDIO_BOOST_DB = 12.0

/** RBJ biquad chain; use one instance per audio pipeline because filter state is not thread-safe. */
internal class ParametricEqualizer(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private var profile: EqualizerProfile? = null
    private var coefficients = emptyArray<DoubleArray>()
    private var appliedBands = emptyList<ParametricEqBand>()

    init {
        require(sampleRate > 0 && channelCount > 0)
    }

    private var activeFilters = IntArray(0)
    private var state = emptyArray<DoubleArray>()
    private var preampGain = 1.0
    private var peakEnvelope = 0.0
    private var peakHoldFrames = 0
    private val peakHoldDuration = (sampleRate * PEAK_HOLD_SECONDS).toInt()
    private val releaseDecay = kotlin.math.exp(-1.0 / (sampleRate * LIMITER_RELEASE_SECONDS))
    var gainReductionDb = 0.0
        private set
    private var processingBuffer = DoubleArray((sampleRate / 10 * channelCount).coerceAtLeast(1))

    fun update(profile: EqualizerProfile?): Boolean {
        if (profile !== this.profile) {
            this.profile = profile
            val bands =
                profile?.bands.orEmpty().filter {
                    it.enabled && it.frequency > 0.0 && it.frequency < sampleRate / 2.0 &&
                        it.gain.isFinite() && it.q.isFinite() && it.q > 0.0
                }
            val previousBands = appliedBands
            val previousCoefficients = coefficients
            val sameTopology =
                bands.size == appliedBands.size &&
                    bands.indices.all { index ->
                        val before = appliedBands[index]
                        val after = bands[index]
                        before.filterType == after.filterType
                    }
            val active = bands.indices.filter { !bands[it].isIdentity() }.toIntArray()
            if (!sameTopology) {
                state = Array(bands.size) { DoubleArray(channelCount * 4) }
            } else {
                for (index in active) if (index !in activeFilters) state[index].fill(0.0)
            }
            activeFilters = active
            coefficients =
                bands
                    .mapIndexed { index, band ->
                        if (previousBands.getOrNull(index) == band) previousCoefficients[index] else coefficientsFor(band)
                    }.toTypedArray()
            appliedBands = bands
            preampGain = 10.0.pow((profile?.preamp ?: 0.0) / 20.0)
        }
        return profile != null
    }

    fun hasActiveProcessing(outputGainDb: Double = 0.0): Boolean =
        activeFilters.isNotEmpty() ||
            preampGain != 1.0 ||
            outputGainDb.coerceIn(MIN_AUDIO_PREAMP_DB, MAX_AUDIO_BOOST_DB) != 0.0

    fun processPcm16(
        samples: ShortArray,
        count: Int = samples.size,
        outputGainDb: Double = 0.0,
    ) {
        val outputGain = 10.0.pow(outputGainDb.coerceIn(MIN_AUDIO_PREAMP_DB, MAX_AUDIO_BOOST_DB) / 20.0)
        if (activeFilters.isEmpty() && preampGain == 1.0 && outputGain == 1.0) {
            gainReductionDb = 0.0
            return
        }
        if (processingBuffer.size < count) processingBuffer = DoubleArray(count)
        for (i in 0 until count) processingBuffer[i] = samples[i] / 32768.0
        filterBuffer(count)
        limitFrames(count, outputGain)
        for (i in 0 until count) {
            val scaled = (processingBuffer[i] * 32768.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = scaled.toShort()
        }
    }

    private fun filterBuffer(count: Int) {
        for (index in activeFilters) {
            val c = coefficients[index]
            val b0 = c[0]
            val b1 = c[1]
            val b2 = c[2]
            val a1 = c[3]
            val a2 = c[4]
            val s = state[index]
            for (channel in 0 until channelCount) {
                val o = channel * 4
                var x1 = s[o]
                var x2 = s[o + 1]
                var y1 = s[o + 2]
                var y2 = s[o + 3]
                var i = channel
                while (i < count) {
                    val x = processingBuffer[i]
                    val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                    x2 = x1
                    x1 = x
                    y2 = y1
                    y1 = y
                    processingBuffer[i] = y
                    i += channelCount
                }
                s[o] = x1
                s[o + 1] = x2
                s[o + 2] = y1
                s[o + 3] = y2
            }
        }
        if (preampGain != 1.0) for (i in 0 until count) processingBuffer[i] *= preampGain
    }

    private fun limitFrames(
        count: Int,
        outputGain: Double,
    ) {
        var frameStart = 0
        var lastGain = outputGain
        while (frameStart < count) {
            val frameEnd = minOf(frameStart + channelCount, count)
            var peak = 0.0
            for (i in frameStart until frameEnd) peak = maxOf(peak, abs(processingBuffer[i]))
            val gain = limiterGain(peak, outputGain)
            lastGain = gain
            for (i in frameStart until frameEnd) processingBuffer[i] *= gain
            frameStart = frameEnd
        }
        gainReductionDb = -20.0 * log10(lastGain / outputGain)
    }

    private fun limiterGain(
        peak: Double,
        outputGain: Double,
    ): Double {
        if (peak >= peakEnvelope) {
            peakEnvelope = peak
            peakHoldFrames = peakHoldDuration
        } else if (peakHoldFrames > 0) {
            peakHoldFrames--
        } else {
            peakEnvelope = peak + (peakEnvelope - peak) * releaseDecay
        }
        return if (peakEnvelope > 0.0) minOf(outputGain, LIMITER_CEILING / peakEnvelope) else outputGain
    }

    fun reset() {
        state.forEach { it.fill(0.0) }
        peakEnvelope = 0.0
        peakHoldFrames = 0
        gainReductionDb = 0.0
    }

    private fun coefficientsFor(band: ParametricEqBand): DoubleArray {
        val a = 10.0.pow(band.gain / 40.0)
        val w = 2.0 * PI * band.frequency / sampleRate
        val cosW = cos(w)
        val alpha = sin(w) / (2.0 * band.q)
        val shelf = 2.0 * sqrt(a) * alpha
        val (b, aCoeffs) =
            when (band.filterType) {
                FilterType.PK -> {
                    doubleArrayOf(1 + alpha * a, -2 * cosW, 1 - alpha * a) to
                        doubleArrayOf(1 + alpha / a, -2 * cosW, 1 - alpha / a)
                }

                FilterType.LSC -> {
                    doubleArrayOf(
                        a * ((a + 1) - (a - 1) * cosW + shelf),
                        2 * a * ((a - 1) - (a + 1) * cosW),
                        a * ((a + 1) - (a - 1) * cosW - shelf),
                    ) to doubleArrayOf((a + 1) + (a - 1) * cosW + shelf, -2 * ((a - 1) + (a + 1) * cosW), (a + 1) + (a - 1) * cosW - shelf)
                }

                FilterType.HSC -> {
                    doubleArrayOf(
                        a * ((a + 1) + (a - 1) * cosW + shelf),
                        -2 * a * ((a - 1) + (a + 1) * cosW),
                        a * ((a + 1) + (a - 1) * cosW - shelf),
                    ) to doubleArrayOf((a + 1) - (a - 1) * cosW + shelf, 2 * ((a - 1) - (a + 1) * cosW), (a + 1) - (a - 1) * cosW - shelf)
                }

                FilterType.LPQ -> {
                    doubleArrayOf((1 - cosW) / 2, 1 - cosW, (1 - cosW) / 2) to doubleArrayOf(1 + alpha, -2 * cosW, 1 - alpha)
                }

                FilterType.HPQ -> {
                    doubleArrayOf((1 + cosW) / 2, -(1 + cosW), (1 + cosW) / 2) to doubleArrayOf(1 + alpha, -2 * cosW, 1 - alpha)
                }
            }
        val a0 = aCoeffs[0]
        return doubleArrayOf(b[0] / a0, b[1] / a0, b[2] / a0, aCoeffs[1] / a0, aCoeffs[2] / a0)
    }

    private companion object {
        const val LIMITER_CEILING = 0.98
        const val PEAK_HOLD_SECONDS = 0.05
        const val LIMITER_RELEASE_SECONDS = 0.15
    }
}

