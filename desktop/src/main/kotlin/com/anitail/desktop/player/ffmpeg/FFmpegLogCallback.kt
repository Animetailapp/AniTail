package com.anitail.desktop.player.ffmpeg

import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry
import org.bytedeco.ffmpeg.avutil.LogCallback
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_DEBUG
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_FATAL
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_INFO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_PANIC
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_TRACE
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_VERBOSE
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_WARNING
import org.bytedeco.ffmpeg.global.avutil.av_dict_count
import org.bytedeco.ffmpeg.global.avutil.av_dict_iterate
import org.bytedeco.ffmpeg.global.avutil.av_log_get_level
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.setLogCallback
import org.bytedeco.javacpp.BytePointer

class FFmpegLogCallback : LogCallback() {
    companion object {
        private val instanceRef by lazy { FFmpegLogCallback().retainReference<FFmpegLogCallback>() }

        @JvmStatic
        fun getInstance(): FFmpegLogCallback = instanceRef

        @JvmStatic
        fun set() {
            setLogCallback(instanceRef)
        }

        @JvmStatic
        fun getLevel(): Int = av_log_get_level()

        @JvmStatic
        fun setLevel(level: Int) {
            av_log_set_level(level)
        }

        @JvmStatic
        fun logRejectedOptions(
            options: AVDictionary?,
            command: String,
        ) {
            if (getLevel() >= AV_LOG_INFO && av_dict_count(options) > 0) {
                val sb = StringBuilder("$command rejected some options:")
                var e: AVDictionaryEntry? = null
                while (av_dict_iterate(options, e).also { e = it } != null) {
                    sb
                        .append("\tOption: ")
                        .append(e?.key()?.string)
                        .append(", value: ")
                        .append(e?.value()?.string)
                }
                FFmpegLogger.i("FFmpeg", sb.toString())
            }
        }
    }

    override fun call(
        level: Int,
        msg: BytePointer?,
    ) {
        // Never unwind a logging exception through FFmpeg's native callback.
        runCatching { logNativeMessage(level, msg?.string ?: return) }
    }
}

internal fun logNativeMessage(
    level: Int,
    text: String,
) {
    val message = FFmpegLogger.redactDiagnosticText(text.trim().take(8192))
    if (message.isBlank()) return
    when (level) {
        AV_LOG_PANIC, AV_LOG_FATAL, AV_LOG_ERROR -> {
            FFmpegLogger.e("FFmpeg", message)
        }

        AV_LOG_WARNING -> {
            FFmpegLogger.w("FFmpeg", message)
        }

        AV_LOG_INFO -> {
            FFmpegLogger.i("FFmpeg", message)
        }

        AV_LOG_VERBOSE, AV_LOG_DEBUG, AV_LOG_TRACE -> {
            // Keep verbose/debug FFmpeg logs suppressed unless needed.
        }

        else -> {}
    }
}

