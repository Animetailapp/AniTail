package com.anitail.desktop.player

import com.anitail.desktop.YouTube
import com.anitail.desktop.player.ffmpeg.AudioPlayer
import com.anitail.desktop.player.ffmpeg.AudioRenderer
import com.anitail.desktop.player.ffmpeg.EqualizerConfiguration
import com.anitail.desktop.player.ffmpeg.EqualizerProfile
import com.anitail.desktop.player.ffmpeg.FFmpegFrameGrabber
import com.anitail.desktop.player.ffmpeg.FilterType
import com.anitail.desktop.player.ffmpeg.HttpMediaStatusException
import com.anitail.desktop.player.ffmpeg.HttpRangeInputStream
import com.anitail.desktop.player.ffmpeg.ParametricEqBand
import com.anitail.desktop.player.ffmpeg.createDirectMediaHttpClient
import com.anitail.desktop.player.ffmpeg.isAudioOutputUnavailable
import com.anitail.desktop.player.ffmpeg.isLiveStreamUrl
import com.anitail.desktop.player.ffmpeg.isTrustedDirectMediaEndpoint
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.innertube.NewPipeExtractor
import com.anitail.innertube.YouTube as InnertubeYouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.response.PlayerResponse
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

data class VideoStreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val contentLengthBytes: Long? = null,
    val audioUrl: String? = null,
    val audioContentLengthBytes: Long? = null,
    val targetVideoId: String = "",
)

/**
 * Reproductor de audio nativo para Desktop.
 * Usa FFmpeg (Bytedeco 8.1.2) como motor principal autointegrado y mantiene VLCJ como fallback automático.
 */
class NativeAudioPlayer {

    private enum class Engine {
        NONE,
        FFMPEG,
        VLC,
    }

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val preferences = DesktopPreferences.getInstance()
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val musicVideoCache = ConcurrentHashMap<String, String>()

    private var ffmpegAvailable: Boolean = false

    @Volatile
    private var activeEngine: Engine = Engine.NONE

    @Volatile
    private var activeFfmpegPlayer: AudioPlayer? = null

    @Volatile
    private var outgoingFfmpegPlayer: AudioPlayer? = null

    private var fadeInJob: Job? = null
    private var fadeOutJob: Job? = null

    private var vlcInitAttempted: Boolean = false
    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var baseVolume: Double = 1.0

    @Volatile
    private var normalizationFactor: Double = 1.0

    @Volatile
    private var fadeGain: Double = 1.0

    @Volatile
    private var currentTempo: Float = 1.0f

    @Volatile
    private var currentPitchSemitone: Int = 0

    @Volatile
    private var skipSilenceEnabled: Boolean = false

    @Volatile
    private var crossfadeDurationSec: Int = 0

    private var playbackRate: Float = 1.0f

    @Volatile
    private var currentStatus: PlaybackStatus = PlaybackStatus.IDLE

    private var _audioEqualizer: AudioEqualizer? = AudioEqualizer(null)
    val audioEqualizer: AudioEqualizer? get() = _audioEqualizer

    @Volatile
    private var currentEqConfig: EqualizerConfiguration = EqualizerConfiguration(null, 0.0)

    private var vlcFallbackEq: uk.co.caprica.vlcj.player.base.Equalizer? = null

    // Callbacks de estado
    var onStatusChanged: ((PlaybackStatus) -> Unit)? = null
    var onTimeChanged: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null

    init {
        ffmpegAvailable = runCatching {
            FFmpegFrameGrabber.tryLoad()
            true
        }.onFailure { error ->
            System.err.println("NativeAudioPlayer: No se pudo inicializar FFmpeg nativo, se usará fallback VLCJ si está disponible: ${error.message}")
        }.getOrDefault(false)

        if (ffmpegAvailable) {
            println("NativeAudioPlayer: Motor FFmpeg nativo inicializado.")
        } else {
            ensureVlcInitialized()
        }
    }

    @Synchronized
    private fun ensureVlcInitialized(): Boolean {
        if (mediaPlayer != null) return true
        if (vlcInitAttempted) return false
        vlcInitAttempted = true

        registerWindowsVlcPath()
        return try {
            if (NativeDiscovery().discover()) {
                println("NativeAudioPlayer: VLC nativo encontrado e inicializado (fallback).")
                factory = MediaPlayerFactory()
                mediaPlayer = factory?.mediaPlayers()?.newMediaPlayer()
                vlcFallbackEq = runCatching { factory?.equalizer()?.newEqualizer() }.getOrNull()
                setupVlcEvents()
                syncVlcFallbackEqualizer()
                mediaPlayer != null
            } else {
                println("NativeAudioPlayer: VLC nativo no encontrado en el sistema.")
                false
            }
        } catch (e: Exception) {
            println("NativeAudioPlayer: Error al inicializar VLC Factory: ${e.message}")
            false
        }
    }

