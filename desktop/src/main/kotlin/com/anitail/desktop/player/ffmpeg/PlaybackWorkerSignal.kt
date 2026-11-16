package com.anitail.desktop.player.ffmpeg

internal class PlaybackWorkerSignal {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val monitor = Object()

    fun awaitWhile(predicate: () -> Boolean) {
        synchronized(monitor) {
            while (predicate()) monitor.wait()
        }
    }

    fun signal() {
        synchronized(monitor) { monitor.notifyAll() }
    }
}

