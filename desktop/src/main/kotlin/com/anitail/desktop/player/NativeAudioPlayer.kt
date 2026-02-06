package com.anitail.desktop.player

import com.anitail.desktop.util.JavaFxManager
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.pages.NewPipeUtils
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.resume

/**
 * Servicio de reproducción de audio nativo usando JavaFX Media.
 * Reemplaza el WebView con reproducción directa de streams de YouTube.
 */
class NativeAudioPlayer {

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var volume: Double = 1.0

    // Callbacks de estado
    var onStatusChanged: ((PlaybackStatus) -> Unit)? = null
    var onTimeChanged: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null

    init {
        JavaFxManager.ensureInitialized()
    }

    private val STREAM_FALLBACK_CLIENTS = arrayOf(
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.MWEB,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR
    )

    /**
     * Reproduce un video de YouTube por su ID.
     * Obtiene la URL de streaming de audio y la reproduce nativamente.
     */
    suspend fun play(videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        println("NativeAudioPlayer: Intentando reproducir videoId: $videoId")

        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        println("NativeAudioPlayer: Signature timestamp: $signatureTimestamp")

        val isLoggedIn = YouTube.cookie != null
        val mainClient = YouTubeClient.WEB_REMIX

        // Intentar con el cliente principal y luego con los fallbacks
        var lastError: String? = null

        // Priorizar el cliente principal y luego los fallbacks más robustos
        val clientsToTry = (listOf(
            mainClient,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.WEB
        ) + STREAM_FALLBACK_CLIENTS).distinct()

        for (client in clientsToTry) {
            println("NativeAudioPlayer: Probando cliente: ${client.clientName}")

            if (client.loginRequired && !isLoggedIn) {
                println("NativeAudioPlayer: Saltando cliente ${client.clientName} (requiere login)")
                continue
            }

            // Para TVHTML5_SIMPLY_EMBEDDED_PLAYER, a veces no necesitamos el signatureTimestamp
            val currentTimestamp =
                if (client.clientName.contains("TVHTML5")) null else signatureTimestamp

            val playerResponse = try {
                println("NativeAudioPlayer: Llamando a YouTube.player para ${client.clientName}...")
                val res = YouTube.player(videoId, null, client, currentTimestamp).getOrNull()
                if (res == null) println("NativeAudioPlayer: YouTube.player devolvió NULL para ${client.clientName}")
                res
            } catch (e: Exception) {
                println("NativeAudioPlayer: EXCEPCIÓN en YouTube.player para ${client.clientName}: ${e.message}")
                e.printStackTrace()
                null
            }

            if (playerResponse?.playabilityStatus?.status == "OK") {
                println("NativeAudioPlayer: PlayabilityStatus OK para cliente ${client.clientName}")

                // Filtrar formatos compatibles con JavaFX (audio/mp4 suele ser AAC)
                val formats = playerResponse.streamingData?.adaptiveFormats
                    ?.filter { it.isAudio && it.mimeType.contains("audio/mp4") }
                    ?.sortedByDescending { it.bitrate } ?: emptyList()

                if (formats.isEmpty()) {
                    println("NativeAudioPlayer: No se encontraron formatos audio/mp4 adecuados para ${client.clientName}")
                }

                for (format in formats) {
                    println("NativeAudioPlayer: Probando formato: ${format.mimeType} (${format.bitrate})")
                    val url =
                        NewPipeUtils.getStreamUrl(format, videoId, client.userAgent).getOrNull()

                    if (url != null) {
                        println("NativeAudioPlayer: URL obtenida, usando PlaybackProxy con headers del cliente...")

                        // Determinar referer adecuado: solo para clientes WEB
                        val referer = if (client.clientName.contains("WEB")) {
                            if (client.clientName.contains("REMIX")) {
                                "https://music.youtube.com/"
                            } else {
                                "https://www.youtube.com/"
                            }
                        } else {
                            "" // Apps móviles no suelen enviar referer en el stream, enviamos vacío explícito
                        }


                        val proxyUrl = PlaybackProxy.createProxyUrl(
                            url = url,
                            userAgent = client.userAgent,
                            referer = referer
                        )

                        val prepared = preparePlayer(proxyUrl)
                        if (prepared) {
                            println("NativeAudioPlayer: ¡Reproducción iniciada con éxito! (Cliente: ${client.clientName})")
                            return@withContext Result.success(Unit)
                        } else {
                            println("NativeAudioPlayer: MediaPlayer falló al cargar stream del cliente ${client.clientName}")
                        }
                    } else {
                        // Si falla la deofuscación, pasamos al siguiente formato/cliente
                        println("NativeAudioPlayer: Falló la deofuscación para ${client.clientName}. Probablemente regex desactualizado.")
                    }
                }
            } else {
                val reason = playerResponse?.playabilityStatus?.reason
                println("NativeAudioPlayer: PlayabilityStatus no OK para cliente ${client.clientName}: $reason")
                lastError = reason
            }
        }

        val rawError = lastError ?: "Error de red o formatos no compatibles"
        val userFriendlyError = if (rawError.contains("bot") || rawError.contains("sesión")) {
            "YouTube requiere iniciar sesión para esta canción (Ajustes -> Privacidad)"
        } else {
            "No se pudo reproducir ningún stream. Razón: $rawError"
        }

        println("NativeAudioPlayer: ERROR FINAL: $userFriendlyError")
        Result.failure(IllegalStateException(userFriendlyError))
    }

