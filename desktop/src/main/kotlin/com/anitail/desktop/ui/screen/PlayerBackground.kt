package com.anitail.desktop.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.layout.ContentScale
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.ui.component.ImageCache
import com.anitail.desktop.ui.extractGradientColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorFilter as SkiaColorFilter
import org.jetbrains.skia.ColorMatrix as SkiaColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader as SkiaShader
import org.jetbrains.skia.Surface as SkiaSurface
import java.util.Collections
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ARTWORK_TEXTURE_SIZE = 128
private const val ARTWORK_TEXTURE_PADDING = 48
private const val LIQUID_PADDED_SIZE = ARTWORK_TEXTURE_SIZE + ARTWORK_TEXTURE_PADDING * 2
private const val LIQUID_BLUR_RADIUS = 26f
private const val LIQUID_SATURATION = 2.5f
private const val LIQUID_FRAME_STEP_MS = 50L
private const val LIQUID_CROSSFADE_MS = 1000

private const val LIQUID_BACKGROUND_SKSL = """
uniform shader artwork;
uniform shader previous;
uniform float2 viewport;
uniform float2 ratios;
uniform float3 cosines;
uniform float3 sines;
uniform float crossfade;
uniform float liquid;
uniform float4 crop;
uniform float4 treatment;
uniform float2 mirror;

float2 rotateAroundCenter(float2 uv, float c, float s, float scale, float2 offset) {
    float2 rotated = float2(
        uv.x * c - uv.y * s,
        uv.x * s + uv.y * c
    );
    return float2(0.5, 0.5) + rotated * scale + offset;
}

half4 sampleBlurredLayer(shader tex, float2 normUv, float2 texSize) {
    float2 pixelCoord = clamp(normUv, 0.04, 0.96) * texSize;
    return tex.eval(pixelCoord);
}

half4 evaluateLiquid(shader tex, float2 centeredUv, float2 texSize) {
    float2 uv0 = rotateAroundCenter(centeredUv, cosines.x, sines.x, 0.74, float2(0.0, 0.0));
    float2 uv1 = rotateAroundCenter(centeredUv, cosines.y, sines.y, 0.58, float2(0.11, -0.09));
    float2 uv2 = rotateAroundCenter(centeredUv, cosines.z, sines.z, 0.46, float2(-0.10, 0.12));

    half4 c0 = sampleBlurredLayer(tex, uv0, texSize);
    half4 c1 = sampleBlurredLayer(tex, uv1, texSize);
    half4 c2 = sampleBlurredLayer(tex, uv2, texSize);

    half4 blended = mix(c0, c1, 0.46);
    blended = mix(blended, c2, 0.36);
    return blended;
}

half4 main(float2 fragCoord) {
    float maxDim = max(viewport.x, viewport.y);
    float2 centeredUv = (fragCoord - viewport * 0.5) / max(maxDim, 1.0);
    centeredUv *= ratios;
    if (mirror.x > 0.5) {
        centeredUv.x = -centeredUv.x;
    }
    if (mirror.y > 0.5) {
        centeredUv.y = -centeredUv.y;
    }

    float2 texSize = float2(max(crop.z - crop.x, 1.0), max(crop.w - crop.y, 1.0));
    half4 currentColor = evaluateLiquid(artwork, centeredUv, texSize);
    half4 color = currentColor;
    if (crossfade < 0.999) {
        half4 prevColor = evaluateLiquid(previous, centeredUv, texSize);
        color = mix(prevColor, currentColor, clamp(crossfade, 0.0, 1.0));
    }

    float radial = length((fragCoord - viewport * 0.5) / max(viewport, float2(1.0)));
    float vignette = clamp(1.0 - radial * (0.28 + treatment.x * 0.1), 0.58, 1.0);
    color.rgb *= vignette * (0.68 + 0.16 * liquid);
    color.a = 1.0;
    return color;
}
"""

private object LiquidShaderRuntime {
    val effect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(LIQUID_BACKGROUND_SKSL) }.getOrNull()
    }
}

