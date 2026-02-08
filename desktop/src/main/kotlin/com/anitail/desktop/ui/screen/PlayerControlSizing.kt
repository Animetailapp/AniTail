package com.anitail.desktop.ui.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class PlayerControlSizes(
    val playButtonWidth: Dp,
    val playButtonHeight: Dp,
    val sideButtonWidth: Dp,
    val sideButtonHeight: Dp,
    val playIconSize: Dp,
    val sideIconSize: Dp,
)

internal fun calculatePlayerControlSizes(@Suppress("UNUSED_PARAMETER") maxWidth: Dp): PlayerControlSizes {
    val referenceWidth = maxWidth.coerceAtMost(360.dp)
    val playButtonHeight = referenceWidth / 6f
    val playButtonWidth = playButtonHeight * 1.1f
    val sideButtonHeight = playButtonHeight * 0.7f
    val sideButtonWidth = sideButtonHeight * 1.3f
    val playIconSize = 42.dp
    val sideIconSize = 32.dp

    return PlayerControlSizes(
        playButtonWidth = playButtonWidth,
        playButtonHeight = playButtonHeight,
        sideButtonWidth = sideButtonWidth,
        sideButtonHeight = sideButtonHeight,
        playIconSize = playIconSize,
        sideIconSize = sideIconSize,
    )
}
