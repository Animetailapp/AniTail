package com.anitail.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val expressiveAvatarPolygons by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Sunny,
        MaterialShapes.Flower,
        MaterialShapes.PuffyDiamond,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberExpressiveAvatarShape(key: String?): Shape {
    val index = remember(key) {
        if (key.isNullOrBlank()) 0 else abs(key.hashCode()) % expressiveAvatarPolygons.size
    }
    return expressiveAvatarPolygons[index].toShape()
}

@Composable
fun ExpressiveFramedAvatar(
    url: String?,
    contentDescription: String?,
    shapeKey: String?,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 2.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
) {
    val shape = rememberExpressiveAvatarShape(shapeKey)
    Box(
        modifier = modifier
            .clip(shape)
            .background(ringColor)
            .padding(ringWidth),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = url,
            contentDescription = contentDescription,
            shape = shape,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        )
    }
}

