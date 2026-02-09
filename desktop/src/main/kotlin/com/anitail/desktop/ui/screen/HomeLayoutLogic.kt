package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun computeHorizontalLazyGridItemWidthFactor(maxWidth: Dp): Float {
    return if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
}

fun computeHorizontalLazyGridItemWidth(maxWidth: Dp): Dp {
    return maxWidth * computeHorizontalLazyGridItemWidthFactor(maxWidth)
}
