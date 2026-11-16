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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anitail.desktop.player.VideoColorRange
import com.anitail.desktop.player.VideoColorSpace
import com.anitail.desktop.player.VideoFrame
import com.anitail.desktop.player.VideoPixelFormat
import com.anitail.desktop.player.VideoPlayer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.roundToInt

private const val YUV420P_SKSL = """
uniform shader yTex;
uniform shader uTex;
uniform shader vTex;
uniform int colorSpace;
uniform int colorRange;

half4 main(vec2 coord) {
    float y = max(yTex.eval(coord).r, yTex.eval(coord).a);
    float u = max(uTex.eval(coord * 0.5).r, uTex.eval(coord * 0.5).a);
    float v = max(vTex.eval(coord * 0.5).r, vTex.eval(coord * 0.5).a);

    if (colorRange == 0) {
        y = (y - 0.062745098) * 1.164383562;
        u = (u - 0.501960784) * 1.138392857;
        v = (v - 0.501960784) * 1.138392857;
    } else {
        u = u - 0.5;
        v = v - 0.5;
    }

    vec3 rgb;
    if (colorSpace == 1) {
        rgb.r = y + 1.402 * v;
        rgb.g = y - 0.344136 * u - 0.714136 * v;
        rgb.b = y + 1.772 * u;
    } else if (colorSpace == 2) {
        rgb.r = y + 1.4746 * v;
        rgb.g = y - 0.164553 * u - 0.571353 * v;
        rgb.b = y + 1.8814 * u;
    } else {
        rgb.r = y + 1.5748 * v;
        rgb.g = y - 0.187324 * u - 0.468124 * v;
        rgb.b = y + 1.8556 * u;
    }

    return half4(clamp(rgb, 0.0, 1.0), 1.0);
}
"""

private const val NV12_SKSL = """
uniform shader yTex;
uniform shader uvTex;
uniform int colorSpace;
uniform int colorRange;

half4 main(vec2 coord) {
    float y = max(yTex.eval(coord).r, yTex.eval(coord).a);
    vec2 uv = uvTex.eval(coord * 0.5).rg;
    float u = uv.r;
    float v = uv.g;

    if (colorRange == 0) {
        y = (y - 0.062745098) * 1.164383562;
        u = (u - 0.501960784) * 1.138392857;
        v = (v - 0.501960784) * 1.138392857;
    } else {
        u = u - 0.5;
        v = v - 0.5;
    }

    vec3 rgb;
    if (colorSpace == 1) {
        rgb.r = y + 1.402 * v;
        rgb.g = y - 0.344136 * u - 0.714136 * v;
        rgb.b = y + 1.772 * u;
    } else if (colorSpace == 2) {
        rgb.r = y + 1.4746 * v;
        rgb.g = y - 0.164553 * u - 0.571353 * v;
        rgb.b = y + 1.8814 * u;
    } else {
        rgb.r = y + 1.5748 * v;
        rgb.g = y - 0.187324 * u - 0.468124 * v;
        rgb.b = y + 1.8556 * u;
    }

    return half4(clamp(rgb, 0.0, 1.0), 1.0);
}
"""

