package com.anitail.desktop.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anitail.shared.model.LibraryItem

/**
 * Estado global del reproductor para Desktop.
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

    // Cola de reproducción
    private val _queue = mutableListOf<LibraryItem>()
    val queue: List<LibraryItem> get() = _queue.toList()

    private var currentQueueIndex by mutableStateOf(-1)

    val canSkipPrevious: Boolean
        get() = currentQueueIndex > 0

    val canSkipNext: Boolean
        get() = currentQueueIndex < _queue.size - 1 || repeatMode == RepeatMode.ALL

    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun play(item: LibraryItem) {
        currentItem = item
        isPlaying = true
        position = 0L
        duration = item.durationMs ?: 180_000L // Default 3 minutes si no hay duración

        // Agregar a cola si no existe
        if (_queue.none { it.id == item.id }) {
            _queue.add(item)
        }
        currentQueueIndex = _queue.indexOfFirst { it.id == item.id }
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
            }
        }
    }

    fun clearQueue() {
        _queue.clear()
        currentQueueIndex = -1
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun pause() {
        isPlaying = false
    }

    fun resume() {
        if (currentItem != null) {
            isPlaying = true
        }
    }

    fun stop() {
        isPlaying = false
        position = 0L
        currentItem = null
    }

    fun seekTo(positionMs: Long) {
        position = positionMs.coerceIn(0L, duration)
    }

    fun seekToProgress(progress: Float) {
        position = (duration * progress.coerceIn(0f, 1f)).toLong()
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
            // Si pasaron más de 3 segundos, reiniciar la canción actual
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

    fun updatePosition(positionMs: Long) {
        position = positionMs

        // Auto-avanzar a la siguiente canción cuando termina
        if (position >= duration && duration > 0) {
            when (repeatMode) {
                RepeatMode.ONE -> seekTo(0L)
                RepeatMode.ALL, RepeatMode.OFF -> {
                    if (canSkipNext) {
                        skipToNext()
                    } else {
                        pause()
                    }
                }
            }
        }
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
