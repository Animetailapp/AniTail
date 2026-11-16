package com.anitail.desktop.player

import com.anitail.desktop.ui.component.VideoShaderEffects
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.BytePointer
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.SamplingMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class VideoDecodeReproTest {
    @Test
    fun `repro 1440x1080 h264 yuv420p decode and Skia shader render with restart`() {
        val tempFile = File.createTempFile("repro_1440x1080_", ".mp4")
        tempFile.deleteOnExit()
        createSyntheticMp4(tempFile, width = 1440, height = 1080, frameCount = 48)

        val surface = Surface.makeRasterN32Premul(640, 480)
        val paint = Paint()
        val yuvBuilder = VideoShaderEffects.yuv420pEffect?.let { RuntimeShaderBuilder(it) }

        val firstFrames = CountDownLatch(3)
        val secondFrames = CountDownLatch(5)
        var secondPhase = false

        val player = VideoPlayer(
            onFrame = { frame ->
                frame.drawSafely {
                    if (format == VideoPixelFormat.YUV420P && yuvBuilder != null) {
                        planeY!!.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR).use { ys ->
                            planeU!!.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR).use { us ->
                                planeV!!.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR).use { vs ->
                                    yuvBuilder.child("yTex", ys)
                                    yuvBuilder.child("uTex", us)
                                    yuvBuilder.child("vTex", vs)
                                    yuvBuilder.uniform("colorSpace", 0)
                                    yuvBuilder.uniform("colorRange", 0)
                                    yuvBuilder.makeShader().use { shader ->
                                        paint.shader = shader
                                        surface.canvas.drawRect(Rect.makeWH(640f, 480f), paint)
                                        paint.shader = null
                                    }
                                }
                            }
                        }
                    }
                }
                if (!secondPhase) firstFrames.countDown() else secondFrames.countDown()
            },
        )
        try {
            player.setTargetSize(640, 480)
            var t0 = System.currentTimeMillis() - 500L
            player.start(tempFile.absolutePath, positionMs = 500L) { System.currentTimeMillis() - t0 }
            assertTrue(firstFrames.await(15, TimeUnit.SECONDS), "Timed out waiting for phase 1 frames")

            // Simulate user toggling / restarting video multiple times
            secondPhase = true
            t0 = System.currentTimeMillis() - 800L
            player.start(tempFile.absolutePath, positionMs = 800L) { System.currentTimeMillis() - t0 }
            assertTrue(secondFrames.await(15, TimeUnit.SECONDS), "Timed out waiting for phase 2 frames")
        } finally {
            kotlinx.coroutines.runBlocking { player.disposeAsync().join() }
            yuvBuilder?.close()
            paint.close()
            surface.close()
            tempFile.delete()
        }
    }

    private fun createSyntheticMp4(file: File, width: Int, height: Int, frameCount: Int) {
        val oc = AVFormatContext(null)
        check(avformat_alloc_output_context2(oc, null, null, file.absolutePath) >= 0)
        val codec = avcodec_find_encoder(AV_CODEC_ID_MPEG4) ?: error("No MPEG4 encoder")
        val st = avformat_new_stream(oc, null) ?: error("No stream")
        val ctx = avcodec_alloc_context3(codec) ?: error("No ctx")
        ctx.codec_id(AV_CODEC_ID_MPEG4)
        ctx.width(width)
        ctx.height(height)
        ctx.time_base(AVRational().num(1).den(24))
        ctx.framerate(AVRational().num(24).den(1))
        ctx.pix_fmt(AV_PIX_FMT_YUV420P)
        ctx.gop_size(12)
        if ((oc.oformat().flags() and AVFMT_GLOBALHEADER) != 0) {
            ctx.flags(ctx.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
        }
        check(avcodec_open2(ctx, codec, null as AVDictionary?) >= 0)
        check(avcodec_parameters_from_context(st.codecpar(), ctx) >= 0)
        st.time_base(ctx.time_base())
        if ((oc.oformat().flags() and AVFMT_NOFILE) == 0) {
            val pb = org.bytedeco.ffmpeg.avformat.AVIOContext(null)
            check(avio_open(pb, file.absolutePath, AVIO_FLAG_WRITE) >= 0)
            oc.pb(pb)
        }
        check(avformat_write_header(oc, null as AVDictionary?) >= 0)

        val frame = av_frame_alloc()
        frame.format(ctx.pix_fmt())
        frame.width(width)
        frame.height(height)
        check(av_frame_get_buffer(frame, 0) >= 0)
        val pkt = av_packet_alloc()

        for (i in 0 until frameCount) {
            av_frame_make_writable(frame)
            frame.pts(i.toLong())
            avcodec_send_frame(ctx, frame)
            while (avcodec_receive_packet(ctx, pkt) == 0) {
                av_packet_rescale_ts(pkt, ctx.time_base(), st.time_base())
                pkt.stream_index(st.index())
                av_interleaved_write_frame(oc, pkt)
                av_packet_unref(pkt)
            }
        }
        avcodec_send_frame(ctx, null)
        while (avcodec_receive_packet(ctx, pkt) == 0) {
            av_packet_rescale_ts(pkt, ctx.time_base(), st.time_base())
            pkt.stream_index(st.index())
            av_interleaved_write_frame(oc, pkt)
            av_packet_unref(pkt)
        }
        av_write_trailer(oc)
        av_packet_free(pkt)
        av_frame_free(frame)
        if ((oc.oformat().flags() and AVFMT_NOFILE) == 0) {
            avio_closep(oc.pb())
        }
        avcodec_free_context(ctx)
        avformat_free_context(oc)
    }
}

