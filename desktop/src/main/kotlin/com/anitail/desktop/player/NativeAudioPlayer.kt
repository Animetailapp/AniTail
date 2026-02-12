package com.anitail.desktop.player

import com.anitail.desktop.YouTube
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.pages.NewPipeUtils
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DesktopPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.coroutines.resume
import javax.swing.SwingUtilities

/**
 * Servicio de reproducción de audio nativo usando VLCJ (VLC for Java).
 * Reemplaza el WebView y JavaFX Media para soportar formatos modernos (WebM/Opus)
 * y mejorar la compatibilidad con streams de YouTube.
 */
class NativeAudioPlayer {

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val preferences = DesktopPreferences.getInstance()

    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: MediaPlayer? = null
    private var baseVolume: Double = 1.0
    private var normalizationFactor: Double = 1.0
    private var playbackRate: Float = 1.0f

    // Callbacks de estado
    var onStatusChanged: ((PlaybackStatus) -> Unit)? = null
    var onTimeChanged: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null

    init {
        // Descubrir librerías nativas de VLC
        if (NativeDiscovery().discover()) {
            println("NativeAudioPlayer: VLC nativo encontrado e inicializado.")
            try {
                factory = MediaPlayerFactory()
                mediaPlayer = factory?.mediaPlayers()?.newMediaPlayer()
                setupVlcEvents()
            } catch (e: Exception) {
                println("NativeAudioPlayer: Error al inicializar VLC Factory: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("NativeAudioPlayer: CRITICAL - No se encontró VLC nativo en el sistema.")
            // Aquí podríamos fallar o notificar, pero dejaremos que los métodos fallen controladamente
        }
    }

    private fun setupVlcEvents() {
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(player: MediaPlayer?) {
                SwingUtilities.invokeLater {
                    onStatusChanged?.invoke(PlaybackStatus.PLAYING)
                    onBuffering?.invoke(false)
                }
            }

            override fun paused(player: MediaPlayer?) {
                SwingUtilities.invokeLater {
                    onStatusChanged?.invoke(PlaybackStatus.PAUSED)
                }
            }

            override fun stopped(player: MediaPlayer?) {
                SwingUtilities.invokeLater {
                    onStatusChanged?.invoke(PlaybackStatus.STOPPED)
                }
            }

            override fun finished(player: MediaPlayer?) {
                SwingUtilities.invokeLater {
                    onStatusChanged?.invoke(PlaybackStatus.ENDED)
                }
            }

            override fun buffering(player: MediaPlayer?, newCache: Float) {
                SwingUtilities.invokeLater {
                    if (newCache < 100.0f) {
                        onBuffering?.invoke(true)
                    } else {
                        onBuffering?.invoke(false)
                    }
                }
            }

            override fun error(player: MediaPlayer?) {
                SwingUtilities.invokeLater {
                    val msg = "Error interno de VLC"
                    println("NativeAudioPlayer: VLC ERROR: $msg")
                    onError?.invoke(msg)
                    onStatusChanged?.invoke(PlaybackStatus.ERROR)
                }
            }
            
            override fun timeChanged(player: MediaPlayer?, newTime: Long) {
                 SwingUtilities.invokeLater {
                     onTimeChanged?.invoke(newTime, player?.status()?.length() ?: 0L)
                 }
            }
            
            override fun positionChanged(player: MediaPlayer?, newPosition: Float) {
                // Alternativa a timeChanged
            }
        })
    }

