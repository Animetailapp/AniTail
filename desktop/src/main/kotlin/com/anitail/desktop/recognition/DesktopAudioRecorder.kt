package com.anitail.desktop.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Grabador de audio para Desktop usando Java Sound API (16kHz, 16-bit, Mono, Little-Endian).
 */
object DesktopAudioRecorder {

    const val SAMPLE_RATE = 16000f
    const val SAMPLE_SIZE_IN_BITS = 16
    const val CHANNELS = 1
    const val SIGNED = true
    const val BIG_ENDIAN = false

    private val format = AudioFormat(
        SAMPLE_RATE,
        SAMPLE_SIZE_IN_BITS,
        CHANNELS,
        SIGNED,
        BIG_ENDIAN
    )

    fun isMicrophoneAvailable(): Boolean {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        return AudioSystem.isLineSupported(info)
    }

    suspend fun recordAudio(durationMs: Long = 7000L): ByteArray = withContext(Dispatchers.IO) {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            throw IllegalStateException("El formato de audio o micrófono no está soportado en este sistema.")
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val startTime = System.currentTimeMillis()

        try {
            while (isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                val read = line.read(buffer, 0, buffer.size)
                if (read > 0) {
                    out.write(buffer, 0, read)
                }
            }
        } finally {
            line.stop()
            line.close()
        }

        out.toByteArray()
    }
}