private val gradientColorCache: MutableMap<String, List<Color>> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Color>>(24, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>?): Boolean {
                return size > 20
            }
        },
    )

private val blurredArtworkCache: MutableMap<String, ImageBitmap> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(12, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > 10
            }
        },
    )

/**
 * Full-bleed adaptive background layer for the desktop player supporting
 * `DEFAULT`, `GRADIENT`, `BLUR`, and the Skia shader-backed `LIQUID` style.
 */
@Composable
fun PlayerBackgroundLayer(
    style: PlayerBackgroundStyle,
    artworkUrl: String?,
    artworkFallbackUrls: List<String> = emptyList(),
    pureBlack: Boolean = false,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background

    when (style) {
        PlayerBackgroundStyle.DEFAULT -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(baseColor),
            )
        }

        PlayerBackgroundStyle.BLUR -> {
            BlurredArtworkBackground(
                artworkUrl = artworkUrl,
                artworkFallbackUrls = artworkFallbackUrls,
                pureBlack = pureBlack,
                modifier = modifier,
            )
        }

        PlayerBackgroundStyle.GRADIENT -> {
            GradientArtworkBackground(
                artworkUrl = artworkUrl,
                artworkFallbackUrls = artworkFallbackUrls,
                pureBlack = pureBlack,
                modifier = modifier,
            )
        }

        PlayerBackgroundStyle.LIQUID -> {
            LiquidArtworkBackground(
                artworkUrl = artworkUrl,
                artworkFallbackUrls = artworkFallbackUrls,
                pureBlack = pureBlack,
                isPlaying = isPlaying,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun BlurredArtworkBackground(
    artworkUrl: String?,
    artworkFallbackUrls: List<String>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    var blurredBitmap by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { blurredArtworkCache[it] })
    }

    LaunchedEffect(artworkUrl, artworkFallbackUrls) {
        val url = artworkUrl
        if (url.isNullOrBlank()) {
            blurredBitmap = null
            return@LaunchedEffect
        }
        blurredArtworkCache[url]?.let {
            blurredBitmap = it
            return@LaunchedEffect
        }
        val bitmap = loadArtworkBitmap(url, artworkFallbackUrls)
        val blurred = bitmap?.let { createBlurredBackgroundBitmap(url, it) }
        blurredBitmap = blurred
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val image = blurredBitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (pureBlack) 0.52f else 0.42f),
                            Color.Black.copy(alpha = if (pureBlack) 0.68f else 0.54f),
                            Color.Black.copy(alpha = if (pureBlack) 0.82f else 0.68f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun GradientArtworkBackground(
    artworkUrl: String?,
    artworkFallbackUrls: List<String>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val fallbackTop = MaterialTheme.colorScheme.surfaceContainerHigh
    val fallbackBottom = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
    var gradientColors by remember { mutableStateOf(listOf(fallbackTop, fallbackBottom)) }

    LaunchedEffect(artworkUrl, artworkFallbackUrls, pureBlack) {
        val url = artworkUrl
        if (url.isNullOrBlank()) {
            gradientColors = listOf(fallbackTop, fallbackBottom)
        } else {
            val extracted = gradientColorCache[url] ?: run {
                val bitmap = loadArtworkBitmap(url, artworkFallbackUrls)
                val colors = withContext(Dispatchers.Default) {
                    bitmap?.extractGradientColors().orEmpty()
                }
                if (colors.size >= 2) {
                    gradientColorCache[url] = colors
                }
                colors
            }
            gradientColors = if (extracted.size >= 2) {
                listOf(
                    extracted[0],
                    extracted.getOrElse(1) { extracted[0] },
                    if (pureBlack) Color.Black else extracted.last(),
                )
            } else {
                listOf(fallbackTop, fallbackBottom)
            }
        }
    }

    val topColor by animateColorAsState(
        targetValue = gradientColors.firstOrNull() ?: fallbackTop,
        animationSpec = tween(700),
        label = "player_gradient_top",
    )
    val middleColor by animateColorAsState(
        targetValue = gradientColors.getOrElse(1) { topColor },
        animationSpec = tween(700),
        label = "player_gradient_middle",
    )
    val bottomColor by animateColorAsState(
        targetValue = gradientColors.lastOrNull() ?: fallbackBottom,
        animationSpec = tween(700),
        label = "player_gradient_bottom",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, middleColor, bottomColor),
                ),
            )
            .background(
                Color.Black.copy(alpha = if (pureBlack) 0.42f else 0.28f),
            ),
    )
}

