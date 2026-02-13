package com.anitail.desktop.player

import kotlin.math.pow

object PlaybackRateCalculator {
    private const val MIN_RATE = 0.25
    private const val MAX_RATE = 4.0

    fun toRate(tempo: Float, pitchSemitone: Int): Double {
        val tempoValue = tempo.toDouble().coerceIn(0.5, 2.0)
        val pitchFactor = 2.0.pow(pitchSemitone.toDouble() / 12.0)
        return (tempoValue * pitchFactor).coerceIn(MIN_RATE, MAX_RATE)
    }
}
