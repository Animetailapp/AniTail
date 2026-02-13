package com.anitail.desktop.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image as SkiaImage

internal fun loadBitmapResource(path: String): ImageBitmap? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        ?: return null
    return stream.use { resource ->
        runCatching {
            SkiaImage.makeFromEncoded(resource.readBytes()).asImageBitmap()
        }.getOrNull()
    }
}
