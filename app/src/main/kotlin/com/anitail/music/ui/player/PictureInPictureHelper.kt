package com.anitail.music.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.compositionLocalOf
import androidx.media3.common.VideoSize
import com.anitail.music.R
import com.anitail.music.ui.component.MusicWidgetProvider
import timber.log.Timber

val LocalIsInPipMode = compositionLocalOf { false }

tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@RequiresApi(Build.VERSION_CODES.O)
object PictureInPictureHelper {
    private const val TAG = "PictureInPictureHelper"
    private const val MIN_ASPECT_RATIO = 0.41841f
    private const val MAX_ASPECT_RATIO = 2.39f

    fun enterPictureInPicture(
        activity: Activity,
        isPlaying: Boolean,
        videoSize: VideoSize?,
        hasVideo: Boolean = true,
    ) {
        try {
            val params = buildPipParams(activity, isPlaying, videoSize, hasVideo = hasVideo)
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to enter PiP mode")
        }
    }

    fun updatePictureInPictureParams(
        activity: Activity,
        isPlaying: Boolean,
        videoSize: VideoSize?,
        hasVideo: Boolean = true,
    ) {
        try {
            val inPip = activity.isInPictureInPictureMode
            if (inPip || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = buildPipParams(activity, isPlaying, videoSize, hasVideo = hasVideo)
                activity.setPictureInPictureParams(params)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to update PiP params")
        }
    }

    fun buildPipParams(
        context: Context,
        isPlaying: Boolean,
        videoSize: VideoSize?,
        hasVideo: Boolean = true,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        builder.setAspectRatio(calculatePipAspectRatio(videoSize, hasVideo))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
        }

        val actions = ArrayList<RemoteAction>(3)

        val prevIntent =
            Intent(context, MusicWidgetProvider::class.java).apply {
                action = MusicWidgetProvider.ACTION_PREV
            }
        val prevPendingIntent =
            PendingIntent.getBroadcast(
                context,
                101,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.skip_previous),
                context.getString(R.string.previous),
                context.getString(R.string.previous),
                prevPendingIntent,
            ),
        )

        val playPauseIntent =
            Intent(context, MusicWidgetProvider::class.java).apply {
                action = MusicWidgetProvider.ACTION_PLAY_PAUSE
            }
        val playPausePendingIntent =
            PendingIntent.getBroadcast(
                context,
                102,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
        val playPauseText = context.getString(if (isPlaying) R.string.pause else R.string.play)
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, playPauseIcon),
                playPauseText,
                playPauseText,
                playPausePendingIntent,
            ),
        )

        val nextIntent =
            Intent(context, MusicWidgetProvider::class.java).apply {
                action = MusicWidgetProvider.ACTION_NEXT
            }
        val nextPendingIntent =
            PendingIntent.getBroadcast(
                context,
                103,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.skip_next),
                context.getString(R.string.next),
                context.getString(R.string.next),
                nextPendingIntent,
            ),
        )

        builder.setActions(actions)
        return builder.build()
    }

    private fun calculatePipAspectRatio(
        videoSize: VideoSize?,
        hasVideo: Boolean,
    ): Rational {
        if (!hasVideo || videoSize == null || videoSize.width <= 0 || videoSize.height <= 0) {
            return Rational(1, 1)
        }
        val pixelRatio =
            if (videoSize.pixelWidthHeightRatio.isFinite() && videoSize.pixelWidthHeightRatio > 0f) {
                videoSize.pixelWidthHeightRatio
            } else {
                1f
            }
        val rawRatio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
        if (!rawRatio.isFinite() || rawRatio <= 0f) {
            return Rational(1, 1)
        }
        val clampedRatio = rawRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        return Rational((clampedRatio * 1000).toInt(), 1000)
    }
}

