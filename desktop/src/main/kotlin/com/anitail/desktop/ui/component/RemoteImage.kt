package com.anitail.desktop.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.URL

/**
 * RemoteImage - Carga y muestra imágenes remotas con caché en memoria
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val cached = remember(url) { ImageCache.get(url) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(cached) }

    LaunchedEffect(url) {
        if (image != null || url.isNullOrBlank()) return@LaunchedEffect
        val bytes = withContext(Dispatchers.IO) {
            runCatching { URL(url).readBytes() }.getOrNull()
        } ?: return@LaunchedEffect
        val bitmap = runCatching { Image.makeFromEncoded(bytes).asImageBitmap() }.getOrNull()
        if (bitmap != null) {
            ImageCache.put(url, bitmap)
            image = bitmap
        }
    }

    if (image == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Image(
            bitmap = image!!,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.clip(shape),
        )
    }
}

/**
 * Caché en memoria para imágenes cargadas
 */
internal object ImageCache {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun get(url: String?): ImageBitmap? = if (url == null) null else cache[url]

    fun put(url: String, bitmap: ImageBitmap) {
        cache[url] = bitmap
    }
}
