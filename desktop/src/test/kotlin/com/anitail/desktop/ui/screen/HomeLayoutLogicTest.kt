package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutLogicTest {
    @Test
    fun usesPrimaryFactorWhenWidthLargeEnough() {
        val maxWidth = 800.dp

        val factor = computeHorizontalLazyGridItemWidthFactor(maxWidth)
        val width = computeHorizontalLazyGridItemWidth(maxWidth)

        assertEquals(0.475f, factor)
        assertEquals(380.dp, width)
    }

    @Test
    fun usesFallbackFactorWhenWidthTooSmall() {
        val maxWidth = 600.dp

        val factor = computeHorizontalLazyGridItemWidthFactor(maxWidth)
        val width = computeHorizontalLazyGridItemWidth(maxWidth)

        assertEquals(0.9f, factor)
        assertEquals(540.dp, width)
    }
}
