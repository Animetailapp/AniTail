package com.anitail.desktop.ui.component

import kotlin.test.Test
import kotlin.test.assertTrue

class MiniPlayerRingGeometryTest {
    @Test
    fun `playing squircle clears wavy ring troughs with margin`() {
        val geometry = calculatePlayPauseRingGeometry(
            ringDiameterDp = 54f,
            strokeWidthDp = 2.6f,
            maxWaveAmplitudeDp = 1.4f,
            buttonSizeDp = 34f,
            buttonCornerDp = 11f,
        )

        assertTrue(
            geometry.outerCrestRadiusDp <= 27.001f,
            "Wave crest (${geometry.outerCrestRadiusDp}dp) should fit within 54dp ring",
        )
        assertTrue(
            geometry.waveTroughClearanceDp >= 2.0f,
            "Playing squircle clearance (${geometry.waveTroughClearanceDp}dp) should be >= 2.0dp",
        )
    }

    @Test
    fun `paused circle clears flat track with generous margin`() {
        val geometry = calculatePlayPauseRingGeometry(
            ringDiameterDp = 54f,
            strokeWidthDp = 2.6f,
            maxWaveAmplitudeDp = 1.4f,
            buttonSizeDp = 36f,
            buttonCornerDp = 18f,
        )

        assertTrue(
            geometry.flatTrackClearanceDp >= 4.5f,
            "Paused circle clearance (${geometry.flatTrackClearanceDp}dp) should be >= 4.5dp",
        )
    }
}

