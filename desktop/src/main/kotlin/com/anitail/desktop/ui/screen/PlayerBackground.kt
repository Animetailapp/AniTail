package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.ui.component.ImageCache
import com.anitail.desktop.ui.component.RemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.URL
import kotlin.math.max

@Composable
fun PlayerBackgroundLayer(
    artworkUrl: String?,
    style: PlayerBackgroundStyle,
    pureBlack: Boolean,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
) {
    if (style == PlayerBackgroundStyle.DEFAULT) return

    val defaultGradient = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val gradientCache = remember { mutableMapOf<String, List<Color>>() }
    var gradientColors by remember(artworkUrl) { mutableStateOf(defaultGradient) }

    val useBlackBackground = pureBlack

    LaunchedEffect(artworkUrl, style, pureBlack) {
        if (style != PlayerBackgroundStyle.GRADIENT) return@LaunchedEffect
        if (useBlackBackground) {
            gradientColors = listOf(Color.Black, Color.Black)
            return@LaunchedEffect
        }
        val url = artworkUrl ?: return@LaunchedEffect
        val cached = gradientCache[url]
        if (cached != null) {
            gradientColors = cached
            return@LaunchedEffect
        }
        val bitmap = loadArtworkBitmap(url)
        if (bitmap != null) {
            val colors = extractGradientColors(bitmap).ifEmpty { defaultGradient }
            gradientCache[url] = colors
            gradientColors = colors
        } else {
            gradientColors = defaultGradient
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (style) {
            PlayerBackgroundStyle.BLUR -> {
                if (useBlackBackground) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
                RemoteImage(
                    url = artworkUrl,
                    modifier = Modifier.fillMaxSize().blur(150.dp),
                    contentScale = ContentScale.FillBounds,
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors)),
                )
            }
            PlayerBackgroundStyle.DEFAULT -> Unit
        }

        if (showLyrics) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }
    }
}

private suspend fun loadArtworkBitmap(url: String): ImageBitmap? {
    ImageCache.get(url)?.let { return it }
    val bytes = withContext(Dispatchers.IO) {
        runCatching { URL(url).readBytes() }.getOrNull()
    } ?: return null
    val bitmap = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    if (bitmap != null) {
        ImageCache.put(url, bitmap)
    }
    return bitmap
}

private fun extractGradientColors(bitmap: ImageBitmap): List<Color> {
    val pixelMap = bitmap.toPixelMap()
    val width = pixelMap.width
    val height = pixelMap.height
    if (width == 0 || height == 0) return emptyList()

    val stepX = max(1, width / 40)
    val stepY = max(1, height / 40)
    var red = 0f
    var green = 0f
    var blue = 0f
    var count = 0

    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val color = pixelMap[x, y]
            red += color.red
            green += color.green
            blue += color.blue
            count++
        }
    }

    if (count == 0) return emptyList()
    val dominant = Color(red / count, green / count, blue / count, 1f)
    val isDark = isColorDark(dominant)
    val secondary = shiftColor(dominant, if (isDark) 0.2f else -0.2f)
    return listOf(dominant, secondary)
}

private fun isColorDark(color: Color): Boolean {
    val yiq =
        ((color.red * 255f) * 299f + (color.green * 255f) * 587f + (color.blue * 255f) * 114f) / 1000f
    return yiq < 128f
}

private fun shiftColor(color: Color, delta: Float): Color {
    return Color(
        red = (color.red + delta).coerceIn(0f, 1f),
        green = (color.green + delta).coerceIn(0f, 1f),
        blue = (color.blue + delta).coerceIn(0f, 1f),
        alpha = color.alpha,
    )
}
