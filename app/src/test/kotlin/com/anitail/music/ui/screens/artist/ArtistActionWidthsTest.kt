package com.anitail.music.ui.screens.artist

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistActionWidthsTest {

    private val density = Density(density = 2f, fontScale = 1f)

    @Test
    fun `action widths never exceed available pixel budget when subscribe is shown`() = with(density) {
        for (available in listOf(240, 320, 480, 720, 1080)) {
            val widths = artistActionWidths(
                availableWidth = available,
                subscribeLabelWidth = 140,
                radioLabelWidth = 90,
                showSubscribe = true,
            )
            val totalPx =
                widths.subscribeWidth.roundToPx() +
                    widths.separation.roundToPx() +
                    widths.radioWidth.roundToPx() +
                    widths.shuffleWidth.roundToPx() +
                    widths.playWidth.roundToPx() +
                    widths.spacing.roundToPx() * 4

            assertTrue(
                "Total width ($totalPx px) exceeded available ($available px) for available=$available",
                totalPx <= available + 4, // up to 1px rounding per segment
            )
            assertTrue(widths.subscribeWidth > 0.dp)
            assertTrue(widths.playWidth > 0.dp)
        }
    }

    @Test
    fun `wide viewport labels radio button and allocates generous play button`() = with(density) {
        val widths = artistActionWidths(
            availableWidth = 960,
            subscribeLabelWidth = 140,
            radioLabelWidth = 80,
            showSubscribe = true,
        )

        assertTrue(widths.labelRadio)
        assertTrue(widths.radioWidth > widths.shuffleWidth)
        assertTrue(widths.playWidth > widths.radioWidth)
        assertTrue(widths.separation >= 28.dp)
    }

    @Test
    fun `narrow viewport collapses radio button label`() = with(density) {
        val widths = artistActionWidths(
            availableWidth = 420,
            subscribeLabelWidth = 160,
            radioLabelWidth = 110,
            showSubscribe = true,
        )

        assertFalse(widths.labelRadio)
        assertEquals(widths.shuffleWidth, widths.radioWidth)
    }

    @Test
    fun `hidden subscribe allocates zero width and zero separation`() = with(density) {
        val widths = artistActionWidths(
            availableWidth = 720,
            subscribeLabelWidth = 140,
            radioLabelWidth = 80,
            showSubscribe = false,
        )

        assertEquals(0.dp, widths.subscribeWidth)
        assertEquals(0.dp, widths.separation)
        val totalPx =
            widths.radioWidth.roundToPx() +
                widths.shuffleWidth.roundToPx() +
                widths.playWidth.roundToPx() +
                widths.spacing.roundToPx() * 2
        assertTrue(totalPx <= 720 + 3)
    }
}

