package com.anitail.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackRateCalculatorTest {
    @Test
    fun computesRateFromTempoAndPitch() {
        assertEquals(1.0, PlaybackRateCalculator.toRate(1.0f, 0), 1e-6)
        assertEquals(2.0, PlaybackRateCalculator.toRate(1.0f, 12), 1e-6)
        assertEquals(0.5, PlaybackRateCalculator.toRate(0.5f, 0), 1e-6)
    }

    @Test
    fun clampsToSupportedRange() {
        assertEquals(0.25, PlaybackRateCalculator.toRate(0.5f, -12), 1e-6)
        assertEquals(4.0, PlaybackRateCalculator.toRate(2.0f, 12), 1e-6)
    }
}
