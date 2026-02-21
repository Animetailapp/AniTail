package com.anitail.music.ui.component

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toBitmap
import com.anitail.music.MainActivity
import com.anitail.music.constants.WidgetActionExploreEnabledKey
import com.anitail.music.constants.WidgetActionLibraryEnabledKey
import com.anitail.music.constants.WidgetActionLyricsEnabledKey
import com.anitail.music.constants.WidgetActionQueueEnabledKey
import com.anitail.music.constants.WidgetActionSearchEnabledKey
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anitail.music.R
import com.anitail.music.playback.MusicService
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.pow

class MusicWidgetProvider : AppWidgetProvider() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (WIDGET_DISABLED) {
            Timber.tag(TAG).d("Widget deshabilitado: onUpdate ignorado")
            return
        }
        updateWidgets(context, appWidgetManager, appWidgetIds)
        requestMusicServiceUpdate(context)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        if (WIDGET_DISABLED) {
            Timber.tag(TAG).d("Widget deshabilitado: optionsChanged ignorado")
            return
        }
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        requestMusicServiceUpdate(context)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (WIDGET_DISABLED) {
            Timber.tag(TAG).d("Widget deshabilitado: onReceive(${intent.action}) ignorado")
            return
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        when (intent.action) {
            MusicService.ACTION_WIDGET_UPDATE -> {
                updateWidgets(context, appWidgetManager, appWidgetIds, intent)
            }

            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV -> {
                forwardActionToService(context, intent)

                if (intent.action == ACTION_NEXT || intent.action == ACTION_PREV) {
                    CoroutineScope(Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(300)
                        requestMusicServiceUpdate(context)
                    }
                }
            }
        }
    }
    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, updateIntent: Intent? = null) {

        if (appWidgetIds.isEmpty()) {
            Timber.tag(TAG).w("No widget instances found")
            return
        }

        // Avoid reusing one RemoteViews for mixed-size widgets; each size needs its own layout.
        if (appWidgetIds.size > 1) {
            val firstLayout = resolveLayoutRes(appWidgetManager, appWidgetIds.first())
            val hasMixedLayouts = appWidgetIds.any { widgetId ->
                resolveLayoutRes(appWidgetManager, widgetId) != firstLayout
            }
            if (hasMixedLayouts) {
                appWidgetIds.forEach { widgetId ->
                    updateWidgets(context, appWidgetManager, intArrayOf(widgetId), updateIntent)
                }
                return
            }
        }

        val layoutRes = resolveLayoutRes(appWidgetManager, appWidgetIds.first())
        val views = RemoteViews(context.packageName, layoutRes)
        val isCompact = layoutRes == R.layout.widget_music_small

        // Set click listeners for controls
        setupWidgetControls(context, views, layoutRes)
        if (isCompact) {
            views.setViewVisibility(R.id.widget_quick_actions, View.GONE)
        }

        // Update widget content if we have update data
        if (updateIntent?.action == MusicService.ACTION_WIDGET_UPDATE) {
            val song = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_SONG_TITLE) ?: ""
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining song title")
                ""
            }

            val artist = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_ARTIST) ?:
                    updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_ARTIST, 0).let {
                        if (it != 0) {
                            try {
                                context.getString(it)
                            } catch (_: Exception) {
                                ""
                            }
                        } else {
                            ""
                        }
                    }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining artist name")
                ""
            }

            val isPlaying = try {
                updateIntent.getBooleanExtra(MusicService.EXTRA_WIDGET_IS_PLAYING, false)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining play state")
                false
            }

            val themeColor = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_THEME_COLOR, 0xFFED5564.toInt())
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining theme color")
                0xFFED5564.toInt()
            }

            val coverUrl = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_COVER_URL) ?: ""
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining cover URL")
                ""
            }

            val progress = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_PROGRESS, 0)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining progress")
                0
            }

            val dominantColor = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_DOMINANT_COLOR, themeColor)
                    .let { if (it == 0) themeColor else it }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining dominant color")
                themeColor
            }

            applyWidgetVisualStyle(views, dominantColor)
            views.setTextViewText(R.id.widget_title, song)
            views.setTextViewText(R.id.widget_artist, artist.ifBlank { context.getString(R.string.unknown_artist) })
            views.setImageViewResource(R.id.widget_play_pause, if (isPlaying) R.drawable.pause else R.drawable.play)
            setWidgetProgress(views, progress)
            views.setViewVisibility(
                R.id.widget_progress,
                if (layoutRes == R.layout.widget_music_square) View.GONE else View.VISIBLE
            )

            if (layoutRes == R.layout.widget_music) {
                updateProgressRing(context, views, R.id.widget_cover_progress_ring, progress, dominantColor, 80)
            }
            if (layoutRes == R.layout.widget_music_square) {
                updateProgressRing(context, views, R.id.widget_play_progress_ring, progress, dominantColor, 56)
            }

            updateWidgetInstances(appWidgetManager, appWidgetIds, views)

            if (coverUrl.isBlank()) {
                if (lastMainCoverUrl.isNotBlank()) {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_placeholder)
                    views.setImageViewResource(R.id.widget_backdrop, R.drawable.ic_music_placeholder)
                    updateWidgetInstances(appWidgetManager, appWidgetIds, views)
                }
                lastMainCoverUrl = ""
            } else if (coverUrl != lastMainCoverUrl) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(135, 135)
                            .allowHardware(false)
                            .crossfade(true)
                            .build()
                        val result = getWidgetImageLoader(context).execute(request)
                        val bitmap = result.drawable?.toBitmap()

                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.widget_cover, bitmap)
                            views.setImageViewBitmap(R.id.widget_backdrop, bitmap)
                            lastMainCoverUrl = coverUrl
                            updateWidgetInstances(appWidgetManager, appWidgetIds, views)
                        } else {
                            Timber.tag(TAG).w("Failed to load cover image, bitmap is null")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error loading cover image")
                    }
                }
            }
        } else {
            applyWidgetVisualStyle(views, DEFAULT_WIDGET_COLOR)
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.song_notplaying))
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.play)
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_placeholder)
            views.setImageViewResource(R.id.widget_backdrop, R.drawable.ic_music_placeholder)
            setWidgetProgress(views, 0)
            views.setViewVisibility(
                R.id.widget_progress,
                if (layoutRes == R.layout.widget_music_square) View.GONE else View.VISIBLE
            )

            // Clear progress ring for wide layout
            if (layoutRes == R.layout.widget_music) {
                updateProgressRing(context, views, R.id.widget_cover_progress_ring, 0, DEFAULT_WIDGET_COLOR, 80)
            }
            if (layoutRes == R.layout.widget_music_square) {
                updateProgressRing(context, views, R.id.widget_play_progress_ring, 0, DEFAULT_WIDGET_COLOR, 56)
            }

            lastMainCoverUrl = ""
            updateWidgetInstances(appWidgetManager, appWidgetIds, views)
        }
    }

    private fun updateWidgetInstances(
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        views: RemoteViews
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun setupWidgetControls(context: Context, views: RemoteViews, layoutRes: Int) {
        val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPlayerPendingIntent = PendingIntent.getActivity(
            context, 3, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_cover, openPlayerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_root, openPlayerPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_backdrop, openPlayerPendingIntent)
        // Play/Pause intent
        val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)

        // Next track intent
        val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 1, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)

        // Previous track intent
        val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)

        val actionSlots = listOf(
            WidgetQuickActionSlot(
                slotId = R.id.widget_action_slot_lyrics,
                iconId = R.id.widget_action_lyrics
            ),
            WidgetQuickActionSlot(
                slotId = R.id.widget_action_slot_queue,
                iconId = R.id.widget_action_queue
            ),
            WidgetQuickActionSlot(
                slotId = R.id.widget_action_slot_search,
                iconId = R.id.widget_action_search
            )
        )
        val maxQuickActions = when (layoutRes) {
            R.layout.widget_music -> 2
            R.layout.widget_music_small -> 0
            else -> 3
        }
        val configuredActions = getConfiguredQuickActions(context).take(maxQuickActions)

        actionSlots.forEachIndexed { index, slot ->
            val quickAction = configuredActions.getOrNull(index)
            if (quickAction == null) {
                views.setViewVisibility(slot.slotId, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(slot.slotId, View.VISIBLE)
            views.setImageViewResource(slot.iconId, quickAction.iconRes)
            views.setContentDescription(slot.iconId, context.getString(quickAction.labelRes))

            val actionIntent = Intent(context, MainActivity::class.java).apply {
                action = quickAction.intentAction
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                4 + index,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(slot.slotId, pendingIntent)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun forwardActionToService(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action

            intent.extras?.let { bundleExtras ->
                putExtras(bundleExtras)
            }

            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        try {
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                action.hashCode(),
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Timber.tag(TAG).d("Action forwarded to service via PendingIntent: $action")
        } catch (_: Exception) {
            try {
                context.startForegroundService(serviceIntent)
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "Error starting service for action: $action")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestMusicServiceUpdate(context: Context) {

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = "com.anitail.music.action.UPDATE_WIDGET"
        }
        try {
            // Widget refresh should not force a foreground service notification.
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Timber.tag(TAG).d("Service intent sent for widget update via PendingIntent")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error sending service intent for update")
        }
    }

    private fun applyWidgetVisualStyle(views: RemoteViews, dominantColor: Int) {
        val accentColor = normalizeWidgetAccent(dominantColor)
        val overlayColor = withAlpha(blendColors(accentColor, Color.BLACK, 0.62f), 150)
        val titleColor = pickReadableForeground(overlayColor)
        val artistColor = withAlpha(titleColor, 215)

        val controlBgColor = withAlpha(blendColors(accentColor, Color.WHITE, 0.30f), 180)
        val playBgColor = withAlpha(blendColors(accentColor, Color.WHITE, 0.16f), 236)
        val quickActionBgColor = withAlpha(blendColors(accentColor, Color.WHITE, 0.24f), 172)

        val controlIconColor = pickReadableForeground(controlBgColor)
        val playIconColor = pickReadableForeground(playBgColor)

        val readableProgressColor = pickReadableForeground(overlayColor)
        val progressColor = withAlpha(readableProgressColor, 246)
        val progressTrackColor = withAlpha(readableProgressColor, 96)
        val coverRingTrackColor = withAlpha(blendColors(accentColor, Color.WHITE, 0.35f), 85)

        views.setInt(R.id.widget_backdrop_tint, "setBackgroundColor", overlayColor)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextColor(R.id.widget_artist, artistColor)

        views.setInt(R.id.widget_cover_ring, "setColorFilter", coverRingTrackColor)
        views.setInt(R.id.widget_prev_bg, "setColorFilter", controlBgColor)
        views.setInt(R.id.widget_next_bg, "setColorFilter", controlBgColor)
        views.setInt(R.id.widget_play_pause_bg, "setColorFilter", playBgColor)
        views.setInt(R.id.widget_action_lyrics_bg, "setColorFilter", quickActionBgColor)
        views.setInt(R.id.widget_action_queue_bg, "setColorFilter", quickActionBgColor)
        views.setInt(R.id.widget_action_search_bg, "setColorFilter", quickActionBgColor)

        views.setInt(R.id.widget_prev, "setColorFilter", controlIconColor)
        views.setInt(R.id.widget_next, "setColorFilter", controlIconColor)
        views.setInt(R.id.widget_action_lyrics, "setColorFilter", controlIconColor)
        views.setInt(R.id.widget_action_queue, "setColorFilter", controlIconColor)
        views.setInt(R.id.widget_action_search, "setColorFilter", controlIconColor)
        views.setInt(R.id.widget_play_pause, "setColorFilter", playIconColor)
        views.setInt(R.id.widget_song_progress_track, "setColorFilter", progressTrackColor)
        views.setInt(R.id.widget_song_progress, "setColorFilter", progressColor)
    }

    private fun setWidgetProgress(views: RemoteViews, progressPercent: Int) {
        val level = progressPercent.coerceIn(0, 100) * 100
        views.setInt(R.id.widget_song_progress, "setImageLevel", level)
    }

    private fun updateProgressRing(context: Context, views: RemoteViews, viewId: Int, progress: Int, dominantColor: Int, sizeDp: Int) {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val strokePx = 5f * density

        val accentColor = normalizeWidgetAccent(dominantColor)
        val ringColor = withAlpha(blendColors(accentColor, Color.WHITE, 0.42f), 255)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }
        val padding = strokePx / 2f
        val rect = RectF(padding, padding, sizePx - padding, sizePx - padding)
        val sweepAngle = (progress.coerceIn(0, 100) / 100f) * 360f
        if (sweepAngle > 0f) {
            canvas.drawArc(rect, -90f, sweepAngle, false, paint)
        }
        views.setImageViewBitmap(viewId, bitmap)
    }

    private fun getWidgetImageLoader(context: Context): ImageLoader {
        val cachedLoader = widgetImageLoader
        if (cachedLoader != null) {
            return cachedLoader
        }

        return synchronized(imageLoaderLock) {
            widgetImageLoader ?: ImageLoader.Builder(context.applicationContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build()
                .also { widgetImageLoader = it }
        }
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val amount = ratio.coerceIn(0f, 1f)
        val inverse = 1f - amount

        return Color.argb(
            (Color.alpha(from) * inverse + Color.alpha(to) * amount).toInt(),
            (Color.red(from) * inverse + Color.red(to) * amount).toInt(),
            (Color.green(from) * inverse + Color.green(to) * amount).toInt(),
            (Color.blue(from) * inverse + Color.blue(to) * amount).toInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
    }

    private fun normalizeWidgetAccent(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.05f).coerceIn(0.25f, 0.78f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.82f)
        return Color.HSVToColor(255, hsv)
    }

    private fun pickReadableForeground(backgroundColor: Int): Int {
        val contrastWithWhite = contrastRatio(backgroundColor, Color.WHITE)
        val contrastWithBlack = contrastRatio(backgroundColor, Color.BLACK)
        return if (contrastWithBlack >= contrastWithWhite) Color.BLACK else Color.WHITE
    }

    private fun contrastRatio(firstColor: Int, secondColor: Int): Double {
        val firstLuminance = colorLuminance(firstColor)
        val secondLuminance = colorLuminance(secondColor)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun colorLuminance(color: Int): Double {
        val red = toLinearColorChannel(Color.red(color))
        val green = toLinearColorChannel(Color.green(color))
        val blue = toLinearColorChannel(Color.blue(color))
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun toLinearColorChannel(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.03928) {
            srgb / 12.92
        } else {
            ((srgb + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun getConfiguredQuickActions(context: Context): List<WidgetQuickActionConfig> {
        val selectedActions = listOf(
            WidgetQuickActionConfig(
                enabled = context.dataStore[WidgetActionLyricsEnabledKey, true],
                iconRes = R.drawable.lyrics,
                labelRes = R.string.lyrics,
                intentAction = MainActivity.ACTION_WIDGET_OPEN_LYRICS
            ),
            WidgetQuickActionConfig(
                enabled = context.dataStore[WidgetActionQueueEnabledKey, true],
                iconRes = R.drawable.queue_music,
                labelRes = R.string.queue,
                intentAction = MainActivity.ACTION_WIDGET_OPEN_QUEUE
            ),
            WidgetQuickActionConfig(
                enabled = context.dataStore[WidgetActionSearchEnabledKey, true],
                iconRes = R.drawable.search,
                labelRes = R.string.search,
                intentAction = MainActivity.ACTION_SEARCH
            ),
            WidgetQuickActionConfig(
                enabled = context.dataStore[WidgetActionLibraryEnabledKey, false],
                iconRes = R.drawable.library_music,
                labelRes = R.string.filter_library,
                intentAction = MainActivity.ACTION_LIBRARY
            ),
            WidgetQuickActionConfig(
                enabled = context.dataStore[WidgetActionExploreEnabledKey, false],
                iconRes = R.drawable.explore_outlined,
                labelRes = R.string.explore,
                intentAction = MainActivity.ACTION_EXPLORE
            )
        ).filter { it.enabled }

        return if (selectedActions.isEmpty()) defaultQuickActions.take(3) else selectedActions.take(3)
    }

    private fun resolveLayoutRes(appWidgetManager: AppWidgetManager, appWidgetId: Int): Int {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val effectiveWidth = maxOf(minWidth, maxWidth)
        val effectiveHeight = maxOf(minHeight, maxHeight)
        val ratio = if (effectiveHeight > 0) effectiveWidth.toFloat() / effectiveHeight.toFloat() else 1f

        // Some launchers (notably MIUI) can report stale min sizes while resizing.
        // Use effective dimensions (max of min/max) to avoid selecting square on large widgets.
        val layoutRes = if (effectiveHeight < 110 || (ratio >= 2.25f && effectiveHeight < 170)) {
            R.layout.widget_music_small
        } else if (
            effectiveHeight >= 165 &&
            (ratio >= 1.2f || effectiveWidth >= 300)
        ) {
            R.layout.widget_music
        } else {
            R.layout.widget_music_square
        }

        Timber.tag(TAG).d(
            "Widget #%d layout=%s minWidth=%d minHeight=%d maxWidth=%d maxHeight=%d effectiveWidth=%d effectiveHeight=%d ratio=%.2f",
            appWidgetId,
            when (layoutRes) {
                R.layout.widget_music -> "wide"
                R.layout.widget_music_square -> "square"
                else -> "bar"
            },
            minWidth,
            minHeight,
            maxWidth,
            maxHeight,
            effectiveWidth,
            effectiveHeight,
            ratio
        )
        return layoutRes
    }

    data class WidgetQuickActionConfig(
        val enabled: Boolean,
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int,
        val intentAction: String
    )
    data class WidgetQuickActionSlot(
        val slotId: Int,
        val iconId: Int
    )

    companion object {
        private const val TAG = "MusicWidgetProvider"
        const val ACTION_PLAY_PAUSE = "com.anitail.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.anitail.music.widget.NEXT"
        const val ACTION_PREV = "com.anitail.music.widget.PREV"
        private const val DEFAULT_WIDGET_COLOR = 0xFF1D3342.toInt()
        @Volatile private var lastMainCoverUrl: String = ""
        private val imageLoaderLock = Any()
        @Volatile private var widgetImageLoader: ImageLoader? = null
        private val defaultQuickActions = listOf(
            WidgetQuickActionConfig(
                enabled = true,
                iconRes = R.drawable.lyrics,
                labelRes = R.string.lyrics,
                intentAction = MainActivity.ACTION_WIDGET_OPEN_LYRICS
            ),
            WidgetQuickActionConfig(
                enabled = true,
                iconRes = R.drawable.queue_music,
                labelRes = R.string.queue,
                intentAction = MainActivity.ACTION_WIDGET_OPEN_QUEUE
            ),
            WidgetQuickActionConfig(
                enabled = true,
                iconRes = R.drawable.search,
                labelRes = R.string.search,
                intentAction = MainActivity.ACTION_SEARCH
            )
        )

        // Flag global para desactivar completamente el widget (evita consumo de batería y CPU)
        const val WIDGET_DISABLED = false
    }
}
