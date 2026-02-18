package com.anitail.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anitail.music.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for MediaStore downloads that shows persistent notifications
 * with download progress and allows user to cancel downloads.
 */
@AndroidEntryPoint
class MediaStoreDownloadService : Service() {

    @Inject
    lateinit var downloadManager: MediaStoreDownloadManager

    @Inject
    lateinit var database: com.anitail.music.db.MusicDatabase

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationManager: NotificationManager
    private val songInfoCache = mutableMapOf<String, Pair<String, String>>()
    private var lastNotificationKey: String? = null
    private val activeChildNotificationIds = mutableSetOf<Int>()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "mediastore_download"
        private const val NOTIFICATION_CHANNEL_NAME = "Music Downloads"
        private const val NOTIFICATION_ID = 100
        private const val NOTIFICATION_GROUP_KEY = "mediastore_download_group"

        const val ACTION_CANCEL_DOWNLOAD = "com.anitail.music.CANCEL_DOWNLOAD"
        const val ACTION_CANCEL_ALL_DOWNLOADS = "com.anitail.music.CANCEL_ALL_DOWNLOADS"
        const val EXTRA_SONG_ID = "song_id"

        fun start(context: Context) {
            val intent = Intent(context, MediaStoreDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaStoreDownloadService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("MediaStoreDownloadService created")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for downloads
        createNotificationChannel()

        // Start as foreground service with initial notification
        startForeground(NOTIFICATION_ID, createInitialNotification())

        // Observe download states and update notifications
        scope.launch {
            downloadManager.downloadStates
                .collectLatest { states ->
                    updateNotification(states)

                    // Stop service if no active downloads
                    if (states.values.none {
                            it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                                    it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED
                        }) {
                        Timber.d("No active downloads, stopping service")
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("MediaStoreDownloadService onStartCommand: ${intent?.action}")

        // Handle cancel action from notification
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    Timber.d("Cancelling download for song: $songId")
                    downloadManager.cancelDownload(songId)
                }
            }
            ACTION_CANCEL_ALL_DOWNLOADS -> {
                val activeSongIds = downloadManager.downloadStates.value
                    .filterValues {
                        it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                                it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED
                    }
                    .keys
                if (activeSongIds.isNotEmpty()) {
                    Timber.d("Cancelling all active downloads: ${activeSongIds.size}")
                    downloadManager.cancelDownloads(activeSongIds)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        Timber.d("MediaStoreDownloadService destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for music download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            Timber.d("Created notification channel: $NOTIFICATION_CHANNEL_ID")
        }
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Preparing downloads...")
            .setSmallIcon(R.drawable.download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateNotification(states: Map<String, MediaStoreDownloadManager.DownloadState>) {
        val activeDownloads = states.values.filter {
            it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                    it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED
        }.sortedBy { it.songId }

        if (activeDownloads.isEmpty()) {
            lastNotificationKey = null
            clearChildNotifications()
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        if (activeDownloads.size == 1) {
            clearChildNotifications()
            val singleNotification = createSingleDownloadNotification(activeDownloads.first())
            notificationManager.notify(NOTIFICATION_ID, singleNotification)
            lastNotificationKey = buildNotificationKey(activeDownloads)
            return
        }

        val currentChildIds = mutableSetOf<Int>()
        activeDownloads.forEach { downloadState ->
            val notificationId = notificationIdForSong(downloadState.songId)
            currentChildIds += notificationId
            notificationManager.notify(
                notificationId,
                createSingleDownloadNotification(
                    state = downloadState,
                    asGroupChild = true
                )
            )
        }
        cancelStaleChildNotifications(currentChildIds)

        val summaryKey = buildNotificationKey(activeDownloads)
        if (summaryKey != lastNotificationKey) {
            lastNotificationKey = summaryKey
        }

        notificationManager.notify(NOTIFICATION_ID, createMultipleDownloadsNotification(activeDownloads))
    }

    private fun buildNotificationKey(
        activeDownloads: List<MediaStoreDownloadManager.DownloadState>
    ): String {
        if (activeDownloads.size == 1) {
            val single = activeDownloads.first()
            return buildString {
                append("single:")
                append(single.songId)
                append(':')
                append(single.status.name)
                append(':')
                append((single.progress * 100f).toInt())
            }
        }

        val downloadingCount =
            activeDownloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
        val queuedCount =
            activeDownloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED }
        val avgProgress = activeDownloads
            .filter { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
            .map { it.progress }
            .average()
            .let { if (it.isNaN()) 0.0 else it }
        return "multi:$downloadingCount:$queuedCount:${(avgProgress * 100.0).toInt()}"
    }

    private suspend fun createSingleDownloadNotification(
        state: MediaStoreDownloadManager.DownloadState,
        asGroupChild: Boolean = false
    ): Notification {
        val (title, artist) = resolveSongInfo(state.songId)

        val progress = (state.progress * 100).toInt()

        // Create cancel intent
        val cancelIntent = Intent(this, MediaStoreDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_SONG_ID, state.songId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            state.songId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (state.status) {
            MediaStoreDownloadManager.DownloadState.Status.QUEUED -> "Queued"
            MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING -> "Downloading"
            else -> ""
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$artist • $statusText")
            .setSmallIcon(R.drawable.download)
            .setProgress(
                100,
                progress,
                state.progress == 0f && state.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.close,
                "Cancel",
                cancelPendingIntent
            )
            .setOnlyAlertOnce(true)

        if (asGroupChild) {
            builder
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(false)
        }

        return builder.build()
    }

    private suspend fun resolveSongInfo(songId: String): Pair<String, String> {
        songInfoCache[songId]?.let { return it }

        val song = database.song(songId).first()
        val title = song?.song?.title ?: "Unknown"
        val artist = song?.artists?.firstOrNull()?.name ?: "Unknown Artist"
        val value = title to artist
        songInfoCache[songId] = value
        return value
    }

    private fun createMultipleDownloadsNotification(
        downloads: List<MediaStoreDownloadManager.DownloadState>
    ): Notification {
        val downloadingCount =
            downloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
        val queuedCount =
            downloads.count { it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED }

        val title = when {
            downloadingCount > 0 && queuedCount > 0 ->
                "Downloading $downloadingCount, $queuedCount queued"

            downloadingCount > 0 ->
                "Downloading $downloadingCount ${if (downloadingCount == 1) "song" else "songs"}"

            else ->
                "$queuedCount ${if (queuedCount == 1) "song" else "songs"} queued"
        }

        // Calculate average progress
        val avgProgress = downloads
            .filter { it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING }
            .map { it.progress }
            .average()
            .let { if (it.isNaN()) 0.0 else it }
            .toFloat()

        val cancelAllIntent = Intent(this, MediaStoreDownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL_DOWNLOADS
        }
        val cancelAllPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Expand to manage each download")
            .setSmallIcon(R.drawable.download)
            .setProgress(100, (avgProgress * 100).toInt(), avgProgress == 0f)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.close,
                "Cancel all",
                cancelAllPendingIntent
            )
            .build()
    }

    private fun notificationIdForSong(songId: String): Int {
        val positiveHash = songId.hashCode() and Int.MAX_VALUE
        return 1_000 + (positiveHash % 1_000_000)
    }

    private fun clearChildNotifications() {
        cancelStaleChildNotifications(emptySet())
    }

    private fun cancelStaleChildNotifications(currentChildIds: Set<Int>) {
        val staleIds = activeChildNotificationIds.filter { it !in currentChildIds }
        staleIds.forEach { notificationManager.cancel(it) }
        activeChildNotificationIds.clear()
        activeChildNotificationIds.addAll(currentChildIds)
    }
}
