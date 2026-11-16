package com.anitail.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.VideoColorRange
import com.anitail.desktop.player.VideoColorSpace
import com.anitail.desktop.player.VideoFrame
import com.anitail.desktop.player.VideoPixelFormat
import com.anitail.desktop.player.VideoPlayer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

private const val YUV420P_SKSL = """
uniform shader yTex;
uniform shader uTex;
uniform shader vTex;
uniform int colorSpace;
uniform int colorRange;
uniform float alphaOnly;

float samplePlane(shader s, float2 coord) {
    half4 c = s.eval(coord);
    return alphaOnly > 0.5 ? float(c.a) : float(c.r);
}

half4 main(float2 fragCoord) {
    float yRaw = samplePlane(yTex, fragCoord);
    float2 chromaCoord = fragCoord * 0.5;
    float uRaw = samplePlane(uTex, chromaCoord);
    float vRaw = samplePlane(vTex, chromaCoord);

    float y;
    float u;
    float v;
    if (colorRange == 1) {
        y = yRaw;
        u = uRaw - (128.0 / 255.0);
        v = vRaw - (128.0 / 255.0);
    } else {
        y = (yRaw - (16.0 / 255.0)) * (255.0 / 219.0);
        u = (uRaw - (128.0 / 255.0)) * (255.0 / 224.0);
        v = (vRaw - (128.0 / 255.0)) * (255.0 / 224.0);
    }

    float r;
    float g;
    float b;
    if (colorSpace == 1) {
        r = y + 1.402 * v;
        g = y - 0.344136 * u - 0.714136 * v;
        b = y + 1.772 * u;
    } else if (colorSpace == 2) {
        r = y + 1.4746 * v;
        g = y - 0.164553 * u - 0.571353 * v;
        b = y + 1.8814 * u;
    } else {
        r = y + 1.5748 * v;
        g = y - 0.187324 * u - 0.468124 * v;
        b = y + 1.8556 * u;
    }
    return half4(clamp(r, 0.0, 1.0), clamp(g, 0.0, 1.0), clamp(b, 0.0, 1.0), 1.0);
}
"""

private const val NV12_SKSL = """
uniform shader yTex;
uniform shader uvTex;
uniform int colorSpace;
uniform int colorRange;
uniform float alphaOnly;

half4 main(float2 fragCoord) {
    half4 ySample = yTex.eval(fragCoord);
    float yRaw = alphaOnly > 0.5 ? float(ySample.a) : float(ySample.r);
    half4 uvSample = uvTex.eval(fragCoord * 0.5);
    float uRaw = float(uvSample.r);
    float vRaw = float(uvSample.g);

    float y;
    float u;
    float v;
    if (colorRange == 1) {
        y = yRaw;
        u = uRaw - (128.0 / 255.0);
        v = vRaw - (128.0 / 255.0);
    } else {
        y = (yRaw - (16.0 / 255.0)) * (255.0 / 219.0);
        u = (uRaw - (128.0 / 255.0)) * (255.0 / 224.0);
        v = (vRaw - (128.0 / 255.0)) * (255.0 / 224.0);
    }

    float r;
    float g;
    float b;
    if (colorSpace == 1) {
        r = y + 1.402 * v;
        g = y - 0.344136 * u - 0.714136 * v;
        b = y + 1.772 * u;
    } else if (colorSpace == 2) {
        r = y + 1.4746 * v;
        g = y - 0.164553 * u - 0.571353 * v;
        b = y + 1.8814 * u;
    } else {
        r = y + 1.5748 * v;
        g = y - 0.187324 * u - 0.468124 * v;
        b = y + 1.8556 * u;
    }
    return half4(clamp(r, 0.0, 1.0), clamp(g, 0.0, 1.0), clamp(b, 0.0, 1.0), 1.0);
}
"""

private object VideoShaderEffects {
    val yuv420pEffect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(YUV420P_SKSL) }.getOrNull()
    }

    val nv12Effect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(NV12_SKSL) }.getOrNull()
    }
}

