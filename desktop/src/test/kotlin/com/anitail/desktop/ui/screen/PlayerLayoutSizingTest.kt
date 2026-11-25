package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.component.VideoShaderEffects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerLayoutSizingTest {
    @Test
    fun `fullscreenPlayerScale scales cleanly with window bounds and clamps negative widths`() {
        val collapsed = fullscreenPlayerScale(availableWidth = 32.dp, availableHeight = 600.dp)
        assertEquals(0f, collapsed)

        val compact = fullscreenPlayerScale(availableWidth = 480.dp, availableHeight = 600.dp)
        val medium = fullscreenPlayerScale(availableWidth = 1056.dp, availableHeight = 626.dp)
        val large = fullscreenPlayerScale(availableWidth = 1850.dp, availableHeight = 960.dp)

        assertTrue(compact > 0f, "Expected positive scale for compact bounds")
        assertTrue(medium >= compact, "Expected medium window scale >= compact scale")
        assertTrue(large > medium, "Expected large monitor scale > medium scale")
        assertEquals(960f / 1030f, large, 0.001f)
    }

    @Test
    fun `fullscreenArtworkSize expands with window height and fits within remaining pane bounds`() {
        val smallWindow = fullscreenArtworkSize(
            availableWidth = 900,
            availableHeight = 520,
            controlsHeight = 240,
            aspectRatio = 1f,
            preferredHeight = 500,
        )
        assertEquals(IntSize(280, 280), smallWindow)

        val largeWindow = fullscreenArtworkSize(
            availableWidth = 1600,
            availableHeight = 960,
            controlsHeight = 240,
            aspectRatio = 1f,
            preferredHeight = 560,
        )
        assertEquals(IntSize(560, 560), largeWindow)
        assertTrue(largeWindow.width > smallWindow.width)
        assertTrue(largeWindow.height <= 960 - 240)
    }

    @Test
    fun `fullscreenArtworkSize preserves 16 by 9 aspect ratio within available width and height`() {
        val ratio16x9 = 16f / 9f
        val widePane = fullscreenArtworkSize(
            availableWidth = 1056,
            availableHeight = 600,
            controlsHeight = 240,
            aspectRatio = ratio16x9,
            preferredHeight = 360,
        )
        assertEquals(IntSize(640, 360), widePane)

        val narrowLeftPane = fullscreenArtworkSize(
            availableWidth = 491,
            availableHeight = 626,
            controlsHeight = 240,
            aspectRatio = ratio16x9,
            preferredHeight = 380,
        )
        assertEquals(491, narrowLeftPane.width)
        assertTrue(narrowLeftPane.height in 275..277, "Expected 16:9 height around 276, got ${narrowLeftPane.height}")
    }

    @Test
    fun `fullscreenArtworkSize returns zero when no vertical room remains after controls`() {
        val zeroHeight = fullscreenArtworkSize(
            availableWidth = 800,
            availableHeight = 220,
            controlsHeight = 240,
            aspectRatio = 1f,
            preferredHeight = 360,
        )
        assertEquals(IntSize.Zero, zeroHeight)
    }

    @Test
    fun `VideoShaderEffects compiles SkSL YUV420P shader cleanly`() {
        assertTrue(
            VideoShaderEffects.isYuvShaderAvailable,
            "Expected YUV420P SkSL shader to compile without Skia syntax errors",
        )
    }
}

