package com.anitail.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Estado global del reproductor para Desktop.
 * Ahora usa NativeAudioPlayer para reproducción real de audio.
 */
class PlayerState {
    var currentItem by mutableStateOf<LibraryItem?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var position by mutableStateOf(0L)
        private set

    var duration by mutableStateOf(0L)
        private set

    var shuffleEnabled by mutableStateOf(false)

    var repeatMode by mutableStateOf(RepeatMode.OFF)
        private set

    var isBuffering by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var volume by mutableStateOf(1.0)
        private set

    // Cola de reproducción
    private val _queue = mutableStateListOf<LibraryItem>()
    val queue: List<LibraryItem> get() = _queue

    var currentQueueIndex by mutableStateOf(-1)
        private set

    // Reproductor nativo de audio
    private val nativePlayer = NativeAudioPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        setupNativePlayerCallbacks()
    }

    private fun setupNativePlayerCallbacks() {
        nativePlayer.onStatusChanged = { status ->
            when (status) {
                NativeAudioPlayer.PlaybackStatus.PLAYING -> {
                    isPlaying = true
                    isBuffering = false
                    errorMessage = null
                }
                NativeAudioPlayer.PlaybackStatus.PAUSED -> {
                    isPlaying = false
                }
                NativeAudioPlayer.PlaybackStatus.BUFFERING -> {
                    isBuffering = true
                }
                NativeAudioPlayer.PlaybackStatus.ENDED -> {
                    isPlaying = false
                    handlePlaybackEnded()
                }
                NativeAudioPlayer.PlaybackStatus.ERROR -> {
                    isPlaying = false
                    isBuffering = false
                }
                NativeAudioPlayer.PlaybackStatus.STOPPED -> {
                    isPlaying = false
                }
                else -> { /* Ignorar otros estados */ }
            }
        }

        nativePlayer.onTimeChanged = { currentMs, totalMs ->
            position = currentMs
            if (totalMs > 0) {
                duration = totalMs
            }
        }

        nativePlayer.onError = { error ->
            errorMessage = error
            isPlaying = false
            isBuffering = false
        }

        nativePlayer.onBuffering = { buffering ->
            isBuffering = buffering
        }
    }

    private fun handlePlaybackEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                nativePlayer.resume()
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                if (canSkipNext) {
                    skipToNext()
                } else {
                    position = 0L
                }
            }
        }
    }

    val canSkipPrevious: Boolean
        get() = currentQueueIndex > 0

    val canSkipNext: Boolean
        get() = currentQueueIndex < _queue.size - 1 || repeatMode == RepeatMode.ALL

    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun play(item: LibraryItem) {
        currentItem = item
        position = 0L
        duration = item.durationMs ?: 180_000L
        isBuffering = true
        errorMessage = null

        // Agregar a cola si no existe
        if (_queue.none { it.id == item.id }) {
            _queue.add(item)
        }
        currentQueueIndex = _queue.indexOfFirst { it.id == item.id }

        // Extraer videoId para intentar reproducción nativa vía extracción de NativeAudioPlayer
        val videoId = extractVideoId(item.playbackUrl)
        println("Anitail DEBUG: Play called for item: ${item.title}, videoId: $videoId")

        scope.launch {
            if (videoId != null) {
                isBuffering = true
                nativePlayer.play(videoId).onFailure { handlePlaybackError(it) }
            } else {
                nativePlayer.playStream(item.playbackUrl).onFailure { handlePlaybackError(it) }
            }
        }
    }

    private fun handlePlaybackError(error: Throwable) {
        errorMessage = error.message ?: "Error al reproducir"
        isPlaying = false
        isBuffering = false
        println("Anitail ERROR: Playback failed: ${error.message}")
    }

    private fun extractVideoId(url: String): String? {
        // Patrones comunes de YouTube
        val patterns = listOf(
            Regex("watch\\?v=([^&]+)"),
            Regex("youtu.be/([^?]+)"),
            Regex("/watch/([^?]+)"),
            Regex("v=([^&]+)"),
        )
        
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        
        // Si la URL parece ser solo un ID de video
        if (url.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return url
        }
        
        return null
    }

    fun playQueue(items: List<LibraryItem>, startIndex: Int = 0) {
        _queue.clear()
        _queue.addAll(items)
        if (items.isNotEmpty() && startIndex in items.indices) {
            currentQueueIndex = startIndex
            play(items[startIndex])
        }
    }

    fun addToQueue(item: LibraryItem, playNext: Boolean = false) {
        if (_queue.none { it.id == item.id }) {
            if (playNext) {
                val insertIndex = (currentQueueIndex + 1).coerceAtMost(_queue.size)
                _queue.add(insertIndex, item)
            } else {
                _queue.add(item)
            }
        }
    }

    fun removeFromQueue(item: LibraryItem) {
        val index = _queue.indexOfFirst { it.id == item.id }
        if (index != -1) {
            _queue.removeAt(index)
            if (index < currentQueueIndex) {
                currentQueueIndex--
            } else if (index == currentQueueIndex && _queue.isNotEmpty()) {
                // Si borramos el actual, reproducimos el siguiente o paramos si es el último
                if (currentQueueIndex < _queue.size) {
                    play(_queue[currentQueueIndex])
                } else {
                    stop()
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in _queue.indices) {
            _queue.removeAt(index)
            if (index < currentQueueIndex) {
                currentQueueIndex--
            } else if (index == currentQueueIndex && _queue.isNotEmpty()) {
                if (currentQueueIndex < _queue.size) {
                    play(_queue[currentQueueIndex])
                } else {
                    stop()
                }
            }
        }
    }

    fun clearQueue() {
        _queue.clear()
        currentQueueIndex = -1
    }

    fun insertIntoQueue(index: Int, item: LibraryItem) {
        val safeIndex = index.coerceIn(0, _queue.size)
        if (_queue.any { it.id == item.id }) {
            return
        }
        _queue.add(safeIndex, item)
        if (currentQueueIndex >= safeIndex && currentQueueIndex != -1) {
            currentQueueIndex++
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.indices || toIndex !in _queue.indices || fromIndex == toIndex) {
            return
        }

        val item = _queue.removeAt(fromIndex)
        _queue.add(toIndex, item)

        currentQueueIndex = when {
            currentQueueIndex == fromIndex -> toIndex
            fromIndex < currentQueueIndex && toIndex >= currentQueueIndex -> currentQueueIndex - 1
            fromIndex > currentQueueIndex && toIndex <= currentQueueIndex -> currentQueueIndex + 1
            else -> currentQueueIndex
        }
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
        nativePlayer.togglePlayPause()
    }

    fun pause() {
        isPlaying = false
        nativePlayer.pause()
    }

    fun resume() {
        if (currentItem != null) {
            isPlaying = true
            nativePlayer.resume()
        }
    }

    fun stop() {
        isPlaying = false
        position = 0L
        currentItem = null
        nativePlayer.stop()
    }

    fun seekTo(positionMs: Long) {
        position = positionMs.coerceIn(0L, duration)
        nativePlayer.seekTo(position)
    }

    fun seekToProgress(progress: Float) {
        val newPosition = (duration * progress.coerceIn(0f, 1f)).toLong()
        seekTo(newPosition)
    }

    fun skipToNext() {
        if (canSkipNext) {
            val nextIndex = if (currentQueueIndex >= _queue.size - 1 && repeatMode == RepeatMode.ALL) {
                0
            } else {
                currentQueueIndex + 1
            }
            if (nextIndex in _queue.indices) {
                currentQueueIndex = nextIndex
                play(_queue[nextIndex])
            }
        }
    }

    fun skipToPrevious() {
        if (position > 3000L) {
            seekTo(0L)
        } else if (canSkipPrevious) {
            currentQueueIndex--
            play(_queue[currentQueueIndex])
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled && _queue.size > 1) {
            val current = currentItem
            val remaining = _queue.filter { it.id != current?.id }.shuffled()
            _queue.clear()
            if (current != null) {
                _queue.add(current)
                currentQueueIndex = 0
            }
            _queue.addAll(remaining)
        }
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun updateVolume(newVolume: Double) {
        volume = newVolume.coerceIn(0.0, 1.0)
        nativePlayer.setVolume(volume)
    }

    fun updatePosition(positionMs: Long) {
        // Este método ahora es principalmente para compatibilidad
        // El reproductor nativo actualiza la posición automáticamente
        position = positionMs

        if (position >= duration && duration > 0) {
            handlePlaybackEnded()
        }
    }

    fun release() {
        nativePlayer.release()
    }
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Composable
fun rememberPlayerState(): PlayerState {
    return remember { PlayerState() }
}