private class LiquidTextureEntry(
    val key: String,
    val image: SkiaImage,
    val shader: SkiaShader = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR),
) {
    fun close() {
        runCatching { shader.close() }
        runCatching { image.close() }
    }
}

@Composable
private fun LiquidArtworkBackground(
    artworkUrl: String?,
    artworkFallbackUrls: List<String>,
    pureBlack: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val fallbackSurface = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerLowest
    val defaultTint = MaterialTheme.colorScheme.primary
    var activeTexture by remember { mutableStateOf<LiquidTextureEntry?>(null) }
    var previousTexture by remember { mutableStateOf<LiquidTextureEntry?>(null) }
    val crossfade = remember { Animatable(1f) }
    var rotationTimeMs by remember { mutableLongStateOf(0L) }
    val effect = LiquidShaderRuntime.effect
    val shaderBuilder = remember(effect) { effect?.let { RuntimeShaderBuilder(it) } }
    val shaderPaint = remember { SkiaPaint() }

    DisposableEffect(shaderBuilder, shaderPaint) {
        onDispose {
            activeTexture?.close()
            previousTexture?.close()
            activeTexture = null
            previousTexture = null
            runCatching { shaderBuilder?.close() }
            runCatching { shaderPaint.close() }
        }
    }

    LaunchedEffect(artworkUrl, artworkFallbackUrls) {
        val url = artworkUrl
        val newEntry = if (url.isNullOrBlank()) {
            null
        } else {
            val bitmap = loadArtworkBitmap(url, artworkFallbackUrls)
            bitmap?.let { createBlurredLiquidTexture(url, it) }
        }

        if (newEntry != null && newEntry.key == activeTexture?.key) {
            newEntry.close()
            return@LaunchedEffect
        }

        val oldPrev = previousTexture
        previousTexture = activeTexture
        activeTexture = newEntry
        if (oldPrev != null && oldPrev !== previousTexture && oldPrev !== activeTexture) {
            oldPrev.close()
        }

        if (previousTexture != null && activeTexture != null) {
            crossfade.snapTo(0f)
            crossfade.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = LIQUID_CROSSFADE_MS, easing = LinearEasing),
            )
            val finishedPrev = previousTexture
            previousTexture = null
            if (finishedPrev != null && finishedPrev !== activeTexture) {
                finishedPrev.close()
            }
        } else {
            crossfade.snapTo(1f)
            val finishedPrev = previousTexture
            previousTexture = null
            if (finishedPrev != null && finishedPrev !== activeTexture) {
                finishedPrev.close()
            }
        }
    }

    LaunchedEffect(isPlaying, activeTexture) {
        if (!isPlaying || activeTexture == null) return@LaunchedEffect
        var lastFrameMs = withFrameMillis { it }
        var accumulatedDelta = 0L
        while (true) {
            val nowMs = withFrameMillis { it }
            val delta = (nowMs - lastFrameMs).coerceIn(0L, 200L)
            lastFrameMs = nowMs
            accumulatedDelta += delta
            if (accumulatedDelta >= LIQUID_FRAME_STEP_MS) {
                rotationTimeMs += accumulatedDelta
                accumulatedDelta = 0L
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackSurface)
            .drawBehind {
                val active = activeTexture
                if (active == null || size.width <= 0f || size.height <= 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                defaultTint.copy(alpha = 0.28f),
                                fallbackSurface,
                            ),
                        ),
                    )
                    return@drawBehind
                }

                val t = rotationTimeMs
                val angle0 = (-2.0 * PI * (t % 130_000L) / 130_000.0).toFloat()
                val angle1 = (2.0 * PI * (t % 90_000L) / 90_000.0).toFloat()
                val angle2 = (2.0 * PI * (t % 70_000L) / 70_000.0).toFloat()

                if (shaderBuilder != null) {
                    val prev = previousTexture ?: active
                    drawIntoCanvas { canvas ->
                        drawLiquidWithShader(
                            skiaCanvas = canvas.nativeCanvas,
                            builder = shaderBuilder,
                            paint = shaderPaint,
                            active = active,
                            previous = prev,
                            width = size.width,
                            height = size.height,
                            angle0 = angle0,
                            angle1 = angle1,
                            angle2 = angle2,
                            crossfade = crossfade.value,
                        )
                    }
                } else {
                    drawIntoCanvas { canvas ->
                        drawLiquidFallback(
                            skiaCanvas = canvas.nativeCanvas,
                            paint = shaderPaint,
                            activeImage = active.image,
                            previousImage = previousTexture?.image,
                            width = size.width,
                            height = size.height,
                            angle0 = angle0,
                            angle1 = angle1,
                            angle2 = angle2,
                            crossfade = crossfade.value,
                        )
                    }
                }
            }
            .background(
                Color.Black.copy(alpha = if (pureBlack) 0.44f else 0.24f),
            ),
    )
}

