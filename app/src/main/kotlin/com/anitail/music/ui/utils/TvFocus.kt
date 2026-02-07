package com.anitail.music.ui.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.music.utils.DeviceUtils

val LocalIsTelevision = staticCompositionLocalOf { false }

@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { DeviceUtils.isTelevisionDevice(context) }
}

@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedScale: Float = 1.03f,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = LocalIndication.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier = composed {
    this
        .tvFocusable(
            enabled = enabled,
            shape = shape,
            focusedBorderWidth = focusedBorderWidth,
            focusedScale = focusedScale,
            interactionSource = interactionSource,
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedScale: Float = 1.03f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier = composed {
    val isTelevision = LocalIsTelevision.current
    if (!isTelevision) {
        return@composed this
    }

    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val targetScale = if (isFocused) focusedScale else 1f
    val scale by animateFloatAsState(targetValue = targetScale, label = "tvFocusScale")

    this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .border(
            width = if (isFocused) focusedBorderWidth else 0.dp,
            color = if (isFocused) focusedColor else LocalContentColor.current.copy(alpha = 0f),
            shape = shape,
        )
        .focusable(enabled = enabled, interactionSource = interactionSource)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvCombinedClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedBorderWidth: Dp = 2.dp,
    focusedScale: Float = 1.03f,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = LocalIndication.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    this
        .tvFocusable(
            enabled = enabled,
            shape = shape,
            focusedBorderWidth = focusedBorderWidth,
            focusedScale = focusedScale,
            interactionSource = interactionSource,
        )
        .combinedClickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            indication = indication,
            interactionSource = interactionSource,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}