internal object VideoShaderEffects {
    val yuv420pEffect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(YUV420P_SKSL) }
            .onFailure { System.err.println("VideoShaderEffects: YUV420P shader compile failed: ${it.message}") }
            .getOrNull()
    }

    val nv12Effect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(NV12_SKSL) }
            .onFailure { System.err.println("VideoShaderEffects: NV12 shader compile failed: ${it.message}") }
            .getOrNull()
    }

    val isYuvShaderAvailable: Boolean
        get() = yuv420pEffect != null
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
    val renderPaint = remember { Paint() }
    val yuvBuilder = remember(VideoShaderEffects.yuv420pEffect) {
        VideoShaderEffects.yuv420pEffect?.let { RuntimeShaderBuilder(it) }
    }
    val nv12Builder = remember(VideoShaderEffects.nv12Effect) {
        VideoShaderEffects.nv12Effect?.let { RuntimeShaderBuilder(it) }
    }

    DisposableEffect(renderPaint, yuvBuilder, nv12Builder) {
        onDispose {
            renderPaint.close()
            yuvBuilder?.close()
            nv12Builder?.close()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { videoPlayer.setTargetSize(it.width, it.height) }
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
                                renderPaint = renderPaint,
                                yuvBuilder = yuvBuilder,
                                nv12Builder = nv12Builder,
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
    renderPaint: Paint,
    yuvBuilder: RuntimeShaderBuilder?,
    nv12Builder: RuntimeShaderBuilder?,
) {
    val frameW = frame.width.toFloat()
    val frameH = frame.height.toFloat()
    if (frameW <= 0f || frameH <= 0f) return

    val scaleFactor = contentScale.computeScaleFactor(
        srcSize = Size(frameW, frameH),
        dstSize = Size(canvasWidth, canvasHeight),
    )
    val scale = scaleFactor.scaleX
    val dstW = (frameW * scale).roundToInt()
    val dstH = (frameH * scale).roundToInt()
    val offsetX = ((canvasWidth - dstW) / 2f).roundToInt().toFloat()
    val offsetY = ((canvasHeight - dstH) / 2f).roundToInt().toFloat()

    when (frame.format) {
        VideoPixelFormat.BGRA -> {
            val bitmap = frame.bgraBitmap ?: return
            Image.makeFromBitmap(bitmap).use { image ->
                val dstRect = Rect.makeXYWH(offsetX, offsetY, dstW.toFloat(), dstH.toFloat())
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
            val builder = yuvBuilder ?: return
            val yBmp = frame.planeY ?: return
            val uBmp = frame.planeU ?: return
            val vBmp = frame.planeV ?: return
            drawWithThreePlanes(
                skiaCanvas = skiaCanvas,
                builder = builder,
                renderPaint = renderPaint,
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
            val builder = nv12Builder ?: return
            val yBmp = frame.planeY ?: return
            val uvBmp = frame.planeUV ?: return
            drawWithTwoPlanes(
                skiaCanvas = skiaCanvas,
                builder = builder,
                renderPaint = renderPaint,
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
    builder: RuntimeShaderBuilder,
    renderPaint: Paint,
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
    yBmp.makePlaneShader().use { yShader ->
        uBmp.makePlaneShader().use { uShader ->
            vBmp.makePlaneShader().use { vShader ->
                builder.child("yTex", yShader)
                builder.child("uTex", uShader)
                builder.child("vTex", vShader)
                builder.uniform("colorSpace", frame.colorSpace.toUniformInt())
                builder.uniform("colorRange", frame.colorRange.toUniformInt())
                builder.makeShader().use { shader ->
                    renderPaint.shader = shader
                    try {
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
                            paint = renderPaint,
                        )
                    } finally {
                        renderPaint.shader = null
                    }
                }
            }
        }
    }
}

private fun drawWithTwoPlanes(
    skiaCanvas: org.jetbrains.skia.Canvas,
    builder: RuntimeShaderBuilder,
    renderPaint: Paint,
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
    yBmp.makePlaneShader().use { yShader ->
        uvBmp.makePlaneShader().use { uvShader ->
            builder.child("yTex", yShader)
            builder.child("uvTex", uvShader)
            builder.uniform("colorSpace", frame.colorSpace.toUniformInt())
            builder.uniform("colorRange", frame.colorRange.toUniformInt())
            builder.makeShader().use { shader ->
                renderPaint.shader = shader
                try {
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
                        paint = renderPaint,
                    )
                } finally {
                    renderPaint.shader = null
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
    val count = skiaCanvas.save()
    try {
        skiaCanvas.clipRect(Rect.makeWH(canvasWidth, canvasHeight))
        skiaCanvas.translate(offsetX, offsetY)
        skiaCanvas.scale(scaleX, scaleY)
        skiaCanvas.drawRect(Rect.makeWH(frameWidth, frameHeight), paint)
    } finally {
        skiaCanvas.restoreToCount(count)
    }
}

private fun Bitmap.makePlaneShader(): Shader =
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