    /**
     * Reproduce un video de YouTube por su ID.
     * Obtiene la URL de streaming de audio y la reproduce nativamente.
     */
    suspend fun play(videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (mediaPlayer == null) {
            return@withContext Result.failure(IllegalStateException("VLC no está disponible. Instale VLC Media Player."))
        }

        println("NativeAudioPlayer: Intentando reproducir videoId: $videoId con VLC")

        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        val isLoggedIn = YouTube.cookie != null
        var lastError: String? = null
        val clientsToTry = StreamClientOrder.build()
        val audioQuality = preferences.audioQuality.value
        val normalizationEnabled = preferences.normalizeAudio.value
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
                val playerResponse = if (client == YouTubeClient.WEB_REMIX && mainResponse != null) {
                    mainResponse
                } else {
                    YouTube.player(videoId, null, client, currentTimestamp).getOrNull()
                }
                val status = playerResponse?.playabilityStatus?.status
                val hasFormats = playerResponse?.streamingData?.adaptiveFormats?.isNotEmpty() == true

                if (status == "OK" || hasFormats) {
                    println("NativeAudioPlayer: Formatos encontrados para cliente ${client.clientName}")
                    val rawFormats = playerResponse?.streamingData?.adaptiveFormats
                        ?.filter { it.isAudio } ?: emptyList()
                    val formats = orderFormatsByQuality(rawFormats, audioQuality)

                    if (formats.isEmpty()) {
                        println("NativeAudioPlayer: No se encontraron formatos de audio para ${client.clientName}")
                        continue
                    }

                    for (format in formats) {
                        println("NativeAudioPlayer: Probando formato: ${format.mimeType} (${format.bitrate})")
                        val url = StreamUrlResolution.resolveStreamUrl(
                            NewPipeStreamUrlResolver,
                            format,
                            videoId,
                            client
                        ).getOrNull()

                        if (url != null) {
                            candidates.add(ResolvedCandidate(client, format, url))
                            break
                        }
                    }
                } else {
                    lastError = playerResponse?.playabilityStatus?.reason ?: "Playability error"
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Error desconocido en cliente ${client.clientName}"
                println("NativeAudioPlayer: EXCEPCIÓN en loop de cliente ${client.clientName}: ${e.message}")
            }
        }

        val orderedCandidates = orderCandidatesByQuality(candidates, audioQuality)
        for (candidate in orderedCandidates) {
            val referer = when {
                candidate.client.clientName.contains("REMIX") -> "https://music.youtube.com/"
                candidate.client.clientName.contains("WEB") -> "https://www.youtube.com/"
                else -> ""
            }
            println(
                "NativeAudioPlayer: Iniciando VLC con formato ${candidate.format.mimeType} " +
                    "(${candidate.format.bitrate}) del cliente ${candidate.client.clientName}"
            )
            val prepared = preparePlayer(candidate.url, candidate.client.userAgent, referer)
            if (prepared) {
                println("NativeAudioPlayer: ¡Reproducción iniciada con éxito! (Cliente: ${candidate.client.clientName})")
                return@withContext Result.success(Unit)
            } else {
                println("NativeAudioPlayer: VLC falló al cargar stream del cliente ${candidate.client.clientName}")
            }
        }

        // Si todos los clientes locales fallaron, intentar con Remote Fallbacks (Piped)
        println("NativeAudioPlayer: Fallback local agotado. Intentando con Remote APIs (Piped)...")
        
            // Sin fallback remoto: si no se resolvió localmente, lo reportamos como fallo.
            println("NativeAudioPlayer: No se resolvió stream localmente para videoId=$videoId; sin fallback remoto.")

