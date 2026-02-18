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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "mediastore_download"
        private const val NOTIFICATION_CHANNEL_NAME = "Music Downloads"
        private const val NOTIFICATION_ID = 100

        const val ACTION_CANCEL_DOWNLOAD = "com.anitail.music.CANCEL_DOWNLOAD"
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
                .onEach { states ->
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
                .collect()
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
        }

        if (activeDownloads.isEmpty()) {
            return
        }

        val notification = if (activeDownloads.size == 1) {
            // Single download - show detailed progress
            createSingleDownloadNotification(activeDownloads.first())
        } else {
            // Multiple downloads - show summary
            createMultipleDownloadsNotification(activeDownloads)
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun createSingleDownloadNotification(
        state: MediaStoreDownloadManager.DownloadState
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
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
            .build()
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Music downloads in progress")
            .setSmallIcon(R.drawable.download)
            .setProgress(100, (avgProgress * 100).toInt(), avgProgress == 0f)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