    private fun registerWindowsVlcPath() {
        if (!Platform.isWindows()) return
        try {
            val pathsToTry = listOf(
                Pair(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\VideoLAN\\VLC"),
                Pair(WinReg.HKEY_CURRENT_USER, "SOFTWARE\\VideoLAN\\VLC"),
                Pair(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\WOW6432Node\\VideoLAN\\VLC"),
            )
            var vlcPath: String? = null
            for ((hkey, subkey) in pathsToTry) {
                try {
                    if (Advapi32Util.registryKeyExists(hkey, subkey)) {
                        val installDir = Advapi32Util.registryGetStringValue(hkey, subkey, "InstallDir")
                        if (!installDir.isNullOrBlank() && File(installDir, "libvlc.dll").exists()) {
                            vlcPath = installDir
                            break
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (vlcPath != null) {
                val currentJnaPath = System.getProperty("jna.library.path") ?: ""
                if (!currentJnaPath.contains(vlcPath)) {
                    val newPath = if (currentJnaPath.isEmpty()) vlcPath else "$vlcPath;$currentJnaPath"
                    System.setProperty("jna.library.path", newPath)
                    println("NativeAudioPlayer: Registrado VLC desde el registro: $vlcPath")
                }
            }
        } catch (t: Throwable) {
            println("NativeAudioPlayer: Error al buscar VLC en el registro de Windows: ${t.message}")
        }
    }

    fun applyEqualizer() {
        currentEqConfig = buildEqualizerConfiguration()
        syncVlcFallbackEqualizer()
    }

    private fun buildEqualizerConfiguration(): EqualizerConfiguration {
        val eq = _audioEqualizer ?: return EqualizerConfiguration(null, 0.0)
        if (!eq.isEnabled) return EqualizerConfiguration(null, 0.0)
        val bands = AudioEqualizer.BAND_FREQUENCIES.mapIndexed { index, freq ->
            ParametricEqBand(
                frequency = freq.toDouble(),
                gain = eq.getBandGain(index).toDouble(),
                q = 1.41,
                filterType = FilterType.PK,
                enabled = true,
            )
        }
        val profile = EqualizerProfile(
            id = "desktop_eq",
            name = eq.currentPreset,
            bands = bands,
            preamp = eq.preamp.toDouble(),
        )
        return EqualizerConfiguration(profile = profile, preampDb = 0.0)
    }

    private fun syncVlcFallbackEqualizer() {
        val eq = _audioEqualizer ?: return
        val vlcEq = vlcFallbackEq
        if (vlcEq != null) {
            vlcEq.setPreamp(eq.preamp)
            for (index in AudioEqualizer.BAND_FREQUENCIES.indices) {
                vlcEq.setAmp(index, eq.getBandGain(index))
            }
            mediaPlayer?.audio()?.setEqualizer(if (eq.isEnabled) vlcEq else null)
        } else {
            mediaPlayer?.audio()?.setEqualizer(eq.getEqualizerForPlayer())
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        skipSilenceEnabled = enabled
    }

    fun setTempoAndPitch(tempo: Float, pitchSemitone: Int) {
        currentTempo = tempo.coerceIn(0.5f, 2.0f)
        currentPitchSemitone = pitchSemitone.coerceIn(-12, 12)
        val pitchFactor = pitchFactorFor(currentPitchSemitone)
        activeFfmpegPlayer?.let { player ->
            player.setPlaybackSpeed(currentTempo)
            player.setPlaybackPitch(pitchFactor)
        }
        playbackRate = PlaybackRateCalculator.toRate(currentTempo, currentPitchSemitone).toFloat()
        if (activeEngine == Engine.VLC) {
            mediaPlayer?.controls()?.setRate(playbackRate)
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSec = seconds.coerceIn(0, 12)
    }

    private fun pitchFactorFor(pitchSemitone: Int): Float =
        2.0.pow(pitchSemitone.toDouble() / 12.0).toFloat().coerceIn(0.5f, 2.0f)

    private fun setupVlcEvents() {
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(player: MediaPlayer?) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    currentStatus = PlaybackStatus.PLAYING
                    onStatusChanged?.invoke(PlaybackStatus.PLAYING)
                    onBuffering?.invoke(false)
                }
            }

            override fun paused(player: MediaPlayer?) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    currentStatus = PlaybackStatus.PAUSED
                    onStatusChanged?.invoke(PlaybackStatus.PAUSED)
                }
            }

            override fun stopped(player: MediaPlayer?) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    currentStatus = PlaybackStatus.STOPPED
                    onStatusChanged?.invoke(PlaybackStatus.STOPPED)
                }
            }

            override fun finished(player: MediaPlayer?) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    currentStatus = PlaybackStatus.ENDED
                    onStatusChanged?.invoke(PlaybackStatus.ENDED)
                }
            }

            override fun bufferingChanged(player: MediaPlayer?, newCache: Float) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    onBuffering?.invoke(newCache < 100.0f)
                }
            }

            override fun error(player: MediaPlayer?) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    val msg = "Error interno de VLC"
                    println("NativeAudioPlayer: VLC ERROR: $msg")
                    currentStatus = PlaybackStatus.ERROR
                    onError?.invoke(msg)
                    onStatusChanged?.invoke(PlaybackStatus.ERROR)
                }
            }

            override fun positionChanged(player: MediaPlayer?, newTime: Long, newPosition: Double) {
                if (activeEngine != Engine.VLC) return
                runOnEdt {
                    onTimeChanged?.invoke(newTime, player?.status()?.length() ?: 0L)
                }
            }
        })
    }

    /**
     * Reproduce un video de YouTube por su ID.
     * Obtiene la URL de streaming de audio y la reproduce con FFmpeg (o fallback VLCJ).
     */
    suspend fun play(videoId: String): Result<Unit> =
        playInternal(videoId, initialPositionMs = 0L, startPlaying = true, allowCrossfade = true)

    suspend fun playAtPosition(
        videoId: String,
        positionMs: Long,
        playWhenReady: Boolean,
    ): Result<Unit> =
        playInternal(
            videoId = videoId,
            initialPositionMs = positionMs.coerceAtLeast(0L),
            startPlaying = playWhenReady,
            allowCrossfade = false,
        )

    private suspend fun playInternal(
        videoId: String,
        initialPositionMs: Long,
        startPlaying: Boolean,
        allowCrossfade: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!ffmpegAvailable && !ensureVlcInitialized()) {
            return@withContext Result.failure(IllegalStateException("No hay motor de audio disponible (FFmpeg/VLC)."))
        }

        println("NativeAudioPlayer: Intentando reproducir videoId: $videoId (ffmpeg=$ffmpegAvailable)")

        val audioQuality = preferences.audioQuality.value
        val normalizationEnabled = preferences.normalizeAudio.value

        // Intento primario con InnerTubeX
        val innertubexResult = DesktopInnerTubeXPlayer.extractStream(videoId, audioQuality)
        if (innertubexResult.isSuccess) {
            val stream = innertubexResult.getOrThrow()
            val loudnessDb = stream.loudnessDb
            val normalization = if (normalizationEnabled && loudnessDb != null) {
                min(10.0.pow(-loudnessDb / 20.0), 1.0)
            } else {
                1.0
            }
            setNormalizationFactor(normalization)

            val userAgent = stream.headers["User-Agent"]
            val referer = stream.headers["Referer"]
            println("NativeAudioPlayer: Iniciando reproducción vía InnerTubeX (${stream.clientName}, itag=${stream.itag})")

            if (
                tryStartPlayback(
                    url = stream.audioUrl,
                    headers = stream.headers,
                    contentLengthBytes = stream.contentLengthBytes,
                    userAgent = userAgent,
                    referer = referer,
                    allowProxyForVlc = false,
                    initialPositionMs = initialPositionMs,
                    startPlaying = startPlaying,
                    allowCrossfade = allowCrossfade,
                )
            ) {
                println("NativeAudioPlayer: ¡Reproducción InnerTubeX iniciada con éxito! (${stream.clientName})")
                return@withContext Result.success(Unit)
            } else {
                println("NativeAudioPlayer: Falló al cargar stream InnerTubeX (${stream.clientName}); recurriendo a fallback...")
                DesktopInnerTubeXPlayer.markStreamClientFailed(videoId, stream.clientName)
            }
        } else {
            println("NativeAudioPlayer: InnerTubeX no pudo resolver stream para $videoId: ${innertubexResult.exceptionOrNull()?.message}; recurriendo a fallback...")
        }

        val signatureTimestamp = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
        val isLoggedIn = YouTube.cookie != null
        var lastError: String? = null
        val clientsToTry = StreamClientOrder.build()
        val mainResponse = YouTube.player(videoId, null, YouTubeClient.WEB_REMIX, signatureTimestamp).getOrNull()
        val loudnessDb = mainResponse?.playerConfig?.audioConfig?.loudnessDb
        val normalization = if (normalizationEnabled && loudnessDb != null) {
            min(10.0.pow(-loudnessDb / 20.0), 1.0)
        } else {
            1.0
        }
        setNormalizationFactor(normalization)

        val candidates = mutableListOf<ResolvedCandidate>()
        for (client in clientsToTry) {
            if (client.loginRequired && !isLoggedIn) continue

            println("NativeAudioPlayer: Probando cliente: ${client.clientName}")
            val currentTimestamp = if (client.clientName.contains("TVHTML5")) null else signatureTimestamp

            try {
                val playerResult = if (client == YouTubeClient.WEB_REMIX && mainResponse != null) {
                    Result.success(mainResponse)
                } else {
                    YouTube.player(videoId, null, client, currentTimestamp)
                }
                val playerResponse = playerResult.getOrNull()
                val status = playerResponse?.playabilityStatus?.status
                val hasFormats = playerResponse?.streamingData?.adaptiveFormats?.isNotEmpty() == true

                if (playerResponse != null && (status == "OK" || hasFormats)) {
                    val rawFormats = playerResponse.streamingData?.adaptiveFormats
                        ?.filter { it.isAudio } ?: emptyList()
                    val formats = orderFormatsByQuality(rawFormats, audioQuality)
                    if (formats.isEmpty()) continue

                    for (format in formats) {
                        val url = StreamUrlResolution.resolveStreamUrl(
                            NewPipeStreamUrlResolver,
                            format,
                            videoId,
                            client,
                        ).getOrNull()

                        if (url != null) {
                            candidates.add(ResolvedCandidate(client, format, url))
                            break
                        }
                    }
                } else {
                    lastError = playerResponse?.playabilityStatus?.reason ?: "Playability error"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: "Error desconocido en cliente ${client.clientName}"
                println("NativeAudioPlayer: EXCEPCIÓN en cliente ${client.clientName}: ${e.message}")
            }
        }

        val orderedCandidates = orderCandidatesByQuality(candidates, audioQuality)
        for (candidate in orderedCandidates) {
            val referer = refererForClient(candidate.client)
            val headers = buildStreamHeaders(candidate.url, candidate.client.userAgent, referer.takeIf { it.isNotBlank() })
            println(
                "NativeAudioPlayer: Probando formato ${candidate.format.mimeType} " +
                    "(${candidate.format.bitrate}) del cliente ${candidate.client.clientName}",
            )
            if (
                tryStartPlayback(
                    url = candidate.url,
                    headers = headers,
                    contentLengthBytes = candidate.format.contentLength,
                    userAgent = candidate.client.userAgent,
                    referer = referer,
                    allowProxyForVlc = true,
                    initialPositionMs = initialPositionMs,
                    startPlaying = startPlaying,
                    allowCrossfade = allowCrossfade,
                )
            ) {
                println("NativeAudioPlayer: ¡Reproducción iniciada con éxito! (Cliente: ${candidate.client.clientName})")
                return@withContext Result.success(Unit)
            }
        }

        if (candidates.isEmpty()) {
            println("NativeAudioPlayer: Intentando extracción directa mediante NewPipeExtractor...")
            val newPipeStreams = runCatching { NewPipeExtractor.newPipePlayer(videoId) }.getOrDefault(emptyList())
            for ((itag, streamUrl) in newPipeStreams) {
                println("NativeAudioPlayer: NewPipe extractor stream disponible (itag=$itag)")
                val headers = buildStreamHeaders(streamUrl, YouTubeClient.USER_AGENT_WEB, "https://www.youtube.com/")
                if (
                    tryStartPlayback(
                        url = streamUrl,
                        headers = headers,
                        contentLengthBytes = null,
                        userAgent = null,
                        referer = "https://www.youtube.com/",
                        allowProxyForVlc = true,
                        initialPositionMs = initialPositionMs,
                        startPlaying = startPlaying,
                        allowCrossfade = allowCrossfade,
                    )
                ) {
                    println("NativeAudioPlayer: ¡Reproducción iniciada con éxito vía NewPipe!")
                    return@withContext Result.success(Unit)
                }
            }
        }

        val rawError = lastError ?: "No se pudo iniciar reproducción con ningún cliente."
        println("NativeAudioPlayer: ERROR FINAL: $rawError")
        Result.failure(IllegalStateException(rawError))
    }

    suspend fun playStream(
        url: String,
        userAgent: String? = null,
        referer: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!ffmpegAvailable && !ensureVlcInitialized()) {
            return@withContext Result.failure(IllegalStateException("No hay motor de audio disponible (FFmpeg/VLC)."))
        }
        setNormalizationFactor(1.0)
        val headers = buildStreamHeaders(url, userAgent, referer)
        val started = tryStartPlayback(
            url = url,
            headers = headers,
            contentLengthBytes = null,
            userAgent = userAgent,
            referer = referer,
            allowProxyForVlc = false,
        )
        if (started) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Error al cargar stream"))
        }
    }

    suspend fun switchAudioStreamPreservingPosition(
        url: String,
        headers: Map<String, String>,
        contentLengthBytes: Long?,
        targetPositionMs: Long,
        playWhenReady: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ffmpegAvailable && !ensureVlcInitialized()) return@withContext false
        val userAgent = headers["User-Agent"]
        val referer = headers["Referer"]
        tryStartPlayback(
            url = url,
            headers = headers,
            contentLengthBytes = contentLengthBytes,
            userAgent = userAgent,
            referer = referer,
            allowProxyForVlc = false,
            initialPositionMs = targetPositionMs.coerceAtLeast(0L),
            startPlaying = playWhenReady,
            allowCrossfade = false,
        )
    }

    /**
     * Resuelve un stream de video MP4/WebM + cabeceras para Video Mode usando InnerTubeX,
     * resolución de Music Video (OMV) en YouTube Music y fallback con YouTube.player / NewPipeExtractor.
     */
    suspend fun extractVideoStream(
        videoId: String,
        title: String? = null,
        artist: String? = null,
    ): Result<VideoStreamInfo> = withContext(Dispatchers.IO) {
        val audioQuality = preferences.audioQuality.value
        val candidateIds = listOfNotNull(
            resolveMusicVideoId(videoId, title, artist),
            videoId,
        ).distinct()

        var lastError: String? = null
        var lowResFallback: VideoStreamInfo? = null
        val isLoggedIn = YouTube.cookie != null

        for (candidateId in candidateIds) {
            val innertubexResult = DesktopInnerTubeXPlayer.extractVideoStream(
                videoId = candidateId,
                audioQuality = audioQuality,
                maxVideoHeight = 1080,
            )
            if (innertubexResult.isSuccess) {
                val stream = innertubexResult.getOrThrow()
                val videoUrl = stream.videoUrl
                if (!videoUrl.isNullOrBlank() && probeStreamAccessible(videoUrl, stream.headers)) {
                    val info = VideoStreamInfo(
                        url = videoUrl,
                        headers = stream.headers,
                        width = stream.videoWidth,
                        height = stream.videoHeight,
                        mimeType = stream.videoMimeType,
                        contentLengthBytes = stream.videoContentLengthBytes,
                        audioUrl = stream.audioUrl.takeUnless { it.startsWith("sabr", ignoreCase = true) },
                        audioContentLengthBytes = stream.contentLengthBytes,
                        targetVideoId = candidateId,
                    )
                    if ((stream.videoHeight ?: 0) >= 720) {
                        return@withContext Result.success(info)
                    }
                    if (lowResFallback == null || (info.height ?: 0) > (lowResFallback.height ?: 0)) {
                        lowResFallback = info
                    }
                } else {
                    DesktopInnerTubeXPlayer.markStreamClientFailed(candidateId, stream.clientName)
                }
            } else {
                lastError = innertubexResult.exceptionOrNull()?.message
            }
        }

        val videoClients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.IOS,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.WEB,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.MOBILE,
            YouTubeClient.WEB_CREATOR,
        )

        for (candidateId in candidateIds) {
            val signatureTimestamp = NewPipeExtractor.getSignatureTimestamp(candidateId).getOrNull()
            for (client in videoClients) {
                if (client.loginRequired && !isLoggedIn) continue
                val currentTimestamp = if (client.clientName.contains("TVHTML5")) null else signatureTimestamp
                try {
                    val response = YouTube.player(candidateId, null, client, currentTimestamp).getOrNull() ?: continue
                    val status = response.playabilityStatus.status
                    val streamingData = response.streamingData ?: continue
                    if (status != "OK" && streamingData.adaptiveFormats.isEmpty() && streamingData.formats.isNullOrEmpty()) {
                        lastError = response.playabilityStatus.reason
                        continue
                    }
                    val allFormats = streamingData.adaptiveFormats + streamingData.formats.orEmpty()
                    val videoFormats = orderVideoFormats(allFormats)
                    val bestAudioFormat = orderFormatsByQuality(allFormats.filter { it.isAudio }, audioQuality).firstOrNull()
                    val referer = refererForClient(client).takeIf { it.isNotBlank() }
                    val resolvedAudioUrl = bestAudioFormat?.let { fmt ->
                        StreamUrlResolution.resolveStreamUrl(NewPipeStreamUrlResolver, fmt, candidateId, client).getOrNull()
                    }
                    for (format in videoFormats) {
                        val resolvedUrl = StreamUrlResolution.resolveStreamUrl(
                            NewPipeStreamUrlResolver,
                            format,
                            candidateId,
                            client,
                        ).getOrNull() ?: continue
                        val headers = buildStreamHeaders(resolvedUrl, client.userAgent, referer)
                        if (probeStreamAccessible(resolvedUrl, headers)) {
                            return@withContext Result.success(
                                VideoStreamInfo(
                                    url = resolvedUrl,
                                    headers = headers,
                                    width = format.width,
                                    height = format.height,
                                    mimeType = format.mimeType,
                                    contentLengthBytes = format.contentLength,
                                    audioUrl = resolvedAudioUrl,
                                    audioContentLengthBytes = bestAudioFormat?.contentLength,
                                    targetVideoId = candidateId,
                                ),
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message
                }
            }

            val newPipeStreams = runCatching { NewPipeExtractor.newPipePlayer(candidateId) }.getOrDefault(emptyList())
            val preferredVideoItags = listOf(137, 299, 248, 303, 136, 298, 22, 247, 302, 135, 244, 134, 18, 243)
            for (itag in preferredVideoItags) {
                val streamUrl = newPipeStreams.firstOrNull { it.first == itag }?.second ?: continue
                val headers = buildStreamHeaders(streamUrl, YouTubeClient.USER_AGENT_WEB, "https://www.youtube.com/")
                if (probeStreamAccessible(streamUrl, headers)) {
                    return@withContext Result.success(
                        VideoStreamInfo(
                            url = streamUrl,
                            headers = headers,
                            height = heightForItag(itag),
                            targetVideoId = candidateId,
                        ),
                    )
                }
            }
        }

        lowResFallback?.let { return@withContext Result.success(it) }
        Result.failure(IllegalStateException(lastError ?: "No se encontró stream de video reproducible para $videoId"))
    }

    private suspend fun resolveMusicVideoId(
        videoId: String,
        title: String?,
        artist: String?,
    ): String? {
        musicVideoCache[videoId]?.let { return it }
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isBlank()) return null
        val cleanArtist = artist?.trim().orEmpty()

        val queries = listOfNotNull(
            listOf(cleanTitle, cleanArtist, "official video").filter { it.isNotBlank() }.joinToString(" "),
            listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ").takeIf { cleanArtist.isNotBlank() },
        ).distinct()

        for (query in queries) {
            val page = try {
                YouTube.search(query, InnertubeYouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue

            val items = page.items.filterIsInstance<SongItem>()
            if (items.any { it.id == videoId }) {
                musicVideoCache[videoId] = videoId
                return videoId
            }

            val best = selectBestMusicVideoCandidate(cleanTitle, cleanArtist, items)
            if (best != null) {
                musicVideoCache[videoId] = best.id
                return best.id
            }
        }
        return null
    }

    private fun selectBestMusicVideoCandidate(
        trackTitle: String,
        trackArtist: String,
        candidates: List<SongItem>,
    ): SongItem? {
        val normTrackTitle = normalizeMatchText(trackTitle)
        if (normTrackTitle.isBlank()) return null
        val normArtists = trackArtist.split(',', '&', '•')
            .map { normalizeMatchText(it) }
            .filter { it.length >= 2 }

        return candidates
            .mapNotNull { candidate ->
                val normCandTitle = normalizeMatchText(candidate.title)
                if (normCandTitle.isBlank()) return@mapNotNull null

                val hasDisqualifiedVariant = MUSIC_VIDEO_VARIANT_DISQUALIFIERS.any { regex ->
                    regex.containsMatchIn(candidate.title) && !regex.containsMatchIn(trackTitle)
                }
                if (hasDisqualifiedVariant) return@mapNotNull null

                val titleScore = when {
                    normCandTitle == normTrackTitle -> 5
                    normCandTitle.contains(normTrackTitle) -> 3
                    normTrackTitle.length >= 4 && normCandTitle.length >= 4 && normTrackTitle.contains(normCandTitle) -> 2
                    else -> return@mapNotNull null
                }

                val candArtistText = candidate.artists.joinToString(" ") { normalizeMatchText(it.name) }
                val artistMatches = normArtists.isEmpty() || normArtists.any { a ->
                    candArtistText.contains(a) || (a.length >= 3 && normCandTitle.contains(a))
                }
                if (!artistMatches) return@mapNotNull null

                var score = titleScore
                if (normArtists.isNotEmpty()) score += 3
                if (candidate.isVideoSong) score += 4
                if (candidate.musicVideoType?.contains("OMV", ignoreCase = true) == true) score += 3
                if (OFFICIAL_VIDEO_HINT_REGEX.containsMatchIn(candidate.title)) score += 2
                if (score > 0) candidate to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalizeMatchText(raw: String): String {
        val withoutBrackets = raw.replace(BRACKETED_TITLE_DECORATOR_REGEX, " ")
        val decomposed = Normalizer.normalize(withoutBrackets, Normalizer.Form.NFD)
        return decomposed
            .replace(DIACRITIC_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    private fun orderVideoFormats(
        formats: List<PlayerResponse.StreamingData.Format>,
    ): List<PlayerResponse.StreamingData.Format> {
        return formats
            .filter { format ->
                !format.isAudio &&
                    (format.height ?: 0) > 0 &&
                    (
                        format.mimeType.contains("video/mp4", ignoreCase = true) ||
                            format.mimeType.contains("video/webm", ignoreCase = true)
                    )
            }
            .sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { (it.height ?: 0) <= 1080 }
                    .thenByDescending { format ->
                        val h = format.height ?: 0
                        if (h <= 1080) h else -h
                    }
                    .thenBy { format ->
                        when {
                            format.mimeType.contains("avc1", ignoreCase = true) -> 0
                            format.mimeType.contains("video/mp4", ignoreCase = true) &&
                                !format.mimeType.contains("av01", ignoreCase = true) -> 1
                            format.mimeType.contains("video/webm", ignoreCase = true) -> 2
                            else -> 3
                        }
                    }
                    .thenByDescending { it.bitrate },
            )
    }

    private fun heightForItag(itag: Int): Int? = when (itag) {
        137, 299, 248, 303 -> 1080
        136, 298, 22, 247, 302 -> 720
        135, 244 -> 480
        134, 18, 243 -> 360
        else -> null
    }

    private fun probeStreamAccessible(url: String, headers: Map<String, String>): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return true
        }
        return try {
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }
                .header("Range", "bytes=0-0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code == 200 || response.code == 206
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun tryStartPlayback(
        url: String,
        headers: Map<String, String>,
        contentLengthBytes: Long?,
        userAgent: String?,
        referer: String?,
        allowProxyForVlc: Boolean,
        initialPositionMs: Long = 0L,
        startPlaying: Boolean = true,
        allowCrossfade: Boolean = true,
    ): Boolean {
        var httpFailureStatus: Int? = null
        if (ffmpegAvailable) {
            val (ffmpegOk, statusCode) = prepareAndPlayFfmpeg(
                url = url,
                headers = headers,
                contentLengthBytes = contentLengthBytes,
                initialPositionMs = initialPositionMs,
                startPlaying = startPlaying,
                allowCrossfade = allowCrossfade,
            )
            if (ffmpegOk) return true
            httpFailureStatus = statusCode
        }

        // Si falló con 401/403/404/410 desde el servidor origen sin proxy, solo probar VLC si hubo proxy o no fue 4xx fatal
        val canTryVlc = (httpFailureStatus == null || httpFailureStatus !in setOf(401, 403, 404, 410) || allowProxyForVlc)
        if (canTryVlc && ensureVlcInitialized()) {
            val vlcStarted = (
                allowProxyForVlc &&
                    prepareVlcPlayer(
                        PlaybackProxy.createProxyUrl(url, userAgent, referer?.takeIf { it.isNotBlank() }),
                        userAgent,
                        referer,
                    )
                ) || prepareVlcPlayer(url, userAgent, referer)
            if (vlcStarted) {
                activeEngine = Engine.VLC
                if (initialPositionMs > 0L) {
                    seekTo(initialPositionMs)
                }
                if (!startPlaying) {
                    pause()
                }
                return true
            }
        }
        return false
    }

    private suspend fun prepareAndPlayFfmpeg(
        url: String,
        headers: Map<String, String>,
        contentLengthBytes: Long?,
        initialPositionMs: Long = 0L,
        startPlaying: Boolean = true,
        allowCrossfade: Boolean = true,
    ): Pair<Boolean, Int?> {
        cancelFadeJobs()

        val previousPlayer = activeFfmpegPlayer
        val wasPlayingPrevious = previousPlayer?.isPlaying() == true
        val fadeDurationMs = if (allowCrossfade) (crossfadeDurationSec * 1000L).coerceAtLeast(0L) else 0L
        val shouldCrossfade = allowCrossfade && fadeDurationMs > 0L && wasPlayingPrevious

        // Detach current active player before closing/fading so stale callbacks never overwrite new state
        activeFfmpegPlayer = null
        if (previousPlayer != null) {
            if (shouldCrossfade) {
                startOutgoingFadeOut(previousPlayer, minOf(fadeDurationMs, 3500L))
            } else {
                outgoingFfmpegPlayer?.close()
                outgoingFfmpegPlayer = null
                previousPlayer.close()
            }
        } else {
            outgoingFfmpegPlayer?.close()
            outgoingFfmpegPlayer = null
        }

        if (activeEngine == Engine.VLC) {
            withContext(Dispatchers.Main) {
                mediaPlayer?.controls()?.stop()
            }
        }

        val committed = java.util.concurrent.atomic.AtomicBoolean(false)

        fun buildPlayer(): AudioPlayer {
            lateinit var instance: AudioPlayer
            instance = AudioPlayer(
                onPlaybackCompleted = { player ->
                    if (activeFfmpegPlayer === player) {
                        notifyStatus(PlaybackStatus.ENDED)
                    }
                },
                onPlaybackFailed = { player, error ->
                    if (committed.get() && activeFfmpegPlayer === player) {
                        val msg = error.message ?: "Error de reproducción FFmpeg"
                        println("NativeAudioPlayer: FFmpeg ERROR: $msg")
                        notifyError(msg)
                        notifyStatus(PlaybackStatus.ERROR)
                    }
                },
                onStateChanged = {
                    if (committed.get() && activeFfmpegPlayer === instance) {
                        syncStatusFromFfmpeg(instance)
                    }
                },
                rendererFactory = { sampleRate, channelCount ->
                    AudioRenderer(
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        configuration = { currentEqConfig },
                        silenceSettings = { skipSilenceEnabled to false },
                    )
                },
            )
            return instance
        }

        val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        val isTrustedRangeTarget =
            isHttp &&
                !isLiveStreamUrl(url) &&
                url.toHttpUrlOrNull()?.let(::isTrustedDirectMediaEndpoint) == true

        fun tryPrepare(player: AudioPlayer): Pair<Boolean, Int?> {
            player.setPlaybackSpeed(currentTempo)
            player.setPlaybackPitch(pitchFactorFor(currentPitchSemitone))
            fadeGain = if (fadeDurationMs > 0L) 0.0 else 1.0
            player.setVolume(effectiveFfmpegVolume())

            var prepared = false
            if (isTrustedRangeTarget) {
                val rangeInput = HttpRangeInputStream(
                    httpClient = createDirectMediaHttpClient(YouTube.proxy),
                    url = url,
                    requestHeaders = headers,
                    mediaSize = contentLengthBytes ?: 0L,
                    chunkSize = 1_048_576,
                    requestTimeoutMs = 35_000L,
                    prefetchAhead = true,
                    targetRequestDurationMs = 4_000L,
                )
                val maxSize = (contentLengthBytes ?: (Int.MAX_VALUE.toLong() - 8L))
                    .coerceIn(1L, Int.MAX_VALUE.toLong() - 8L)
                    .toInt()
                prepared = player.prepareStream(rangeInput, maxSize, startPaused = true)
                val failureStatus = (player.prepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode
                if (!prepared && failureStatus in setOf(401, 403, 404, 410)) {
                    return false to failureStatus
                }
            }

            if (!prepared) {
                prepared = player.prepareFile(url, headers, startPaused = true)
            }

            if (!prepared) {
                val failureStatus = (player.prepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode
                return false to failureStatus
            }

            if (initialPositionMs > 0L) {
                player.seekToAwait(initialPositionMs, 15_000L)
                if (player.hasFailed) {
                    val failureStatus = (player.prepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode
                    return false to failureStatus
                }
            }

            player.prebuffer()
            if (player.hasFailed) {
                val failureStatus = (player.prepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode
                return false to failureStatus
            }
            return true to null
        }

        var newPlayer = buildPlayer()
        var (prepared, failureStatus) = tryPrepare(newPlayer)

        if (!prepared && newPlayer.prepareFailure?.isAudioOutputUnavailable() == true && outgoingFfmpegPlayer != null) {
            // Release outgoing line if the host audio device only permits a single SourceDataLine
            fadeOutJob?.cancel()
            fadeOutJob = null
            outgoingFfmpegPlayer?.close()
            outgoingFfmpegPlayer = null
            newPlayer.close()
            newPlayer = buildPlayer()
            val retryResult = tryPrepare(newPlayer)
            prepared = retryResult.first
            failureStatus = retryResult.second
        }

        if (!prepared) {
            newPlayer.close()
            fadeGain = 1.0
            return false to failureStatus
        }

        activeFfmpegPlayer = newPlayer
        activeEngine = Engine.FFMPEG

        if (startPlaying) {
            newPlayer.play()
        } else {
            newPlayer.pause()
        }
        if (newPlayer.hasFailed) {
            val status = (newPlayer.prepareFailure as? HttpMediaStatusException)?.playbackHttpStatusCode
            activeFfmpegPlayer = null
            activeEngine = Engine.NONE
            newPlayer.close()
            fadeGain = 1.0
            return false to status
        }
        committed.set(true)

        if (startPlaying && fadeDurationMs > 0L) {
            val targetFadeInMs = if (shouldCrossfade) minOf(fadeDurationMs, 3500L) else minOf(fadeDurationMs, 1500L)
            startFadeIn(newPlayer, targetFadeInMs)
        } else {
            fadeGain = 1.0
            newPlayer.setVolume(effectiveFfmpegVolume())
        }

        notifyBuffering(false)
        notifyStatus(if (startPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
        notifyTime(
            newPlayer.getCurrentPosition().takeIf { it > 0L } ?: initialPositionMs.coerceAtLeast(0L),
            newPlayer.getDuration(),
        )
        return true to null
    }

    private fun startOutgoingFadeOut(oldPlayer: AudioPlayer, durationMs: Long) {
        fadeOutJob?.cancel()
        outgoingFfmpegPlayer?.close()
        outgoingFfmpegPlayer = oldPlayer
        val startVol = oldPlayer.getVolume()
        fadeOutJob = playerScope.launch {
            try {
                val steps = 20
                val stepMs = (durationMs / steps).coerceAtLeast(15L)
                for (i in 1..steps) {
                    if (!isActive || outgoingFfmpegPlayer !== oldPlayer) return@launch
                    val ratio = 1f - (i.toFloat() / steps)
                    oldPlayer.setVolume((startVol * ratio).coerceIn(0f, 1f))
                    delay(stepMs)
                }
            } finally {
                if (outgoingFfmpegPlayer === oldPlayer) {
                    outgoingFfmpegPlayer = null
                }
                oldPlayer.closeAsync()
            }
        }
    }

    private fun startFadeIn(targetPlayer: AudioPlayer, durationMs: Long) {
        fadeInJob?.cancel()
        fadeGain = 0.0
        targetPlayer.setVolume(0f)
        fadeInJob = playerScope.launch {
            val steps = 20
            val stepMs = (durationMs / steps).coerceAtLeast(15L)
            for (i in 1..steps) {
                if (!isActive || activeFfmpegPlayer !== targetPlayer) return@launch
                fadeGain = (i.toDouble() / steps).coerceIn(0.0, 1.0)
                targetPlayer.setVolume(effectiveFfmpegVolume())
                delay(stepMs)
            }
            fadeGain = 1.0
            targetPlayer.setVolume(effectiveFfmpegVolume())
        }
    }

    private fun cancelFadeJobs() {
        fadeInJob?.cancel()
        fadeInJob = null
        fadeOutJob?.cancel()
        fadeOutJob = null
    }

    private fun syncStatusFromFfmpeg(player: AudioPlayer) {
        if (player.hasFailed) return
        val next = when {
            player.wantsPlayback -> PlaybackStatus.PLAYING
            currentStatus == PlaybackStatus.PLAYING -> PlaybackStatus.PAUSED
            else -> return
        }
        if (next != currentStatus) {
            notifyStatus(next)
        }
    }

    private fun notifyStatus(status: PlaybackStatus) {
        currentStatus = status
        runOnEdt {
            onStatusChanged?.invoke(status)
        }
    }

    private fun notifyBuffering(buffering: Boolean) {
        runOnEdt {
            onBuffering?.invoke(buffering)
        }
    }

    private fun notifyError(message: String) {
        runOnEdt {
            onError?.invoke(message)
        }
    }

    private fun notifyTime(currentMs: Long, totalMs: Long) {
        runOnEdt {
            onTimeChanged?.invoke(currentMs, totalMs)
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }

    private suspend fun prepareVlcPlayer(url: String, userAgent: String? = null, referer: String? = null): Boolean =
        withContext(Dispatchers.Main) {
            activeFfmpegPlayer?.close()
            activeFfmpegPlayer = null
            cancelFadeJobs()
            outgoingFfmpegPlayer?.close()
            outgoingFfmpegPlayer = null
            fadeGain = 1.0

            suspendCancellableCoroutine { continuation ->
                val player = mediaPlayer ?: run {
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                val listener = object : MediaPlayerEventAdapter() {
                    override fun playing(mp: MediaPlayer?) {
                        mp?.events()?.removeMediaPlayerEventListener(this)
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun error(mp: MediaPlayer?) {
                        mp?.events()?.removeMediaPlayerEventListener(this)
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun finished(mediaPlayer: MediaPlayer?) {
                        mediaPlayer?.events()?.removeMediaPlayerEventListener(this)
                        if (continuation.isActive) continuation.resume(false)
                    }
                }

                player.events().addMediaPlayerEventListener(listener)

                val options = mutableListOf<String>()
                userAgent?.let { options.add(":http-user-agent=$it") }
                referer?.takeIf { it.isNotBlank() }?.let { options.add(":http-referrer=$it") }

                val success = player.media().play(url, *options.toTypedArray())
                if (!success) {
                    player.events().removeMediaPlayerEventListener(listener)
                    if (continuation.isActive) continuation.resume(false)
                }

                player.controls().setRate(playbackRate)
                applyVolume()
                syncVlcFallbackEqualizer()

                continuation.invokeOnCancellation {
                    player.events().removeMediaPlayerEventListener(listener)
                    player.controls().stop()
                }
            }
        }

    fun pause() {
        when (activeEngine) {
            Engine.FFMPEG -> {
                activeFfmpegPlayer?.pause()
                notifyStatus(PlaybackStatus.PAUSED)
            }
            Engine.VLC -> mediaPlayer?.controls()?.pause()
            Engine.NONE -> Unit
        }
    }

    fun resume() {
        when (activeEngine) {
            Engine.FFMPEG -> {
                activeFfmpegPlayer?.resume()
                notifyStatus(PlaybackStatus.PLAYING)
            }
            Engine.VLC -> mediaPlayer?.controls()?.play()
            Engine.NONE -> Unit
        }
    }

    fun togglePlayPause() {
        when (activeEngine) {
            Engine.FFMPEG -> {
                val player = activeFfmpegPlayer ?: return
                if (player.wantsPlayback || player.isPlaying()) {
                    pause()
                } else {
                    resume()
                }
            }
            Engine.VLC -> {
                val player = mediaPlayer ?: return
                if (player.status().isPlaying) {
                    player.controls().pause()
                } else {
                    player.controls().play()
                }
            }
            Engine.NONE -> Unit
        }
    }

    fun setPlaybackRate(rate: Double) {
        val clamped = rate.toFloat().coerceIn(0.5f, 2.0f)
        playbackRate = rate.toFloat()
        currentTempo = clamped
        activeFfmpegPlayer?.setPlaybackSpeed(clamped)
        if (activeEngine == Engine.VLC) {
            mediaPlayer?.controls()?.setRate(playbackRate)
        }
    }

    fun stop() {
        cancelFadeJobs()
        fadeGain = 1.0
        outgoingFfmpegPlayer?.close()
        outgoingFfmpegPlayer = null
        activeFfmpegPlayer?.close()
        activeFfmpegPlayer = null
        if (activeEngine == Engine.VLC) {
            mediaPlayer?.controls()?.stop()
        }
        activeEngine = Engine.NONE
        notifyStatus(PlaybackStatus.STOPPED)
    }

    fun seekTo(positionMs: Long) {
        when (activeEngine) {
            Engine.FFMPEG -> {
                val player = activeFfmpegPlayer ?: return
                player.seekTo(positionMs)
                notifyTime(positionMs, player.getDuration())
            }
            Engine.VLC -> mediaPlayer?.controls()?.setTime(positionMs)
            Engine.NONE -> Unit
        }
    }

    fun setVolume(volume: Double) {
        baseVolume = volume.coerceIn(0.0, 1.0)
        applyVolume()
    }

    fun getVolume(): Double = when (activeEngine) {
        Engine.FFMPEG -> activeFfmpegPlayer?.getVolume()?.toDouble() ?: baseVolume
        Engine.VLC -> (mediaPlayer?.audio()?.volume() ?: 100) / 100.0
        Engine.NONE -> baseVolume
    }

    fun getCurrentPosition(): Long = when (activeEngine) {
        Engine.FFMPEG -> activeFfmpegPlayer?.getCurrentPosition() ?: 0L
        Engine.VLC -> mediaPlayer?.status()?.time() ?: 0L
        Engine.NONE -> 0L
    }

    fun getCurrentAudioPtsMs(): Long? =
        if (activeEngine == Engine.FFMPEG) activeFfmpegPlayer?.currentAudioPtsMs() else null

    fun getDuration(): Long = when (activeEngine) {
        Engine.FFMPEG -> activeFfmpegPlayer?.getDuration() ?: 0L
        Engine.VLC -> mediaPlayer?.status()?.length() ?: 0L
        Engine.NONE -> 0L
    }

    fun isPlaying(): Boolean = when (activeEngine) {
        Engine.FFMPEG -> {
            val player = activeFfmpegPlayer
            player != null && (player.isPlaying() || player.wantsPlayback)
        }
        Engine.VLC -> mediaPlayer?.status()?.isPlaying == true
        Engine.NONE -> false
    }

    fun getStatus(): PlaybackStatus = when (activeEngine) {
        Engine.FFMPEG -> currentStatus
        Engine.VLC -> {
            val mp = mediaPlayer ?: return PlaybackStatus.IDLE
            if (mp.status().isPlaying) PlaybackStatus.PLAYING
            else if (mp.status().length() > 0 && mp.status().time() == 0L && !mp.status().isPlaying) PlaybackStatus.READY
            else if (!mp.status().isPlaying && mp.status().length() > 0) PlaybackStatus.PAUSED
            else PlaybackStatus.IDLE
        }
        Engine.NONE -> currentStatus
    }

    fun release() {
        cancelFadeJobs()
        playerScope.cancel()
        outgoingFfmpegPlayer?.close()
        outgoingFfmpegPlayer = null
        activeFfmpegPlayer?.close()
        activeFfmpegPlayer = null
        mediaPlayer?.release()
        factory?.release()
        mediaPlayer = null
        factory = null
        activeEngine = Engine.NONE
    }

    private fun effectiveFfmpegVolume(): Float =
        (baseVolume * normalizationFactor * fadeGain).coerceIn(0.0, 1.0).toFloat()

    private fun applyVolume() {
        val applied = (baseVolume * normalizationFactor).coerceIn(0.0, 1.0)
        activeFfmpegPlayer?.setVolume(effectiveFfmpegVolume())
        mediaPlayer?.audio()?.setVolume((applied * 100).toInt())
    }

    private fun setNormalizationFactor(factor: Double) {
        normalizationFactor = factor.coerceIn(0.0, 1.0)
        applyVolume()
    }

    private fun refererForClient(client: YouTubeClient): String = when {
        client.clientName.contains("REMIX") -> "https://music.youtube.com/"
        client.clientName.contains("WEB") -> "https://www.youtube.com/"
        else -> ""
    }

    private fun buildStreamHeaders(
        url: String,
        userAgent: String?,
        referer: String?,
    ): Map<String, String> = buildMap {
        if (!userAgent.isNullOrBlank()) {
            put("User-Agent", userAgent)
        }
        if (!referer.isNullOrBlank()) {
            put("Referer", referer)
            put("Origin", referer.trimEnd('/'))
        }
        val cookie = YouTube.cookie?.takeIf { it.isNotBlank() }
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        if (cookie != null && host != null && (host.endsWith("googlevideo.com") || host.endsWith("youtube.com"))) {
            put("Cookie", cookie)
        }
    }

    private data class ResolvedCandidate(
        val client: YouTubeClient,
        val format: PlayerResponse.StreamingData.Format,
        val url: String,
    )

    private fun orderCandidatesByQuality(
        candidates: List<ResolvedCandidate>,
        audioQuality: AudioQuality,
    ): List<ResolvedCandidate> {
        if (candidates.isEmpty()) return candidates
        return when (audioQuality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> candidates.sortedByDescending { it.format.bitrate }
            AudioQuality.LOW -> candidates.sortedBy { it.format.bitrate }
            AudioQuality.MEDIUM -> candidates.sortedWith(
                compareBy<ResolvedCandidate> { abs(it.format.bitrate - 192_000) }
                    .thenByDescending { it.format.bitrate },
            )
        }
    }

    private fun orderFormatsByQuality(
        formats: List<PlayerResponse.StreamingData.Format>,
        audioQuality: AudioQuality,
    ): List<PlayerResponse.StreamingData.Format> {
        if (formats.isEmpty()) return formats
        return when (audioQuality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> formats.sortedByDescending { it.bitrate }
            AudioQuality.LOW -> formats.sortedBy { it.bitrate }
            AudioQuality.MEDIUM -> formats.sortedWith(
                compareBy<PlayerResponse.StreamingData.Format> {
                    abs(it.bitrate - 192_000)
                }.thenByDescending { it.bitrate },
            )
        }
    }

    enum class PlaybackStatus {
        IDLE,
        READY,
        PLAYING,
        PAUSED,
        BUFFERING,
        STOPPED,
        ENDED,
        ERROR,
    }

    private companion object {
        val MUSIC_VIDEO_VARIANT_DISQUALIFIERS = listOf(
            Regex("\\bkaraoke\\b", RegexOption.IGNORE_CASE),
            Regex("\\binstrumental\\b", RegexOption.IGNORE_CASE),
            Regex("\\blyrics?\\b", RegexOption.IGNORE_CASE),
            Regex("\\bletra\\b", RegexOption.IGNORE_CASE),
            Regex("\\bcover\\b", RegexOption.IGNORE_CASE),
            Regex("\\bslowed\\b", RegexOption.IGNORE_CASE),
            Regex("\\breverb\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsped\\s*up\\b", RegexOption.IGNORE_CASE),
            Regex("\\bnightcore\\b", RegexOption.IGNORE_CASE),
            Regex("\\b8d\\b", RegexOption.IGNORE_CASE),
            Regex("\\bremix\\b", RegexOption.IGNORE_CASE),
            Regex("\\ben\\s+vivo\\b", RegexOption.IGNORE_CASE),
            Regex("\\blive\\b", RegexOption.IGNORE_CASE),
            Regex("\\bac[uú]stic[oa]?\\b", RegexOption.IGNORE_CASE),
            Regex("\\breacti[oó]n\\b", RegexOption.IGNORE_CASE),
            Regex("\\btutorial\\b", RegexOption.IGNORE_CASE),
        )
        val OFFICIAL_VIDEO_HINT_REGEX = Regex(
            "(official\\s*(music\\s*)?video|video\\s*oficial|\\bm/?v\\b)",
            RegexOption.IGNORE_CASE,
        )
        val BRACKETED_TITLE_DECORATOR_REGEX = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
        val DIACRITIC_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