    /**
     * Reproduce una URL de audio directa (MP3, AAC, etc).
     * Usa el PlaybackProxy para añadir cabeceras si es necesario.
     */
    suspend fun playStream(
        url: String,
        userAgent: String? = null,
        referer: String? = null
    ): Result<Unit> = withContext(Dispatchers.Main) {
        val proxyUrl = PlaybackProxy.createProxyUrl(
            url = url,
            userAgent = userAgent ?: YouTubeClient.USER_AGENT_WEB,
            referer = referer ?: ""
        )

        val prepared = preparePlayer(proxyUrl)
        if (prepared) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("No se pudo cargar el stream directo"))
        }
    }

    /**
     * Prepara el MediaPlayer y espera a que esté listo (READY) o falle (ERROR).
     * Devuelve true si se pudo iniciar la reproducción.
     */
    private suspend fun preparePlayer(url: String): Boolean =
        suspendCancellableCoroutine { continuation ->
        Platform.runLater {
            try {
                // Liberar el anterior si existe
                mediaPlayer?.dispose()
                
                val media = Media(url)
                val player = MediaPlayer(media)

                // Configurar callbacks generales (permanentes)
                setupPlayerCallbacks(player)

                // Callbacks específicos para la preparación
                player.setOnReady {
                    mediaPlayer = player
                    onStatusChanged?.invoke(PlaybackStatus.READY)
                    player.play()
                    if (continuation.isActive) continuation.resume(true)
                }

                player.setOnError {
                    val err = player.error?.message ?: "Error desconocido"
                    println("NativeAudioPlayer: Error de JavaFX durante la carga: $err")
                    onError?.invoke(err) // Reportar error de carga
                    player.dispose()
                    if (continuation.isActive) continuation.resume(false)
                }

                // Si la URL es inválida o falla inmediatamente
                if (player.status == MediaPlayer.Status.HALTED) {
                    player.dispose()
                    if (continuation.isActive) continuation.resume(false)
                }
            } catch (e: Exception) {
                println("NativeAudioPlayer: Excepción al crear Media: ${e.message}")
                onError?.invoke(
                    e.message ?: "Error al inicializar reproductor"
                ) // Reportar error de creación
                if (continuation.isActive) continuation.resume(false)
            }
        }

            continuation.invokeOnCancellation {
                Platform.runLater {
                    // Si el usuario cancela (ej: cambia de canción rápido)
                    // Se detiene el proceso de carga
                    mediaPlayer?.dispose() // Asegurarse de liberar recursos si se cancela
                    mediaPlayer = null
                }
            }
        }

    /**
     * Configura los listeners estándar del reproductor.
     */
    private fun setupPlayerCallbacks(player: MediaPlayer) {
        player.setOnPlaying {
            onStatusChanged?.invoke(PlaybackStatus.PLAYING)
            onBuffering?.invoke(false)
        }

        player.setOnPaused {
            onStatusChanged?.invoke(PlaybackStatus.PAUSED)
        }

        player.setOnStopped {
            onStatusChanged?.invoke(PlaybackStatus.STOPPED)
        }

        player.setOnEndOfMedia {
            onStatusChanged?.invoke(PlaybackStatus.ENDED)
        }

        player.setOnStalled {
            onBuffering?.invoke(true)
        }

        player.setOnError {
            val error = player.error?.message ?: "Error desconocido"
            println("NativeAudioPlayer: JAVA FX ERROR durante reproducción: $error")
            onError?.invoke(error)
            onStatusChanged?.invoke(PlaybackStatus.ERROR)
        }

        player.currentTimeProperty().addListener { _, _, newValue ->
            val currentMs = newValue?.toMillis()?.toLong() ?: 0L
            val totalMs = player.totalDuration?.toMillis()?.toLong() ?: 0L
            onTimeChanged?.invoke(currentMs, totalMs)
        }

        // Volumen inicial
        player.volume = volume
    }
    
    /**
     * Pausar reproducción.
     */
    fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
        }
    }
    
    /**
     * Reanudar reproducción.
     */
    fun resume() {
        Platform.runLater {
            mediaPlayer?.play()
        }
    }
    
    /**
     * Alternar entre pausar y reproducir.
     */
    fun togglePlayPause() {
        Platform.runLater {
            val player = mediaPlayer ?: return@runLater
            when (player.status) {
                MediaPlayer.Status.PLAYING -> player.pause()
                MediaPlayer.Status.PAUSED -> player.play()
                else -> { /* Ignorar otros estados */ }
            }
        }
    }
    
    /**
     * Detener reproducción.
     */
    fun stop() {
        Platform.runLater {
            mediaPlayer?.stop()
        }
    }
    
    /**
     * Buscar a una posición específica.
     */
    fun seekTo(positionMs: Long) {
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
        }
    }
    
    /**
     * Establecer volumen (0.0 a 1.0).
     */
    fun setVolume(volume: Double) {
        this.volume = volume.coerceIn(0.0, 1.0)
        Platform.runLater {
            mediaPlayer?.volume = this.volume
        }
    }
    
    /**
     * Obtener volumen actual.
     */
    fun getVolume(): Double = mediaPlayer?.volume ?: 1.0
    
    /**
     * Obtener posición actual en milisegundos.
     */
    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentTime?.toMillis()?.toLong() ?: 0L
    }
    
    /**
     * Obtener duración total en milisegundos.
     */
    fun getDuration(): Long {
        return mediaPlayer?.totalDuration?.toMillis()?.toLong() ?: 0L
    }
    
    /**
     * Verificar si está reproduciendo.
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.status == MediaPlayer.Status.PLAYING
    }
    
    /**
     * Obtener estado actual.
     */
    fun getStatus(): PlaybackStatus {
        return when (mediaPlayer?.status) {
            MediaPlayer.Status.READY -> PlaybackStatus.READY
            MediaPlayer.Status.PLAYING -> PlaybackStatus.PLAYING
            MediaPlayer.Status.PAUSED -> PlaybackStatus.PAUSED
            MediaPlayer.Status.STOPPED -> PlaybackStatus.STOPPED
            MediaPlayer.Status.STALLED -> PlaybackStatus.BUFFERING
            MediaPlayer.Status.HALTED -> PlaybackStatus.ERROR
            MediaPlayer.Status.DISPOSED -> PlaybackStatus.STOPPED
            else -> PlaybackStatus.IDLE
        }
    }
    
    /**
     * Liberar recursos.
     */
    fun release() {
        Platform.runLater {
            mediaPlayer?.dispose()
            mediaPlayer = null
        }
    }
    
    /**
     * Estados de reproducción.
     */
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
