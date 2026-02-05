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
import kotlin.coroutines.resume

/**
 * Servicio de reproducción de audio nativo usando JavaFX Media.
 * Reemplaza el WebView con reproducción directa de streams de YouTube.
 */
class NativeAudioPlayer {
    
    private var mediaPlayer: MediaPlayer? = null
    
    // Callbacks de estado
    var onStatusChanged: ((PlaybackStatus) -> Unit)? = null
    var onTimeChanged: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null
    
    init {
        JavaFxManager.ensureInitialized()
    }
    
    /**
     * Reproduce un video de YouTube por su ID.
     * Obtiene la URL de streaming de audio y la reproduce nativamente.
     */
    suspend fun play(videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Obtener signature timestamp para descifrar URLs
            val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
            
            // Obtener player response con datos de streaming
            val playerResponse = YouTube.player(
                videoId = videoId,
                playlistId = null,
                client = YouTubeClient.WEB_REMIX,
                signatureTimestamp = signatureTimestamp,
            ).getOrThrow()
            
            // Verificar que el video es reproducible
            if (playerResponse.playabilityStatus.status != "OK") {
                throw IllegalStateException(
                    "Video no reproducible: ${playerResponse.playabilityStatus.reason ?: "desconocido"}"
                )
            }
            
            // Obtener el mejor formato de audio
            val audioFormats = playerResponse.streamingData?.adaptiveFormats
                ?.filter { it.isAudio }
                ?.sortedByDescending { it.bitrate }
                ?: throw IllegalStateException("No hay formatos de audio disponibles")
            
            val bestFormat = audioFormats.firstOrNull()
                ?: throw IllegalStateException("No se encontró formato de audio")
            
            // Obtener URL descifrada
            val streamUrl = NewPipeUtils.getStreamUrl(bestFormat, videoId).getOrThrow()
            
            // Reproducir el audio
            playStreamUrl(streamUrl)
        }
    }
    
    /**
     * Reproduce una URL de streaming directamente.
     */
    private suspend fun playStreamUrl(url: String) = suspendCancellableCoroutine { continuation ->
        Platform.runLater {
            try {
                // Limpiar reproductor anterior
                mediaPlayer?.dispose()
                
                // Crear nuevo Media y MediaPlayer
                val media = Media(url)
                val player = MediaPlayer(media)
                
                // Configurar callbacks
                player.setOnReady {
                    onStatusChanged?.invoke(PlaybackStatus.READY)
                    player.play()
                }
                
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
                    onError?.invoke(error)
                    onStatusChanged?.invoke(PlaybackStatus.ERROR)
                }
                
                player.currentTimeProperty().addListener { _, _, newValue ->
                    val currentMs = newValue?.toMillis()?.toLong() ?: 0L
                    val totalMs = player.totalDuration?.toMillis()?.toLong() ?: 0L
                    onTimeChanged?.invoke(currentMs, totalMs)
                }
                
                mediaPlayer = player
                
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Unit) // Resume pero reportar error
                }
                onError?.invoke(e.message ?: "Error al inicializar reproductor")
            }
        }
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
        Platform.runLater {
            mediaPlayer?.volume = volume.coerceIn(0.0, 1.0)
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
