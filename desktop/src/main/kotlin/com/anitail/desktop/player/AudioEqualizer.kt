package com.anitail.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.Equalizer

/**
 * Gestor de Ecualizador de Audio de 10 bandas para Desktop basado en VLCJ.
 */
class AudioEqualizer(private val factory: MediaPlayerFactory?) {

    companion object {
        val BAND_FREQUENCIES = listOf(
            60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f
        )

        val BAND_LABELS = listOf(
            "60 Hz", "170 Hz", "310 Hz", "600 Hz", "1 kHz", "3 kHz", "6 kHz", "12 kHz", "14 kHz", "16 kHz"
        )

        val PRESETS: Map<String, List<Float>> = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
            "Rock" to listOf(4.5f, 3f, -1.5f, -2.5f, -0.5f, 2f, 4.5f, 6f, 6f, 6.5f),
            "Pop" to listOf(-1.5f, 1f, 3f, 4f, 3f, -0.5f, -1.5f, -1.5f, -1f, -1f),
            "Classical" to listOf(4.5f, 3.5f, 3f, 2.5f, -1.5f, -1.5f, 0f, 2f, 3f, 3.5f),
            "Dance" to listOf(6f, 5f, 2f, 0f, 0f, -2f, -3f, -2f, 4f, 5f),
            "Electronic" to listOf(4.5f, 4f, 1.5f, 0f, -2f, 2f, 1.5f, 2.5f, 4.5f, 5f),
            "Vocal Boost" to listOf(-1.5f, -2.5f, -2.5f, 1.5f, 4.5f, 4.5f, 3f, 1.5f, 0f, -1.5f),
            "Acoustic" to listOf(3.5f, 2.5f, 1f, 1f, 1.5f, 1.5f, 3f, 3.5f, 3.5f, 2f),
            "Techno" to listOf(5f, 4f, 0f, -3f, -2.5f, 0f, 5f, 6f, 6f, 5f)
        )
    }

    private var vlcEqualizer: Equalizer? = null
    var isEnabled: Boolean = false
        private set

    var preamp: Float = 0f
        private set

    private val bandGains = FloatArray(10) { 0f }
    var currentPreset: String = "Flat"
        private set

    init {
        try {
            vlcEqualizer = factory?.equalizer()?.newEqualizer()
        } catch (e: Exception) {
            println("AudioEqualizer: No se pudo crear instancia de Equalizer de VLC: ${e.message}")
        }
    }

    fun getBandGain(index: Int): Float {
        return if (index in bandGains.indices) bandGains[index] else 0f
    }

    fun setBandGain(index: Int, gainDb: Float): Equalizer? {
        if (index !in bandGains.indices) return vlcEqualizer
        val clamped = gainDb.coerceIn(-20f, 20f)
        bandGains[index] = clamped
        currentPreset = "Custom"
        vlcEqualizer?.setAmp(index, clamped)
        return if (isEnabled) vlcEqualizer else null
    }

    fun setPreampGain(gainDb: Float): Equalizer? {
        val clamped = gainDb.coerceIn(-20f, 20f)
        preamp = clamped
        vlcEqualizer?.setPreamp(clamped)
        return if (isEnabled) vlcEqualizer else null
    }

    fun applyPreset(presetName: String): Equalizer? {
        val gains = PRESETS[presetName] ?: return vlcEqualizer
        currentPreset = presetName
        gains.forEachIndexed { index, gain ->
            bandGains[index] = gain
            vlcEqualizer?.setAmp(index, gain)
        }
        return if (isEnabled) vlcEqualizer else null
    }

    fun setEnabled(enabled: Boolean): Equalizer? {
        isEnabled = enabled
        return if (enabled) vlcEqualizer else null
    }

    fun getEqualizerForPlayer(): Equalizer? = if (isEnabled) vlcEqualizer else null
}