        val rawError = lastError ?: "No se pudo iniciar reproducción con ningún cliente."
        println("NativeAudioPlayer: ERROR FINAL: $rawError")
        Result.failure(IllegalStateException(rawError))
    }


    suspend fun playStream(
        url: String,
        userAgent: String? = null,
        referer: String? = null
    ): Result<Unit> = withContext(Dispatchers.Main) {
        if (mediaPlayer == null) return@withContext Result.failure(IllegalStateException("VLC no inicializado"))
        setNormalizationFactor(1.0)
        
        val prepared = preparePlayer(url, userAgent, referer)
        if (prepared) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Error al cargar stream"))
        }
    }

    /**
     * Prepara VLC para reproducir la URL.
     * Dado que VLC es asíncrono, iniciamos la reproducción y esperamos un evento de éxito o error.
     */
    private suspend fun preparePlayer(url: String, userAgent: String? = null, referer: String? = null): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val player = mediaPlayer ?: run {
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

            // Listener temporal para detectar el inicio o fallo inicial
            val listener = object : MediaPlayerEventAdapter() {
                override fun playing(mp: MediaPlayer?) {
                    mp?.events()?.removeMediaPlayerEventListener(this)
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun error(mp: MediaPlayer?) {
                    mp?.events()?.removeMediaPlayerEventListener(this)
                    if (continuation.isActive) continuation.resume(false)
                }
                
                // Si el player se para inmediatamente (ej: end of media inmediato por error de formato)
                override fun finished(mediaPlayer: MediaPlayer?) {
                    mediaPlayer?.events()?.removeMediaPlayerEventListener(this)
                     // Si termina inmediatamente sin haber hecho "playing", asumimos fallo de carga
                     // Pero es difícil saber si reproducio un frame o no.
                     // Asumiremos false si fue inmediato.
                     if (continuation.isActive) continuation.resume(false)
                }
            }

            player.events().addMediaPlayerEventListener(listener)
            
            val options = mutableListOf<String>()
            userAgent?.let { options.add(":http-user-agent=$it") }
            referer?.let { options.add(":http-referrer=$it") }

            val success = player.media().play(url, *options.toTypedArray())
            if (!success) {
                player.events().removeMediaPlayerEventListener(listener)
                if (continuation.isActive) continuation.resume(false)
            }
            
            // Set playback rate and volume
             player.controls().setRate(playbackRate)
             applyVolume()

            continuation.invokeOnCancellation {
                player.events().removeMediaPlayerEventListener(listener)
                player.controls().stop()
            }
        }
    }


    fun pause() {
        mediaPlayer?.controls()?.pause()
    }

    fun resume() {
        mediaPlayer?.controls()?.play()
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            player.controls().play()
        }
    }

    fun setPlaybackRate(rate: Double) {
        playbackRate = rate.toFloat()
        mediaPlayer?.controls()?.setRate(playbackRate)
    }

    fun stop() {
        mediaPlayer?.controls()?.stop()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.controls()?.setTime(positionMs)
    }

    fun setVolume(volume: Double) {
        // VLC volume 0-100 (standard), can be up to 200 via software amp
        baseVolume = volume.coerceIn(0.0, 1.0)
        applyVolume()
    }

    fun getVolume(): Double = (mediaPlayer?.audio()?.volume() ?: 100) / 100.0

    fun getCurrentPosition(): Long {
        return mediaPlayer?.status()?.time() ?: 0L
    }

    fun getDuration(): Long {
        return mediaPlayer?.status()?.length() ?: 0L
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.status()?.isPlaying == true
    }

    fun getStatus(): PlaybackStatus {
        val mp = mediaPlayer ?: return PlaybackStatus.IDLE
        return if (mp.status().isPlaying) PlaybackStatus.PLAYING
        else if (mp.status().length() > 0 && mp.status().time() == 0L && !mp.status().isPlaying) PlaybackStatus.READY
        else if (!mp.status().isPlaying && mp.status().length() > 0) PlaybackStatus.PAUSED 
        else PlaybackStatus.IDLE
        // VLCJ mapping is implied by events, direct status check is simpler
    }

    fun release() {
        mediaPlayer?.release()
        factory?.release()
    }

    private fun applyVolume() {
        val applied = (baseVolume * normalizationFactor).coerceIn(0.0, 1.0)
        mediaPlayer?.audio()?.setVolume((applied * 100).toInt())
    }

    private fun setNormalizationFactor(factor: Double) {
        normalizationFactor = factor.coerceIn(0.0, 1.0)
        applyVolume()
    }

    private data class ResolvedCandidate(
        val client: YouTubeClient,
        val format: com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format,
        val url: String,
    )

    private fun orderCandidatesByQuality(
        candidates: List<ResolvedCandidate>,
        audioQuality: AudioQuality
    ): List<ResolvedCandidate> {
        if (candidates.isEmpty()) return candidates
        return when (audioQuality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> candidates.sortedByDescending { it.format.bitrate }
            AudioQuality.LOW -> candidates.sortedBy { it.format.bitrate }
            AudioQuality.MEDIUM -> candidates.sortedWith(
                compareBy<ResolvedCandidate> { abs(it.format.bitrate - 192_000) }
                    .thenByDescending { it.format.bitrate }
            )
        }
    }

    private fun orderFormatsByQuality(
        formats: List<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format>,
        audioQuality: AudioQuality
    ): List<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format> {
        if (formats.isEmpty()) return formats
        return when (audioQuality) {
            AudioQuality.HIGH, AudioQuality.AUTO -> formats.sortedByDescending { it.bitrate }
            AudioQuality.LOW -> formats.sortedBy { it.bitrate }
            AudioQuality.MEDIUM -> formats.sortedWith(
                compareBy<com.anitail.innertube.models.response.PlayerResponse.StreamingData.Format> {
                    abs(it.bitrate - 192_000)
                }.thenByDescending { it.bitrate }
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
}
