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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.ffmpeg.sharedMediaOkHttpClient
import com.anitail.desktop.storage.DesktopPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface as SkiaSurface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * RemoteImage - Carga y muestra imágenes remotas con caché en memoria
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrls: List<String> = emptyList(),
    maxDimensionPx: Int = 768,
    onImageLoaded: ((width: Int, height: Int) -> Unit)? = null,
) {
    val candidates = remember(url, fallbackUrls) {
        buildList {
            if (!url.isNullOrBlank()) add(url)
            fallbackUrls.forEach { candidate ->
                if (candidate.isNotBlank()) add(candidate)
            }
        }.distinct()
    }
    var candidateIndex by remember(candidates) { mutableStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)

    val cached = remember(activeUrl) { ImageCache.get(activeUrl) }
    var image by remember(activeUrl) { mutableStateOf<ImageBitmap?>(cached) }
    val currentOnImageLoaded by rememberUpdatedState(onImageLoaded)

    LaunchedEffect(image) {
        val loaded = image ?: return@LaunchedEffect
        currentOnImageLoaded?.invoke(loaded.width, loaded.height)
    }

    LaunchedEffect(activeUrl, candidateIndex, candidates, maxDimensionPx) {
        if (image != null || activeUrl.isNullOrBlank()) return@LaunchedEffect
        val bitmap = ImageCache.loadOrFetch(activeUrl, maxDimensionPx = maxDimensionPx)
        if (bitmap != null) {
            image = bitmap
        } else if (candidateIndex < candidates.lastIndex) {
            candidateIndex += 1
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
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.clip(shape),
        )
    }
}

/**
 * Caché en memoria y cargador deduplicado para imágenes remotas
 */
internal object ImageCache {
    private data class CacheEntry(
        val bitmap: ImageBitmap,
        val approxBytes: Long,
    )

    private val cache = LinkedHashMap<String, CacheEntry>(64, 0.75f, true)
    private var totalBytes = 0L

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeSemaphore = Semaphore(6)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val httpClient by lazy {
        sharedMediaOkHttpClient
            .newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun get(url: String?): ImageBitmap? = if (url == null) null else cache[url]?.bitmap

    @Synchronized
    fun put(url: String, bitmap: ImageBitmap) {
        val bytes = estimateBytes(bitmap)
        val previous = cache.put(url, CacheEntry(bitmap = bitmap, approxBytes = bytes))
        if (previous != null) {
            totalBytes -= previous.approxBytes
        }
        totalBytes += bytes
        trimToLimit(resolveMaxBytes())
    }

    suspend fun loadOrFetch(url: String, maxDimensionPx: Int = 768): ImageBitmap? {
        get(url)?.let { return it }
        val deferred = inFlight.computeIfAbsent(url) {
            loaderScope.async {
                try {
                    get(url)?.let { return@async it }
                    val bytes = fetchBytes(url) ?: return@async null
                    val bitmap = decodeSemaphore.withPermit {
                        decodeBitmap(bytes, maxDimensionPx)
                    }
                    if (bitmap != null) {
                        put(url, bitmap)
                    }
                    bitmap
                } finally {
                    inFlight.remove(url)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                return@runCatching java.net.URI(url).toURL().openStream().use { it.readBytes() }
            }
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun decodeBitmap(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap? {
        return runCatching {
            SkiaImage.makeFromEncoded(bytes).use { skiaImage ->
                val srcW = skiaImage.width
                val srcH = skiaImage.height
                if (srcW <= 0 || srcH <= 0) return@use null
                if (maxDimensionPx <= 0 || (srcW <= maxDimensionPx && srcH <= maxDimensionPx)) {
                    return@use skiaImage.toComposeImageBitmap()
                }
                val scale = maxDimensionPx.toFloat() / maxOf(srcW, srcH).toFloat()
                val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
                val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)
                SkiaSurface.makeRasterN32Premul(dstW, dstH).use { surface ->
                    SkiaPaint().use { paint ->
                        surface.canvas.drawImageRect(
                            skiaImage,
                            SkiaRect.makeWH(srcW.toFloat(), srcH.toFloat()),
                            SkiaRect.makeWH(dstW.toFloat(), dstH.toFloat()),
                            SamplingMode.LINEAR,
                            paint,
                            true,
                        )
                    }
                    surface.makeImageSnapshot().use { scaled ->
                        scaled.toComposeImageBitmap()
                    }
                }
            }
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        cache.clear()
        totalBytes = 0L
    }

    @Synchronized
    fun estimatedSizeBytes(): Long = totalBytes

    @Synchronized
    fun enforceCurrentLimit() {
        trimToLimit(resolveMaxBytes())
    }

    @Synchronized
    private fun trimToLimit(maxBytes: Long) {
        if (maxBytes <= 0L) return
        val iterator = cache.entries.iterator()
        while (totalBytes > maxBytes && iterator.hasNext()) {
            val entry = iterator.next()
            totalBytes -= entry.value.approxBytes
            iterator.remove()
        }
    }

    private fun resolveMaxBytes(): Long {
        val maxMb = runCatching { DesktopPreferences.getInstance().maxImageCacheSizeMB.value }
            .getOrDefault(500)
        return maxMb.toLong() * 1024L * 1024L
    }

    private fun estimateBytes(bitmap: ImageBitmap): Long {
        return bitmap.width.toLong() * bitmap.height.toLong() * 4L
    }
}