/**
 * Hardware-accelerated Skia video surface that decodes [VideoFrame] planes on the GPU
 * via SkSL runtime shaders (`YUV420P`, `NV12`) or draws `BGRA` bitmaps directly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerSurface(
    videoPlayer: VideoPlayer,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
) {
    val hasRenderedFrame = remember(videoPlayer) { booleanArrayOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .drawBehind {
                // Subscribe to frameSequence inside drawBehind so new frames trigger redraws without recomposition.
                val sequence = videoPlayer.frameSequence
                val frame = videoPlayer.currentFrame
                if (frame != null && sequence >= 0L && size.width > 0f && size.height > 0f) {
                    val drawn = frame.drawSafely {
                        val lockedFrame = this
                        drawIntoCanvas { canvas ->
                            drawVideoFrame(
                                skiaCanvas = canvas.nativeCanvas,
                                frame = lockedFrame,
                                canvasWidth = size.width,
                                canvasHeight = size.height,
                                contentScale = contentScale,
                            )
                        }
                    }
                    if (drawn) {
                        hasRenderedFrame[0] = true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val error = videoPlayer.errorMessage
        when {
            error != null -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            videoPlayer.isBuffering && videoPlayer.currentFrame == null && !hasRenderedFrame[0] -> {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                )
            }
        }
    }
}

private fun drawVideoFrame(
    skiaCanvas: org.jetbrains.skia.Canvas,
    frame: VideoFrame,
    canvasWidth: Float,
    canvasHeight: Float,
    contentScale: ContentScale,
) {
    val frameW = frame.width.toFloat()
    val frameH = frame.height.toFloat()
    if (frameW <= 0f || frameH <= 0f) return

    val scaleFactor = contentScale.computeScaleFactor(
        srcSize = Size(frameW, frameH),
        dstSize = Size(canvasWidth, canvasHeight),
    )
    val drawW = frameW * scaleFactor.scaleX
    val drawH = frameH * scaleFactor.scaleY
    val offsetX = (canvasWidth - drawW) * 0.5f
    val offsetY = (canvasHeight - drawH) * 0.5f

    when (frame.format) {
        VideoPixelFormat.BGRA -> {
            val bitmap = frame.bgraBitmap ?: return
            Image.makeFromBitmap(bitmap).use { image ->
                val dstRect = Rect.makeXYWH(offsetX, offsetY, drawW, drawH)
                skiaCanvas.save()
                skiaCanvas.clipRect(Rect.makeWH(canvasWidth, canvasHeight))
                skiaCanvas.drawImageRect(
                    image,
                    Rect.makeWH(frameW, frameH),
                    dstRect,
                    SamplingMode.LINEAR,
                    null,
                    true,
                )
                skiaCanvas.restore()
            }
        }

        VideoPixelFormat.YUV420P -> {
            val effect = VideoShaderEffects.yuv420pEffect ?: return
            val yBmp = frame.planeY ?: return
            val uBmp = frame.planeU ?: return
            val vBmp = frame.planeV ?: return
            drawWithThreePlanes(
                skiaCanvas = skiaCanvas,
                effect = effect,
                yBmp = yBmp,
                uBmp = uBmp,
                vBmp = vBmp,
                frame = frame,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                offsetX = offsetX,
                offsetY = offsetY,
                scaleX = scaleFactor.scaleX,
                scaleY = scaleFactor.scaleY,
            )
        }

        VideoPixelFormat.NV12 -> {
            val effect = VideoShaderEffects.nv12Effect ?: return
            val yBmp = frame.planeY ?: return
            val uvBmp = frame.planeUV ?: return
            drawWithTwoPlanes(
                skiaCanvas = skiaCanvas,
                effect = effect,
                yBmp = yBmp,
                uvBmp = uvBmp,
                frame = frame,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                offsetX = offsetX,
                offsetY = offsetY,
                scaleX = scaleFactor.scaleX,
                scaleY = scaleFactor.scaleY,
            )
        }
    }
}

private fun drawWithThreePlanes(
    skiaCanvas: org.jetbrains.skia.Canvas,
    effect: RuntimeEffect,
    yBmp: Bitmap,
    uBmp: Bitmap,
    vBmp: Bitmap,
    frame: VideoFrame,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scaleX: Float,
    scaleY: Float,
) {
    Image.makeFromBitmap(yBmp).use { yImg ->
        Image.makeFromBitmap(uBmp).use { uImg ->
            Image.makeFromBitmap(vBmp).use { vImg ->
                yImg.makePlaneShader().use { yShader ->
                    uImg.makePlaneShader().use { uShader ->
                        vImg.makePlaneShader().use { vShader ->
                            val builder = RuntimeShaderBuilder(effect).apply {
                                child("yTex", yShader)
                                child("uTex", uShader)
                                child("vTex", vShader)
                                uniform("colorSpace", frame.colorSpace.toUniformInt())
                                uniform("colorRange", frame.colorRange.toUniformInt())
                                uniform("alphaOnly", if (yBmp.colorType == ColorType.ALPHA_8) 1f else 0f)
                            }
                            builder.makeShader().use { shader ->
                                Paint().use { paint ->
                                    paint.shader = shader
                                    drawTransformedShaderRect(
                                        skiaCanvas = skiaCanvas,
                                        frameWidth = frame.width.toFloat(),
                                        frameHeight = frame.height.toFloat(),
                                        canvasWidth = canvasWidth,
                                        canvasHeight = canvasHeight,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        scaleX = scaleX,
                                        scaleY = scaleY,
                                        paint = paint,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun drawWithTwoPlanes(
    skiaCanvas: org.jetbrains.skia.Canvas,
    effect: RuntimeEffect,
    yBmp: Bitmap,
    uvBmp: Bitmap,
    frame: VideoFrame,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scaleX: Float,
    scaleY: Float,
) {
    Image.makeFromBitmap(yBmp).use { yImg ->
        Image.makeFromBitmap(uvBmp).use { uvImg ->
            yImg.makePlaneShader().use { yShader ->
                uvImg.makePlaneShader().use { uvShader ->
                    val builder = RuntimeShaderBuilder(effect).apply {
                        child("yTex", yShader)
                        child("uvTex", uvShader)
                        uniform("colorSpace", frame.colorSpace.toUniformInt())
                        uniform("colorRange", frame.colorRange.toUniformInt())
                        uniform("alphaOnly", if (yBmp.colorType == ColorType.ALPHA_8) 1f else 0f)
                    }
                    builder.makeShader().use { shader ->
                        Paint().use { paint ->
                            paint.shader = shader
                            drawTransformedShaderRect(
                                skiaCanvas = skiaCanvas,
                                frameWidth = frame.width.toFloat(),
                                frameHeight = frame.height.toFloat(),
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                paint = paint,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun drawTransformedShaderRect(
    skiaCanvas: org.jetbrains.skia.Canvas,
    frameWidth: Float,
    frameHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    offsetX: Float,
    offsetY: Float,
    scaleX: Float,
    scaleY: Float,
    paint: Paint,
) {
    skiaCanvas.save()
    skiaCanvas.clipRect(Rect.makeWH(canvasWidth, canvasHeight))
    skiaCanvas.translate(offsetX, offsetY)
    skiaCanvas.scale(scaleX, scaleY)
    skiaCanvas.drawRect(Rect.makeWH(frameWidth, frameHeight), paint)
    skiaCanvas.restore()
}

private fun Image.makePlaneShader(): Shader =
    makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)

private fun VideoColorSpace.toUniformInt(): Int = when (this) {
    VideoColorSpace.BT709 -> 0
    VideoColorSpace.BT601 -> 1
    VideoColorSpace.BT2020 -> 2
}

private fun VideoColorRange.toUniformInt(): Int = when (this) {
    VideoColorRange.LIMITED -> 0
    VideoColorRange.FULL -> 1
}
