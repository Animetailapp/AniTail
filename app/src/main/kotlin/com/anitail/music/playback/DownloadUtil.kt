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
import androidx.media3.exoplayer.offline.DownloadService
import com.anitail.innertube.YouTube
import com.anitail.music.constants.AudioQuality
import com.anitail.music.constants.AudioQualityKey
import com.anitail.music.constants.CustomDownloadPathEnabledKey
import com.anitail.music.constants.CustomDownloadPathUriKey
import com.anitail.music.constants.MaxDownloadSpeedKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.FormatEntity
import com.anitail.music.db.entities.Song
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.di.DownloadCache
import com.anitail.music.utils.DownloadExportHelper
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.booleanPreference
import com.anitail.music.utils.enumPreference
import com.anitail.music.utils.stringPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadUtil
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    val mediaStoreDownloadManager: MediaStoreDownloadManager,
    private val downloadExportHelper: DownloadExportHelper,
) {
    companion object {
        private const val MAX_PARALLEL_CACHE_DOWNLOADS_TURBO = 10
        private const val MAX_PARALLEL_CACHE_DOWNLOADS_BALANCED_UNMETERED = 3
        private const val MAX_PARALLEL_CACHE_DOWNLOADS_BALANCED_METERED = 2
    }

    sealed class MediaStoreCollectionStatus {
        object NotDownloaded : MediaStoreCollectionStatus()
        object Completed : MediaStoreCollectionStatus()
        data class Downloading(val progress: Float) : MediaStoreCollectionStatus()
        object Failed : MediaStoreCollectionStatus()
    }

    private data class CachedMediaStoreDownload(
        val state: MediaStoreDownloadManager.DownloadState,
        val download: Download,
    )

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val customDownloadPathEnabled by booleanPreference(context, CustomDownloadPathEnabledKey, false)
    private val customDownloadPathUri by stringPreference(context, CustomDownloadPathUriKey, "")
    private val maxDownloadSpeedEnabled by booleanPreference(context, MaxDownloadSpeedKey, true)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val songUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val targetItagOverride = ConcurrentHashMap<String, Int>()
    private val pendingTargetCacheReset = ConcurrentHashMap<String, Boolean>()
    private val exportJobs = ConcurrentHashMap<String, Job>()
    private val mediaStoreDownloadCache = ConcurrentHashMap<String, CachedMediaStoreDownload>()
    private val downloadExecutor = Executor { command ->
        scope.launch(Dispatchers.IO) { command.run() }
    }

    private fun maxParallelCacheDownloadsForCurrentNetwork(): Int {
        val isMetered = runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(true)
        val parallelDownloads = if (maxDownloadSpeedEnabled) {
            MAX_PARALLEL_CACHE_DOWNLOADS_TURBO
        } else {
            if (isMetered) {
                MAX_PARALLEL_CACHE_DOWNLOADS_BALANCED_METERED
            } else {
                MAX_PARALLEL_CACHE_DOWNLOADS_BALANCED_UNMETERED
            }
        }
        Timber.d(
            "Cache download parallelism selected: %d (maxSpeed=%s, metered=%s)",
            parallelDownloads,
            maxDownloadSpeedEnabled,
            isMetered
        )
        return parallelDownloads
    }

    // Legacy cache downloads (for compatibility)
    private val cacheDownloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    // Unified downloads combining cache and MediaStore
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    fun setTargetItag(songId: String, itag: Int) {
        targetItagOverride[songId] = itag
        pendingTargetCacheReset[songId] = true
        mediaStoreDownloadManager.setTargetItag(songId, itag)
        invalidateUrl(songId)
    }

    fun clearTargetItag(songId: String) {
        targetItagOverride.remove(songId)
        pendingTargetCacheReset.remove(songId)
        mediaStoreDownloadManager.setTargetItag(songId, 0)
    }

    fun invalidateUrl(songId: String) {
        songUrlCache.remove(songId)
    }

    private fun scheduleCustomPathExport(songId: String) {
        if (!customDownloadPathEnabled || customDownloadPathUri.isEmpty()) return

        exportJobs[songId]?.takeIf { it.isActive }?.let { return }
        exportJobs.remove(songId)

        exportJobs[songId] = scope.launch {
            try {
                val existingUri = database.getDownloadUri(songId)
                if (!existingUri.isNullOrBlank() && downloadExportHelper.verifyFileAccess(
                        existingUri
                    )
                ) {
                    return@launch
                }
                downloadExportHelper.exportToCustomPath(songId, customDownloadPathUri)
            } catch (e: Exception) {
                Timber.e(e, "Custom path export failed for %s", songId)
            } finally {
                exportJobs.remove(songId)
            }
        }
    }

    private fun cancelCustomPathExport(songId: String) {
        exportJobs.remove(songId)?.cancel()
    }

    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            CacheDataSource
                .Factory()
                .setCache(downloadCache)
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

            val targetItag = targetItagOverride[mediaId] ?: 0
            val hasTargetItag = targetItag > 0

            if (hasTargetItag && pendingTargetCacheReset.remove(mediaId) != null) {
                songUrlCache.remove(mediaId)
                try {
                    if (downloadCache.getCachedSpans(mediaId).isNotEmpty()) {
                        downloadCache.removeResource(mediaId)
                    }
                } catch (_: Exception) {
                }
            }

            if (downloadCache.isCached(mediaId, dataSpec.position, length)) {
                return@Factory dataSpec
            }

            // Keep URL cache while it is still valid.
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            val playbackData =
                fetchPlaybackDataBlocking(mediaId, targetItag) ?: error("Playback data unavailable")
            val format = playbackData.format
            val resolvedContentLength = format.contentLength ?: 0L

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = resolvedContentLength,
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

            val streamUrl = playbackData.streamUrl

            val streamExpiryMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
            songUrlCache[mediaId] = streamUrl to streamExpiryMs
            dataSpec.withUri(streamUrl.toUri())
        }

    private fun fetchPlaybackDataBlocking(
        mediaId: String,
        targetItag: Int = 0,
    ): YTPlayerUtils.PlaybackData? {
        val future = CompletableFuture<YTPlayerUtils.PlaybackData?>()
        scope.launch {
            val data =
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    targetItag = targetItag,
                ).getOrNull()
            future.complete(data)
        }
        return try {
            future.get(15, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
    }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            downloadExecutor
        ).apply {
            maxParallelDownloads = maxParallelCacheDownloadsForCurrentNetwork()
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

                        val songId = download.request.id
                        when (download.state) {
                            Download.STATE_COMPLETED -> {
                                clearTargetItag(songId)
                                scheduleCustomPathExport(songId)
                            }

                            Download.STATE_FAILED,
                            Download.STATE_STOPPED -> {
                                clearTargetItag(songId)
                                cancelCustomPathExport(songId)
                            }

                            Download.STATE_REMOVING -> {
                                clearTargetItag(songId)
                                cancelCustomPathExport(songId)
                                scope.launch {
                                    try {
                                        downloadExportHelper.deleteFromCustomPath(songId)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed removing exported file for %s", songId)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

    init {
        // Initialize cache downloads off the main thread to avoid startup jank/ANR.
        scope.launch(Dispatchers.IO) {
            val result = mutableMapOf<String, Download>()
            runCatching {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        result[cursor.download.request.id] = cursor.download
                    }
                }
            }.onFailure {
                Timber.w(it, "Failed to load download index during initialization")
            }
            cacheDownloads.value = result
        }

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
                    merged[songId] = getOrCreateMediaStoreDownload(songId, downloadState)
                }
                val cacheIterator = mediaStoreDownloadCache.keys.iterator()
                while (cacheIterator.hasNext()) {
                    val cachedSongId = cacheIterator.next()
                    if (!mediaStore.containsKey(cachedSongId)) {
                        cacheIterator.remove()
                    }
                }

                merged.toMap()
            }.collect { mergedDownloads ->
                downloads.update { currentDownloads ->
                    if (hasSameDownloadReferences(currentDownloads, mergedDownloads)) {
                        currentDownloads
                    } else {
                        mergedDownloads
                    }
                }
            }
        }
    }

    private fun getOrCreateMediaStoreDownload(
        songId: String,
        state: MediaStoreDownloadManager.DownloadState,
    ): Download {
        val cached = mediaStoreDownloadCache[songId]
        if (cached != null && cached.state == state) {
            return cached.download
        }

        val download = state.toDownload()
        mediaStoreDownloadCache[songId] = CachedMediaStoreDownload(state, download)
        return download
    }

    private fun hasSameDownloadReferences(
        current: Map<String, Download>,
        next: Map<String, Download>,
    ): Boolean {
        if (current.size != next.size) return false
        current.forEach { (songId, download) ->
            if (next[songId] !== download) return false
        }
        return true
    }

    fun getDownloadState(songIds: Collection<String>): Flow<Int> {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) {
            return flowOf(Download.STATE_STOPPED)
        }

        return downloads
            .map { currentDownloads ->
                calculateDownloadState(uniqueSongIds, currentDownloads)
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun getDownloadState(songIdsFlow: Flow<List<String>>): Flow<Int> =
        songIdsFlow
            .map { it.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { songIds -> getDownloadState(songIds) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun getMediaStoreDownloads(songIds: Collection<String>): Flow<Map<String, MediaStoreDownloadManager.DownloadState>> {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) {
            return flowOf(emptyMap())
        }

        return mediaStoreDownloadManager.downloadStates
            .map { currentStates ->
                buildMap(uniqueSongIds.size) {
                    uniqueSongIds.forEach { songId ->
                        currentStates[songId]?.let { state ->
                            put(songId, state)
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun getMediaStoreDownloads(songIdsFlow: Flow<List<String>>): Flow<Map<String, MediaStoreDownloadManager.DownloadState>> =
        songIdsFlow
            .map { it.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { songIds -> getMediaStoreDownloads(songIds) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun getMediaStoreCollectionStatus(songs: Collection<Song>): Flow<MediaStoreCollectionStatus> {
        val distinctSongs = songs
            .groupBy { it.id }
            .mapNotNull { (_, groupedSongs) -> groupedSongs.firstOrNull() }
        if (distinctSongs.isEmpty()) {
            return flowOf(MediaStoreCollectionStatus.NotDownloaded)
        }

        return getMediaStoreDownloads(distinctSongs.map { it.id })
            .map { states ->
                calculateMediaStoreCollectionStatus(
                    songs = distinctSongs,
                    states = states
                )
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun calculateMediaStoreCollectionStatus(
        songs: List<Song>,
        states: Map<String, MediaStoreDownloadManager.DownloadState>,
    ): MediaStoreCollectionStatus {
        if (songs.isEmpty()) return MediaStoreCollectionStatus.NotDownloaded

        val songStates = songs.mapNotNull { states[it.id] }
        val allPersistedInMediaStore = songs.all { song ->
            !song.song.isLocal &&
                hasAccessiblePersistedUri(song)
        }

        return when {
            allPersistedInMediaStore -> MediaStoreCollectionStatus.Completed
            songStates.isEmpty() -> MediaStoreCollectionStatus.NotDownloaded
            songStates.all { it.status == MediaStoreDownloadManager.DownloadState.Status.COMPLETED } ->
                MediaStoreCollectionStatus.Completed

            songStates.any {
                it.status == MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING ||
                    it.status == MediaStoreDownloadManager.DownloadState.Status.QUEUED
            } -> {
                val totalBytes = songStates.sumOf { it.totalBytes.coerceAtLeast(0L) }
                val downloadedBytes = songStates.sumOf { state ->
                    when (state.status) {
                        MediaStoreDownloadManager.DownloadState.Status.COMPLETED ->
                            state.totalBytes.coerceAtLeast(0L)

                        MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING,
                        MediaStoreDownloadManager.DownloadState.Status.QUEUED ->
                            state.bytesDownloaded.coerceAtLeast(0L)

                        else -> 0L
                    }
                }
                val totalProgress = if (totalBytes > 0L) {
                    (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    songStates.sumOf { state ->
                        when (state.status) {
                            MediaStoreDownloadManager.DownloadState.Status.COMPLETED -> 1.0
                            MediaStoreDownloadManager.DownloadState.Status.DOWNLOADING -> state.progress.toDouble()
                            else -> 0.0
                        }
                    } / songs.size
                }
                MediaStoreCollectionStatus.Downloading(totalProgress.toFloat())
            }

            songStates.any { it.status == MediaStoreDownloadManager.DownloadState.Status.FAILED } ->
                MediaStoreCollectionStatus.Failed

            else -> MediaStoreCollectionStatus.NotDownloaded
        }
    }

    private fun hasAccessiblePersistedUri(song: Song): Boolean {
        val mediaStoreUri = song.song.mediaStoreUri
        if (!mediaStoreUri.isNullOrBlank() && downloadExportHelper.verifyFileAccess(mediaStoreUri)) {
            return true
        }

        val downloadUri = song.song.downloadUri
        if (!downloadUri.isNullOrBlank() && downloadExportHelper.verifyFileAccess(downloadUri)) {
            return true
        }

        return false
    }

    private fun calculateDownloadState(
        songIds: Set<String>,
        currentDownloads: Map<String, Download>,
    ): Int {
        if (songIds.isEmpty()) return Download.STATE_STOPPED
        if (songIds.size > currentDownloads.size) return Download.STATE_STOPPED

        val allCompleted = songIds.all { songId ->
            currentDownloads[songId]?.state == Download.STATE_COMPLETED
        }
        if (allCompleted) return Download.STATE_COMPLETED

        val allQueuedOrDownloadingOrCompleted = songIds.all { songId ->
            when (currentDownloads[songId]?.state) {
                Download.STATE_QUEUED,
                Download.STATE_DOWNLOADING,
                Download.STATE_COMPLETED -> true

                else -> false
            }
        }
        return if (allQueuedOrDownloadingOrCompleted) {
            Download.STATE_DOWNLOADING
        } else {
            Download.STATE_STOPPED
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

    fun getDownload(songId: String): Flow<Download?> =
        downloads
            .map { it[songId] }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    // MediaStore download methods
    fun getMediaStoreDownload(songId: String): Flow<MediaStoreDownloadManager.DownloadState?> =
        mediaStoreDownloadManager.downloadStates
            .map { it[songId] }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun downloadToMediaStore(song: com.anitail.music.db.entities.Song, targetItag: Int? = null) {
        if (song.song.isLocal || song.id.startsWith("LOCAL_")) {
            Timber.d("Skipping MediaStore download for local song: %s", song.id)
            return
        }
        targetItag?.let { mediaStoreDownloadManager.setTargetItag(song.id, it) }
        mediaStoreDownloadManager.downloadSongs(listOf(song))
    }

    fun downloadSongsToMediaStore(
        songs: Collection<com.anitail.music.db.entities.Song>,
        targetItag: Int? = null,
    ) {
        val remoteSongs = songs.filterNot { it.song.isLocal || it.id.startsWith("LOCAL_") }
        if (remoteSongs.isEmpty()) {
            Timber.d("Skipping MediaStore batch download: no remote songs to download")
            return
        }

        targetItag?.let { itag ->
            remoteSongs.forEach { song ->
                mediaStoreDownloadManager.setTargetItag(song.id, itag)
            }
        }

        mediaStoreDownloadManager.downloadSongs(remoteSongs)
    }

    suspend fun downloadToMediaStoreWithMetadataPreference(song: com.anitail.music.db.entities.Song) {
        if (song.song.isLocal || song.id.startsWith("LOCAL_")) {
            Timber.d("Skipping metadata-preferred download for local song: %s", song.id)
            return
        }

        val preferredItag = YTPlayerUtils.getAllAvailableAudioFormats(song.id)
            .getOrElse { error ->
                Timber.tag("DownloadUtil").w(error, "Failed to resolve formats for metadata preference")
                emptyList()
            }
            .firstOrNull { it.supportsMetadata }
            ?.itag

        if (preferredItag != null) {
            Timber.d("Auto-like download using metadata-capable itag %d for %s", preferredItag, song.id)
        } else {
            Timber.w("No metadata-capable format found for %s, using default selection", song.id)
        }

        downloadToMediaStore(song, targetItag = preferredItag)
    }

    fun cancelMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.cancelDownloads(listOf(songId))
    }

    fun cancelMediaStoreDownloads(songIds: Collection<String>) {
        mediaStoreDownloadManager.cancelDownloads(songIds)
    }

    fun retryMediaStoreDownload(songId: String) {
        mediaStoreDownloadManager.retryDownloads(listOf(songId))
    }

    fun retryMediaStoreDownloads(songIds: Collection<String>) {
        mediaStoreDownloadManager.retryDownloads(songIds)
    }

    fun removeDownload(songId: String) {
        removeDownloads(listOf(songId))
    }

    fun removeDownloads(songIds: Collection<String>) {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            val mediaStoreSongIdsToRemove = uniqueSongIds.filter { songId ->
                val hasInMemoryMediaStoreState =
                    mediaStoreDownloadManager.downloadStates.value[songId] != null
                val hasPersistedMediaStoreUri = runCatching {
                    val dbSong = database.getSongByIdBlocking(songId)?.song
                    dbSong != null &&
                        (!dbSong.mediaStoreUri.isNullOrEmpty() || !dbSong.downloadUri.isNullOrEmpty())
                }.getOrDefault(false)
                hasInMemoryMediaStoreState || hasPersistedMediaStoreUri
            }

            if (mediaStoreSongIdsToRemove.isNotEmpty()) {
                mediaStoreDownloadManager.removeDownloads(mediaStoreSongIdsToRemove)
            }

            uniqueSongIds.forEach { songId ->
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    songId,
                    false,
                )
            }
        }
    }
}
