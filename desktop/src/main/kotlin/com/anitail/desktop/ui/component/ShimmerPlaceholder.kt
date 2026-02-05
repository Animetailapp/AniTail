package com.anitail.desktop.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Efecto shimmer animado para placeholders de carga.
 * Similar al shimmer de la app Android.
 */
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim),
    )
}

/**
 * Box con efecto shimmer.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush()),
    )
}

/**
 * Placeholder para un item de lista (como QuickPickRowItem).
 */
@Composable
fun ShimmerListItem(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
            )
        }
    }
}

/**
 * Placeholder para un item de grid (como HomeItemCard).
 */
@Composable
fun ShimmerGridItem(
    size: Dp = 128.dp,
    isCircle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = if (isCircle) CircleShape else RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .width(size)
            .padding(12.dp),
    ) {
        ShimmerBox(
            modifier = Modifier.size(size),
            shape = shape,
        )
        Spacer(modifier = Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp),
        )
    }
}

/**
 * Fila horizontal de placeholders shimmer para secciones.
 */
@Composable
fun ShimmerSectionRow(
    itemCount: Int = 6,
    itemSize: Dp = 128.dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(itemCount) {
            ShimmerGridItem(size = itemSize)
        }
    }
}

/**
 * Grid de placeholders para Quick Picks.
 */
@Composable
fun ShimmerQuickPicksGrid(
    rows: Int = 4,
    columns: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(columns) {
                    ShimmerListItem(
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Placeholder para chips de filtro.
 */
@Composable
fun ShimmerChipsRow(
    chipCount: Int = 5,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 12.dp),
    ) {
        repeat(chipCount) { index ->
            ShimmerBox(
                modifier = Modifier
                    .width((60 + index * 10).dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}
