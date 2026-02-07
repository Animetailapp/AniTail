package com.anitail.desktop.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.anitail.desktop.storage.SliderStyle
import kotlin.math.PI
import kotlin.math.sin

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
) {
    var dragInProgress by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (style == SliderStyle.SLIM) 16.dp else 24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragInProgress = true },
                    onDragEnd = {
                        dragInProgress = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragInProgress = false
                        onValueChangeFinished()
                    },
                    onDrag = { change, _ ->
                        val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(if (style == SliderStyle.SLIM) 16.dp else 24.dp)) {
            val centerY = size.height / 2f
            val trackHeight = when (style) {
                SliderStyle.SLIM -> 2.dp.toPx()
                SliderStyle.SQUIGGLY -> 3.dp.toPx()
                SliderStyle.DEFAULT -> 4.dp.toPx()
            }
            val activeWidth = size.width * value.coerceIn(0f, 1f)

            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )

            if (style == SliderStyle.SQUIGGLY) {
                val amplitude = if (isPlaying) 6.dp.toPx() else 2.dp.toPx()
                val wavelength = 28.dp.toPx()
                val step = 6.dp.toPx()
                val path = Path()
                var x = 0f
                path.moveTo(0f, centerY)
                while (x <= activeWidth) {
                    val y = centerY + sin((x / wavelength) * (2 * PI)).toFloat() * amplitude
                    path.lineTo(x, y)
                    x += step
                }
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(width = trackHeight, cap = StrokeCap.Round),
                )
            } else {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeWidth, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }

            if (style != SliderStyle.SLIM) {
                val thumbRadius = if (dragInProgress) 7.dp.toPx() else 6.dp.toPx()
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = Offset(activeWidth, centerY),
                )
            }
        }
    }
}
