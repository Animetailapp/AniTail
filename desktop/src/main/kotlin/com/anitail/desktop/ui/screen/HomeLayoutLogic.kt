package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout constants for horizontal lazy grid item sizing.
 *
 * [HORIZONTAL_LAZY_GRID_PREFERRED_ITEM_WIDTH_FRACTION] is used when the computed item width
 * would be at least [HORIZONTAL_LAZY_GRID_MIN_ITEM_WIDTH]. For narrower layouts, the
 * [HORIZONTAL_LAZY_GRID_FALLBACK_ITEM_WIDTH_FRACTION] is used to keep items readable.
 */
private const val HORIZONTAL_LAZY_GRID_PREFERRED_ITEM_WIDTH_FRACTION: Float = 0.475f
private val HORIZONTAL_LAZY_GRID_MIN_ITEM_WIDTH = 320.dp
private const val HORIZONTAL_LAZY_GRID_FALLBACK_ITEM_WIDTH_FRACTION: Float = 0.9f

fun computeHorizontalLazyGridItemWidthFactor(maxWidth: Dp): Float {
    return if (maxWidth * HORIZONTAL_LAZY_GRID_PREFERRED_ITEM_WIDTH_FRACTION >= HORIZONTAL_LAZY_GRID_MIN_ITEM_WIDTH) {
        HORIZONTAL_LAZY_GRID_PREFERRED_ITEM_WIDTH_FRACTION
    } else {
        HORIZONTAL_LAZY_GRID_FALLBACK_ITEM_WIDTH_FRACTION
    }
}

fun computeHorizontalLazyGridItemWidth(maxWidth: Dp): Dp {
    return maxWidth * computeHorizontalLazyGridItemWidthFactor(maxWidth)
}