private fun drawLiquidWithShader(
    skiaCanvas: org.jetbrains.skia.Canvas,
    builder: RuntimeShaderBuilder,
    paint: SkiaPaint,
    active: LiquidTextureEntry,
    previous: LiquidTextureEntry,
    width: Float,
    height: Float,
    angle0: Float,
    angle1: Float,
    angle2: Float,
    crossfade: Float,
) {
    builder.apply {
        child("artwork", active.shader)
        child("previous", previous.shader)
        uniform("viewport", width, height)
        uniform("ratios", 1f, 1f)
        uniform("cosines", cos(angle0), cos(angle1), cos(angle2))
        uniform("sines", sin(angle0), sin(angle1), sin(angle2))
        uniform("crossfade", crossfade)
        uniform("liquid", 1f)
        uniform("crop", 0f, 0f, active.image.width.toFloat(), active.image.height.toFloat())
        uniform("treatment", 0f, 0f, 0f, 0f)
        uniform("mirror", 0f, 0f)
    }
    builder.makeShader().use { shader ->
        paint.shader = shader
        skiaCanvas.drawRect(SkiaRect.makeWH(width, height), paint)
        paint.shader = null
    }
}

private fun drawLiquidFallback(
    skiaCanvas: org.jetbrains.skia.Canvas,
    paint: SkiaPaint,
    activeImage: SkiaImage,
    previousImage: SkiaImage?,
    width: Float,
    height: Float,
    angle0: Float,
    angle1: Float,
    angle2: Float,
    crossfade: Float,
) {
    val diagonal = kotlin.math.hypot(width.toDouble(), height.toDouble()).toFloat() * 1.35f
    val cx = width * 0.5f
    val cy = height * 0.5f

    fun drawRotatedLayers(image: SkiaImage, layerWeight: Float) {
        if (layerWeight <= 0.01f) return
        val srcRect = SkiaRect.makeWH(image.width.toFloat(), image.height.toFloat())
        val dstRect = SkiaRect.makeXYWH(-diagonal * 0.5f, -diagonal * 0.5f, diagonal, diagonal)
        val anglesDeg = floatArrayOf(
            (angle0 * 180f / PI.toFloat()),
            (angle1 * 180f / PI.toFloat()),
            (angle2 * 180f / PI.toFloat()),
        )
        val alphas = floatArrayOf(1f, 0.45f, 0.30f)
        paint.shader = null
        for (i in anglesDeg.indices) {
            paint.alpha = (alphas[i] * layerWeight * 255f).roundToInt().coerceIn(0, 255)
            skiaCanvas.save()
            skiaCanvas.translate(cx, cy)
            skiaCanvas.rotate(anglesDeg[i])
            skiaCanvas.drawImageRect(image, srcRect, dstRect, SamplingMode.LINEAR, paint, true)
            skiaCanvas.restore()
        }
        paint.alpha = 255
    }

    skiaCanvas.save()
    skiaCanvas.clipRect(SkiaRect.makeWH(width, height))
    if (previousImage != null && crossfade < 0.999f) {
        drawRotatedLayers(previousImage, 1f - crossfade)
        drawRotatedLayers(activeImage, crossfade)
    } else {
        drawRotatedLayers(activeImage, 1f)
    }
    skiaCanvas.restore()
}

