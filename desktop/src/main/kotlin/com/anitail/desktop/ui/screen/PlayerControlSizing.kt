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
    val playButtonHeight = (maxWidth / 6f).coerceAtMost(60.dp)
    val playButtonWidth = playButtonHeight * 1.1f
    val sideButtonHeight = playButtonHeight * 0.7f
    val sideButtonWidth = (sideButtonHeight * 1.3f).coerceAtMost(54.dp)
    val playIconSize = playButtonHeight * 0.7f
    val sideIconSize = sideButtonHeight * 0.762f

    return PlayerControlSizes(
        playButtonWidth = playButtonWidth,
        playButtonHeight = playButtonHeight,
        sideButtonWidth = sideButtonWidth,
        sideButtonHeight = sideButtonHeight,
        playIconSize = playIconSize,
        sideIconSize = sideIconSize,
    )
}
