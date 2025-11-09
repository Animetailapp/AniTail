package com.anitail.music.playback


import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.anitail.innertube.YouTube
import com.anitail.music.constants.AudioQuality
import com.anitail.music.constants.AudioQualityKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.FormatEntity
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.di.DownloadCache
import com.anitail.music.di.PlayerCache
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
    val mediaStoreDownloadManager: MediaStoreDownloadManager,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    // Legacy cache downloads (for compatibility)
    private val cacheDownloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    // Unified downloads combining cache and MediaStore
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(
                    OkHttpDataSource.Factory(
                        OkHttpClient.Builder()
                            .proxy(YouTube.proxy)
                            .proxyAuthenticator { _, response ->
                                YouTube.proxyAuth?.let { auth ->
                                    response.request.newBuilder()
                                        .header("Proxy-Authorization", auth)
                                        .build()
                                } ?: response.request
                            }
                            .build(),
                    ),
                ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            val length = if (dataSpec.length >= 0) dataSpec.length else 1

            if (playerCache.isCached(mediaId, dataSpec.position, length)) {
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.second < System.currentTimeMillis() }?.let {
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrThrow()
            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength!!,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )

                val now = LocalDateTime.now()
                val existing = getSongByIdBlocking(mediaId)?.song

                val updatedSong = if (existing != null) {
                    if (existing.dateDownload == null) existing.copy(dateDownload = now) else existing
                } else {
                    SongEntity(
                        id = mediaId,
                        title = playbackData.videoDetails?.title ?: "Unknown",
                        duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                        thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                        dateDownload = now
                    )
                }

                upsert(updatedSong)
            }

            val streamUrl = playbackData.streamUrl.let {
                "${it}&range=0-${format.contentLength ?: 10000000}"
            }

            songUrlCache[mediaId] = streamUrl to playbackData.streamExpiresInSeconds * 1000L
            dataSpec.withUri(streamUrl.toUri())
        }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        cacheDownloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }
                    }
                }
            )
        }

    init {
        // Initialize cache downloads
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        cacheDownloads.value = result

        // Merge cache downloads and MediaStore downloads into unified flow
        scope.launch {
            combine(
                cacheDownloads,
                mediaStoreDownloadManager.downloadStates
            ) { cache, mediaStore ->
                // Start with cache downloads
                val merged = cache.toMutableMap()

                // Add MediaStore downloads as fake Download objects
                mediaStore.forEach { (songId, downloadState) ->
                    merged[songId] = downloadState.toDownload()
                }

                merged.toMap()
            }.collect { mergedDownloads ->
                downloads.value = mergedDownloads
            }
        }
    }

    // Convert MediaStore DownloadState to Media3 Download (for UI compatibility)
    private fun MediaStoreDownloadManager.DownloadState.toDownload(): Download {
        val state = when (this.status) {
            MediaStoreDownloadManager.DownloadState.Status.QUEUED -> Download.STATE_QUEUED
            MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING -> Download.STATE_DOWNLOADING
            MediaStoreDownloadManager.DownloadState.Status.COMPLETED -> Download.STATE_COMPLETED
            MediaStoreDownloadManager.DownloadState.Status.FAILED -> Download.STATE_FAILED
            MediaStoreDownloadManager.DownloadState.Status.CANCELLED -> Download.STATE_STOPPED
        }

        val downloadRequest =
            androidx.media3.exoplayer.offline.DownloadRequest.Builder(songId, songId.toUri())
                .setCustomCacheKey(songId)
                .build()

        return Download(
            downloadRequest,
            state,
            /* startTimeMs = */
            0,
            /* updateTimeMs = */
            System.currentTimeMillis(),
            /* contentLength = */
            totalBytes,
            /* stopReason = */
            0,
            /* failureReason = */
            if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE
        )
    }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    // MediaStore download methods
    fun getMediaStoreDownload(songId: String): Flow<MediaStoreDownloadManager.DownloadState?> =
        mediaStoreDownloadManager.downloadStates.map { it[songId] }

    fun getAllMediaStoreDownloads(): StateFlow<Map<String, MediaStoreDownloadManager.DownloadState>> =
        mediaStoreDownloadManager.downloadStates

    fun downloadToMediaStore(song: com.anitail.music.db.entities.Song) {
        mediaStoreDownloadManager.downloadSong(song)
    }

    fun cancelMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.cancelDownload(songId)
    }

    fun retryMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.retryDownload(songId)
    }


}