private suspend fun createBlurredBackgroundBitmap(
    key: String,
    bitmap: ImageBitmap,
): ImageBitmap? = withContext(Dispatchers.Default) {
    blurredArtworkCache[key]?.let { return@withContext it }
    runCatching {
        val skiaBitmap = bitmap.asSkiaBitmap()
        SkiaImage.makeFromBitmap(skiaBitmap).use { srcImage ->
            SkiaSurface.makeRasterN32Premul(LIQUID_PADDED_SIZE, LIQUID_PADDED_SIZE).use { surface ->
                ImageFilter.makeBlur(LIQUID_BLUR_RADIUS, LIQUID_BLUR_RADIUS, FilterTileMode.CLAMP).use { blurFilter ->
                    SkiaPaint().use { paint ->
                        paint.imageFilter = blurFilter
                        surface.canvas.drawImageRect(
                            srcImage,
                            SkiaRect.makeWH(srcImage.width.toFloat(), srcImage.height.toFloat()),
                            SkiaRect.makeWH(LIQUID_PADDED_SIZE.toFloat(), LIQUID_PADDED_SIZE.toFloat()),
                            SamplingMode.LINEAR,
                            paint,
                            true,
                        )
                    }
                }
                surface.makeImageSnapshot().use { snapshot ->
                    snapshot.toComposeImageBitmap().also { blurredArtworkCache[key] = it }
                }
            }
        }
    }.getOrNull()
}

private suspend fun createBlurredLiquidTexture(
    key: String,
    bitmap: ImageBitmap,
): LiquidTextureEntry? = withContext(Dispatchers.Default) {
    runCatching {
        val skiaBitmap = bitmap.asSkiaBitmap()
        SkiaImage.makeFromBitmap(skiaBitmap).use { srcImage ->
            SkiaSurface.makeRasterN32Premul(LIQUID_PADDED_SIZE, LIQUID_PADDED_SIZE).use { surface ->
                val satMatrix = ComposeColorMatrix().apply { setToSaturation(LIQUID_SATURATION) }
                SkiaColorFilter.makeMatrix(SkiaColorMatrix(satMatrix.values)).use { satFilter ->
                    ImageFilter.makeBlur(LIQUID_BLUR_RADIUS, LIQUID_BLUR_RADIUS, FilterTileMode.CLAMP).use { blurFilter ->
                        SkiaPaint().use { paint ->
                            paint.colorFilter = satFilter
                            paint.imageFilter = blurFilter
                            surface.canvas.drawImageRect(
                                srcImage,
                                SkiaRect.makeWH(srcImage.width.toFloat(), srcImage.height.toFloat()),
                                SkiaRect.makeWH(LIQUID_PADDED_SIZE.toFloat(), LIQUID_PADDED_SIZE.toFloat()),
                                SamplingMode.LINEAR,
                                paint,
                                true,
                            )
                        }
                    }
                }
                LiquidTextureEntry(key = key, image = surface.makeImageSnapshot())
            }
        }
    }.getOrNull()
}

private suspend fun loadArtworkBitmap(
    url: String,
    fallbackUrls: List<String> = emptyList(),
): ImageBitmap? {
    val candidates = buildList {
        add(url)
        fallbackUrls.forEach { fallback ->
            if (fallback.isNotBlank() && fallback != url) {
                add(fallback)
            }
        }
    }

    for (candidate in candidates) {
        val loaded = ImageCache.loadOrFetch(candidate, maxDimensionPx = 768)
        if (loaded != null) {
            return loaded
        }
    }
    return null
}
