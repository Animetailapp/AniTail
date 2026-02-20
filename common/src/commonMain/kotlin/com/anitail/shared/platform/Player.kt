package com.anitail.shared.platform

/**
 * Interface unificada para el reproductor de medios.
 */
interface Player {
    val isPlaying: Boolean
    val position: Long
    val duration: Long
    val isBuffering: Boolean

    fun play(url: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Double)
    fun release()

    // Callbacks
    var onStatusChanged: ((PlayerStatus) -> Unit)?
    var onTimeChanged: ((currentMs: Long, totalMs: Long) -> Unit)?
    var onError: ((String) -> Unit)?
}

enum class PlayerStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    ENDED,
    ERROR
}
