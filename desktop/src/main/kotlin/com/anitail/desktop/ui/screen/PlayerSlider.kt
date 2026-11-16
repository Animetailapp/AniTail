package com.anitail.desktop.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.anitail.desktop.storage.SliderStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material 3 Expressive playback progress slider supporting `DEFAULT`, `SQUIGGLY`, and `SLIM`
 * styles with immediate click-and-scrub pointer tracking and hover/drag micro-interactions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    style: SliderStyle,
    activeColor: Color,
    inactiveColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bufferedFraction: Float = 0f,
) {
    val clampedValue = value.coerceIn(0f, 1f)
    val clampedBuffer = bufferedFraction.coerceIn(clampedValue, 1f)
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(1) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val wavePhaseTransition = rememberInfiniteTransition(label = "squiggly_wave_phase")
    val infinitePhase by wavePhaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "squiggly_phase",
    )

    val waveAmplitudeScale by animateFloatAsState(
        targetValue = if (style == SliderStyle.SQUIGGLY && isPlaying && !isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "squiggly_amplitude",
    )

    val thumbHeight by animateDpAsState(
        targetValue = when {
            isDragging -> 24.dp
            isHovered -> 20.dp
            style == SliderStyle.SLIM -> 10.dp
            else -> 18.dp
        },
        animationSpec = spring(),
        label = "slider_thumb_height",
    )

    val slimTrackHeight by animateDpAsState(
        targetValue = when {
            isDragging -> 8.dp
            isHovered -> 6.dp
            else -> 4.dp
        },
        animationSpec = spring(),
        label = "slim_track_height",
    )

    val phaseHolder = remember { object { var value = 0f } }
    val activePhase = if (isPlaying && !isDragging) {
        phaseHolder.value = infinitePhase
        infinitePhase
    } else {
        phaseHolder.value
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { size ->
                widthPx = size.width.coerceAtLeast(1)
            }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    val startFraction = (down.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                    currentOnValueChange(startFraction)
                    drag(down.id) { change ->
                        change.consume()
                        val updatedFraction = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        currentOnValueChange(updatedFraction)
                    }
                    isDragging = false
                    currentOnFinished()
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            val totalWidth = size.width
            if (totalWidth <= 0f) return@Canvas
            val cy = size.height * 0.5f
            val horizontalInset = 4.dp.toPx()
            val trackStart = horizontalInset
            val trackEnd = (totalWidth - horizontalInset).coerceAtLeast(trackStart + 1f)
            val usableWidth = trackEnd - trackStart
            val thumbX = trackStart + usableWidth * clampedValue
            val bufferX = trackStart + usableWidth * clampedBuffer

            when (style) {
                SliderStyle.SQUIGGLY -> {
                    val strokePx = 3.dp.toPx()
                    val thumbWidthPx = if (isDragging || isHovered) 4.5.dp.toPx() else 3.5.dp.toPx()
                    val thumbHeightPx = thumbHeight.toPx()
                    val gapPx = 5.dp.toPx()
                    val activeEnd = (thumbX - gapPx).coerceAtLeast(trackStart)
                    val inactiveStart = (thumbX + gapPx).coerceAtMost(trackEnd)

                    if (clampedBuffer > clampedValue && inactiveStart < bufferX) {
                        drawLine(
                            color = activeColor.copy(alpha = 0.25f),
                            start = Offset(inactiveStart, cy),
                            end = Offset(bufferX, cy),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    }
                    if (inactiveStart < trackEnd) {
                        drawLine(
                            color = inactiveColor.copy(alpha = 0.34f),
                            start = Offset(inactiveStart, cy),
                            end = Offset(trackEnd, cy),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    }

                    if (activeEnd > trackStart) {
                        val amplitudePx = 2.8.dp.toPx() * waveAmplitudeScale
                        if (amplitudePx <= 0.1f) {
                            drawLine(
                                color = activeColor,
                                start = Offset(trackStart, cy),
                                end = Offset(activeEnd, cy),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round,
                            )
                        } else {
                            val wavelengthPx = 22.dp.toPx()
                            val stepPx = 2.dp.toPx().coerceAtLeast(1f)
                            val path = Path()
                            var x = trackStart
                            var first = true
                            val fadeZonePx = 12.dp.toPx()
                            while (x <= activeEnd) {
                                val distFromStart = (x - trackStart).coerceIn(0f, fadeZonePx) / fadeZonePx
                                val distFromEnd = (activeEnd - x).coerceIn(0f, fadeZonePx) / fadeZonePx
                                val envelope = minOf(distFromStart, distFromEnd)
                                val angle = ((x - trackStart) / wavelengthPx) * (2.0 * PI).toFloat() - activePhase
                                val y = cy + sin(angle) * amplitudePx * envelope
                                if (first) {
                                    path.moveTo(x, y)
                                    first = false
                                } else {
                                    path.lineTo(x, y)
                                }
                                x += stepPx
                            }
                            path.lineTo(activeEnd, cy)
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }

                    // M3 Expressive vertical capsule thumb
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(thumbX - thumbWidthPx * 0.5f, cy - thumbHeightPx * 0.5f),
                        size = Size(thumbWidthPx, thumbHeightPx),
                        cornerRadius = CornerRadius(thumbWidthPx * 0.5f, thumbWidthPx * 0.5f),
                    )
                }

                SliderStyle.SLIM -> {
                    val barHeight = slimTrackHeight.toPx()
                    val radius = barHeight * 0.5f
                    drawRoundRect(
                        color = inactiveColor.copy(alpha = 0.28f),
                        topLeft = Offset(trackStart, cy - radius),
                        size = Size(usableWidth, barHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    if (clampedBuffer > clampedValue) {
                        drawRoundRect(
                            color = activeColor.copy(alpha = 0.28f),
                            topLeft = Offset(trackStart, cy - radius),
                            size = Size((bufferX - trackStart).coerceAtLeast(0f), barHeight),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    val activeW = (thumbX - trackStart).coerceAtLeast(0f)
                    if (activeW > 0f) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(trackStart, cy - radius),
                            size = Size(activeW, barHeight),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    if (isHovered || isDragging) {
                        val dotRadius = (barHeight * 0.9f).coerceAtLeast(4.5.dp.toPx())
                        drawCircle(
                            color = activeColor,
                            radius = dotRadius,
                            center = Offset(thumbX, cy),
                        )
                    }
                }

                SliderStyle.DEFAULT -> {
                    val trackHeight = if (isHovered || isDragging) 6.dp.toPx() else 5.dp.toPx()
                    val halfTrack = trackHeight * 0.5f
                    val gapPx = 5.dp.toPx()
                    val thumbWidthPx = if (isDragging || isHovered) 5.dp.toPx() else 4.dp.toPx()
                    val thumbHeightPx = thumbHeight.toPx()

                    val activeEnd = (thumbX - gapPx).coerceAtLeast(trackStart)
                    val inactiveStart = (thumbX + gapPx).coerceAtMost(trackEnd)

                    if (clampedBuffer > clampedValue && inactiveStart < bufferX) {
                        drawRoundRect(
                            color = activeColor.copy(alpha = 0.24f),
                            topLeft = Offset(inactiveStart, cy - halfTrack),
                            size = Size(bufferX - inactiveStart, trackHeight),
                            cornerRadius = CornerRadius(halfTrack, halfTrack),
                        )
                    }
                    if (inactiveStart < trackEnd) {
                        drawRoundRect(
                            color = inactiveColor.copy(alpha = 0.34f),
                            topLeft = Offset(inactiveStart, cy - halfTrack),
                            size = Size(trackEnd - inactiveStart, trackHeight),
                            cornerRadius = CornerRadius(halfTrack, halfTrack),
                        )
                    }
                    if (activeEnd > trackStart) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(trackStart, cy - halfTrack),
                            size = Size(activeEnd - trackStart, trackHeight),
                            cornerRadius = CornerRadius(halfTrack, halfTrack),
                        )
                    }

                    // Crisp M3 Expressive vertical capsule thumb + stop dot on inactive end
                    drawCircle(
                        color = activeColor.copy(alpha = 0.65f),
                        radius = 2.dp.toPx(),
                        center = Offset(trackEnd - 2.dp.toPx(), cy),
                    )
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(thumbX - thumbWidthPx * 0.5f, cy - thumbHeightPx * 0.5f),
                        size = Size(thumbWidthPx, thumbHeightPx),
                        cornerRadius = CornerRadius(thumbWidthPx * 0.5f, thumbWidthPx * 0.5f),
                    )
                }
            }
        }
    }
}
