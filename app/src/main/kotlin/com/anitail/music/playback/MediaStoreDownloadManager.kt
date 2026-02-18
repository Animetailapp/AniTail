package com.anitail.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.anitail.music.constants.AudioQuality
import com.anitail.music.constants.AudioQualityKey
import com.anitail.music.constants.CustomDownloadPathEnabledKey
import com.anitail.music.constants.CustomDownloadPathUriKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.utils.DownloadExportHelper
import com.anitail.music.utils.MediaStoreHelper
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.booleanPreference
import com.anitail.music.utils.enumPreference
import com.anitail.music.utils.stringPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow

/**
 * Download manager that uses MediaStore to save music files to the public Music/Anitail folder.
 *
 * Features:
 * - Downloads audio streams from YouTube via InnerTube API
 * - Saves files using MediaStore for Android 10+ compatibility
 * - Supports concurrent downloads (max 3 simultaneous)
 * - Retry logic with exponential backoff
 * - Progress tracking with StateFlow
 * - Download queue management
 * - Automatic cleanup on failure
 */
@Singleton
class MediaStoreDownloadManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadExportHelper: DownloadExportHelper,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mediaStoreHelper = MediaStoreHelper(context)
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val customDownloadPathEnabled by
        booleanPreference(context, CustomDownloadPathEnabledKey, false)
    private val customDownloadPathUri by stringPreference(context, CustomDownloadPathUriKey, "")

    // Concurrent download limiter (max 3 simultaneous downloads)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // Mutex to protect against duplicate downloads from concurrent requests
    private val downloadMutex = Mutex()

    // Download state tracking
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Download queue
    private val downloadQueue = mutableListOf<Song>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()
    private val targetItagOverride = ConcurrentHashMap<String, Int>()

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2.0
        private const val UNKNOWN_ARTIST_NAME = "Unknown Artist"
        private const val MIN_PROGRESS_UPDATE_BYTES = 512 * 1024L
        private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 1000L
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 120_000
    }

    /**
     * Download state for a song
     */
    data class DownloadState(
        val songId: String,
        val status: Status,
        val progress: Float = 0f,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val error: String? = null,
        val retryAttempt: Int = 0,
    ) {
        enum class Status {
            QUEUED,
            DOWNLOADING,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }

    /**
     * Start downloading a song
     *
     * @param song The song to download
     */
    fun downloadSong(song: Song) = downloadSongs(listOf(song))

    fun downloadSongs(songs: Collection<Song>) {
        if (songs.isEmpty()) return

        scope.launch {
            val songsToQueue = preprocessSongsForQueue(songs)
            if (songsToQueue.isEmpty()) return@launch

            val queuedAny = downloadMutex.withLock {
                enqueueSongsLocked(songsToQueue)
            }
            if (!queuedAny) return@launch

            MediaStoreDownloadService.start(context)
            repeat(MAX_CONCURRENT_DOWNLOADS) {
                processQueue()
            }
        }
    }

    fun setTargetItag(songId: String, itag: Int) {
        if (itag > 0) {
            targetItagOverride[songId] = itag
        } else {
            targetItagOverride.remove(songId)
        }
    }

    /**
     * Cancel a download
     *
     * @param songId The ID of the song to cancel
     */
    fun cancelDownload(songId: String) = cancelDownloads(listOf(songId))

    fun cancelDownloads(songIds: Collection<String>) {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) return

        scope.launch {
            stopAndCleanupDownloads(uniqueSongIds)
        }
    }

    /**
     * Remove a downloaded/cancelled song and clean up any persisted MediaStore + database state.
     */
    fun removeDownload(songId: String) = removeDownloads(listOf(songId))

    fun removeDownloads(songIds: Collection<String>) {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) return

        scope.launch {
            stopAndCleanupDownloads(uniqueSongIds)
        }
    }

    /**
     * Retry a failed download
     *
     * @param songId The ID of the song to retry
     */
    fun retryDownload(songId: String) = retryDownloads(listOf(songId))

    fun retryDownloads(songIds: Collection<String>) {
        val uniqueSongIds = songIds.toSet()
        if (uniqueSongIds.isEmpty()) return

        scope.launch {
            val songsToRetry = mutableListOf<Song>()

            uniqueSongIds.forEach { songId ->
                val song = database.song(songId).first()
                if (song == null) {
                    Timber.Forest.e("Song not found in database: $songId")
                    return@forEach
                }
                songsToRetry += song
            }

            if (songsToRetry.isNotEmpty()) {
                downloadSongs(songsToRetry)
            }
        }
    }

    /**
     * Process the download queue
     */
    private fun processQueue() {
        if (!downloadSemaphore.tryAcquire()) {
            return
        }

        val song = synchronized(downloadQueue) {
            if (downloadQueue.isNotEmpty()) downloadQueue.removeAt(0) else null
        }
        if (song == null) {
            downloadSemaphore.release()
            return
        }

        val job = scope.launch {
            try {
                performDownload(song)
            } finally {
                downloadSemaphore.release()
                activeDownloads.remove(song.id)

                processQueue()
            }
        }
        activeDownloads[song.id] = job
    }

    private suspend fun stopAndCleanupDownloads(songIds: Set<String>) {
        songIds.forEach { songId ->
            markCancelRequested(songId)
            activeDownloads.remove(songId)?.cancel()
        }

        synchronized(downloadQueue) {
            downloadQueue.removeAll { it.id in songIds }
        }

        removePersistedDownloads(songIds)
        clearDownloadStates(songIds)
    }

    private suspend fun enqueueSongsLocked(songs: Collection<Song>): Boolean {
        var queuedAny = false

        for (song in songs) {
            clearCancelRequested(song.id)

            val currentState = _downloadStates.value[song.id]
            if (currentState?.status == DownloadState.Status.DOWNLOADING ||
                currentState?.status == DownloadState.Status.COMPLETED ||
                currentState?.status == DownloadState.Status.QUEUED
            ) {
                Timber.Forest.d(
                    "Song ${song.song.title} is already queued/downloading/completed (status: ${currentState.status})"
                )
                continue
            }

            val enqueued = synchronized(downloadQueue) {
                if (downloadQueue.any { it.id == song.id }) {
                    false
                } else {
                    downloadQueue.add(song)
                    true
                }
            }
            if (!enqueued) continue

            queuedAny = true
            updateDownloadState(
                song.id,
                DownloadState(
                    songId = song.id,
                    status = DownloadState.Status.QUEUED
                )
            )
        }

        return queuedAny
    }

    private suspend fun preprocessSongsForQueue(songs: Collection<Song>): List<Song> {
        val uniqueSongs = songs
            .groupBy { it.id }
            .mapNotNull { (_, sameIdSongs) -> sameIdSongs.firstOrNull() }
        val customPathWritable = if (customDownloadPathEnabled) {
            isCustomPathWritable()
        } else {
            false
        }

        val songsToQueue = ArrayList<Song>(uniqueSongs.size)
        for (song in uniqueSongs) {
            if (customDownloadPathEnabled && !customPathWritable) {
                updateDownloadState(
                    song.id,
                    DownloadState(
                        songId = song.id,
                        status = DownloadState.Status.FAILED,
                        error = "Custom folder is unavailable or read-only"
                    )
                )
                continue
            }

            if (!song.song.mediaStoreUri.isNullOrEmpty()) {
                Timber.Forest.d(
                    "Song ${song.song.title} is already downloaded in database: ${song.song.mediaStoreUri}"
                )
                val movedToCustomPath = exportToCustomPathIfEnabled(
                    song.id,
                    sourceMediaStoreUri = song.song.mediaStoreUri
                )
                if (!movedToCustomPath && customDownloadPathEnabled) {
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.FAILED,
                            error = "Unable to move existing file to custom folder"
                        )
                    )
                } else {
                    clearDownloadState(song.id)
                }
                continue
            }

            if (!song.song.downloadUri.isNullOrEmpty() &&
                downloadExportHelper.verifyFileAccess(song.song.downloadUri)
            ) {
                if (song.song.mediaStoreUri != song.song.downloadUri) {
                    markSongAsDownloaded(song.id, song.song.downloadUri)
                }
                clearDownloadState(song.id)
                continue
            }

            if (!customDownloadPathEnabled) {
                val existingFile = mediaStoreHelper.findExistingFile(
                    title = song.song.title,
                    artist = resolvePrimaryArtist(song)
                )
                if (existingFile != null) {
                    Timber.Forest.d("Song ${song.song.title} already exists in MediaStore: $existingFile")
                    markSongAsDownloaded(song.id, existingFile.toString())
                    clearDownloadState(song.id)
                    continue
                }
            }

            songsToQueue += song
        }

        return songsToQueue
    }

    /**
     * Perform the actual download with retry logic
     */
    private suspend fun performDownload(song: Song): Unit = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${song.id}.part")
        var retryAttempt = 0
        var lastError: Exception? = null
        var lockedItag = targetItagOverride[song.id] ?: 0

        runCatching {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        try {
            while (retryAttempt <= MAX_RETRY_ATTEMPTS) {
                if (isCancelRequested(song.id)) {
                    clearDownloadState(song.id)
                    return@withContext
                }
                try {
                    val alreadyDownloadedBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
                    val previousState = _downloadStates.value[song.id]
                    val previousTotalBytes = previousState?.totalBytes ?: 0L
                    val initialProgress = if (previousTotalBytes > 0L) {
                        (alreadyDownloadedBytes.toFloat() / previousTotalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        previousState?.progress ?: 0f
                    }

                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.DOWNLOADING,
                            progress = initialProgress,
                            bytesDownloaded = alreadyDownloadedBytes,
                            totalBytes = previousTotalBytes,
                            retryAttempt = retryAttempt
                        )
                    )

                    Timber.Forest.d(
                        "Starting download for: ${song.song.title} (attempt ${retryAttempt + 1})"
                    )

                    // Get playback URL from YouTube using YTPlayerUtils
                    val playbackData = YTPlayerUtils.playerResponseForPlayback(
                        videoId = song.id,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        targetItag = lockedItag
                    ).getOrThrow()
                    if (lockedItag == 0) {
                        lockedItag = playbackData.format.itag
                    }

                    val format = playbackData.format
                    val downloadUrl = playbackData.streamUrl
                    val expectedContentLength = format.contentLength?.takeIf { it > 0L }
                    val resumeFromBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L

                    downloadFile(
                        url = downloadUrl,
                        outputFile = tempFile,
                        songId = song.id,
                        startByte = resumeFromBytes,
                        expectedContentLength = expectedContentLength
                    )

                    val downloadedBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
                    if (downloadedBytes == 0L) {
                        throw Exception("Download failed - temp file not created or empty")
                    }

                    if (expectedContentLength != null && downloadedBytes < expectedContentLength) {
                        throw Exception(
                            "Incomplete download ($downloadedBytes/$expectedContentLength bytes)"
                        )
                    }

                    val title = song.song.title
                    val artist = resolvePrimaryArtist(song)
                    val album = song.album?.title
                    val duration = song.song.duration.takeIf { it > 0 }?.times(1000L)
                    val year = song.song.year
                    val mimeType = format.mimeType.substringBefore(";").trim().ifBlank {
                        "audio/mp4"
                    }
                    val extension = extensionFromMimeType(mimeType)
                    val persistedUri = if (customDownloadPathEnabled) {
                        saveFileToCustomPath(
                            song = song,
                            tempFile = tempFile,
                            mimeType = mimeType,
                            extension = extension
                        )
                    } else {
                        val fileName = "$artist - $title.$extension"
                        mediaStoreHelper.saveFileToMediaStore(
                            tempFile = tempFile,
                            fileName = fileName,
                            mimeType = mimeType,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            year = year
                        )?.toString()
                    }

                    if (persistedUri.isNullOrBlank()) {
                        val destination = if (customDownloadPathEnabled) {
                            "custom folder"
                        } else {
                            "MediaStore"
                        }
                        throw Exception("Failed to save file to $destination")
                    }

                    if (isCancelRequested(song.id)) {
                        deletePersistedUri(persistedUri)
                        clearDownloadState(song.id)
                        return@withContext
                    }
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )
                    markSongAsDownloaded(song.id, persistedUri)
                    clearDownloadState(song.id)
                    return@withContext
                } catch (e: CancellationException) {
                    clearDownloadState(song.id)
                    return@withContext
                } catch (e: Exception) {
                    if (isCancelRequested(song.id)) {
                        clearDownloadState(song.id)
                        return@withContext
                    }
                    lastError = e
                    Timber.Forest.e(
                        e,
                        "Download failed for ${song.song.title} (attempt ${retryAttempt + 1}): ${e.message}"
                    )

                    if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
                        break
                    }

                    val delayMs: Long =
                        (INITIAL_RETRY_DELAY_MS * RETRY_BACKOFF_MULTIPLIER.pow(retryAttempt)).toLong()
                    val downloadedBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
                    val previousState = _downloadStates.value[song.id]
                    val previousTotalBytes = previousState?.totalBytes ?: 0L
                    val resumeProgress = if (previousTotalBytes > 0L) {
                        (downloadedBytes.toFloat() / previousTotalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        previousState?.progress ?: 0f
                    }

                    Timber.Forest.d("Retrying download in ${delayMs}ms from byte $downloadedBytes...")

                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.DOWNLOADING,
                            progress = resumeProgress,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = previousTotalBytes,
                            error = "Retrying... (${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS)",
                            retryAttempt = retryAttempt + 1
                        )
                    )

                    delay(delayMs)
                    retryAttempt++
                }
            }

            if (isCancelRequested(song.id)) {
                clearDownloadState(song.id)
                return@withContext
            }

            updateDownloadState(
                song.id,
                DownloadState(
                    songId = song.id,
                    status = DownloadState.Status.FAILED,
                    error = lastError?.message ?: "Unknown error",
                    retryAttempt = retryAttempt
                )
            )
        } finally {
            targetItagOverride.remove(song.id)
            runCatching {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Timber.Forest.d("Cleaned up temp file: ${tempFile.absolutePath}")
                }
            }.onFailure { error ->
                Timber.Forest.w(error, "Failed to delete temp file: ${tempFile.absolutePath}")
            }
        }
    }

    /**
     * Download a file from a URL to a temp file with progress tracking
     */
    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        songId: String,
        startByte: Long,
        expectedContentLength: Long?,
    ) =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection

            // Configure connection for YouTube
            connection.apply {
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                if (startByte > 0L) {
                    setRequestProperty("Range", "bytes=$startByte-")
                }
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }

            connection.connect()

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode == 416 &&
                expectedContentLength != null &&
                startByte >= expectedContentLength
            ) {
                return@withContext
            }
            if (responseCode !in 200..299) {
                throw Exception("HTTP error $responseCode: ${connection.responseMessage}")
            }

            val appendMode = startByte > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (startByte > 0L && !appendMode) {
                // Server ignored Range; restart from zero to avoid file corruption.
                runCatching { outputFile.delete() }
            }

            val serverContentLength = connection.contentLengthLong.takeIf { it > 0L }
            val totalBytes = expectedContentLength ?: serverContentLength?.let { length ->
                if (appendMode) startByte + length else length
            }
            var totalBytesRead = if (appendMode) startByte else 0L
            var lastProgressBytes = totalBytesRead
            var lastProgressAt = System.currentTimeMillis()
            var lastReportedProgress = if (totalBytes != null && totalBytes > 0L) {
                (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            connection.getInputStream().use { input ->
                FileOutputStream(outputFile, appendMode).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelRequested(songId)) {
                            throw CancellationException("Download cancelled: $songId")
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        if (totalBytes != null && totalBytes > 0L) {
                            val now = System.currentTimeMillis()
                            val shouldEmitProgress =
                                (totalBytesRead - lastProgressBytes) >= MIN_PROGRESS_UPDATE_BYTES ||
                                    (now - lastProgressAt) >= MIN_PROGRESS_UPDATE_INTERVAL_MS

                            if (shouldEmitProgress) {
                                val progress =
                                    (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                if (abs(progress - lastReportedProgress) >= 0.01f) {
                                    updateDownloadState(
                                        songId,
                                        DownloadState(
                                            songId = songId,
                                            status = DownloadState.Status.DOWNLOADING,
                                            progress = progress,
                                            bytesDownloaded = totalBytesRead,
                                            totalBytes = totalBytes
                                        )
                                    )
                                    lastReportedProgress = progress
                                }
                                lastProgressBytes = totalBytesRead
                                lastProgressAt = now
                            }
                        }
                    }
                }
            }

            if (totalBytes != null && totalBytes > 0L) {
                val finalProgress =
                    (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                updateDownloadState(
                    songId,
                    DownloadState(
                        songId = songId,
                        status = DownloadState.Status.DOWNLOADING,
                        progress = finalProgress,
                        bytesDownloaded = totalBytesRead,
                        totalBytes = totalBytes
                    )
                )
            }

            if (expectedContentLength != null && totalBytesRead < expectedContentLength) {
                throw Exception("Incomplete stream read ($totalBytesRead/$expectedContentLength bytes)")
            }

            Timber.Forest.d("Download completed: $totalBytesRead bytes written to ${outputFile.absolutePath}")
        }

    /**
     * Update the download state for a song
     */
    private fun updateDownloadState(songId: String, state: DownloadState) {
        _downloadStates.update { currentStates ->
            if (currentStates[songId] == state) {
                currentStates
            } else {
                currentStates + (songId to state)
            }
        }
    }

    private fun clearDownloadState(songId: String) {
        _downloadStates.update { currentStates ->
            if (songId in currentStates) currentStates - songId else currentStates
        }
    }

    private fun clearDownloadStates(songIds: Set<String>) {
        if (songIds.isEmpty()) return
        _downloadStates.update { currentStates ->
            val existingSongIds = songIds.filterTo(mutableSetOf()) { it in currentStates }
            if (existingSongIds.isEmpty()) currentStates else currentStates - existingSongIds
        }
    }

    /**
     * Mark a song as downloaded in the database with MediaStore URI
     */
    private suspend fun markSongAsDownloaded(songId: String, persistedUri: String) {
        if (isCancelRequested(songId)) {
            deletePersistedUri(persistedUri)
            return
        }

        val song = database.song(songId).first()
        if (song != null) {
            val isMediaStoreUri = isMediaStoreContentUri(persistedUri)
            database.query {
                database.upsert(
                    song.song.copy(
                        dateDownload = LocalDateTime.now(),
                        mediaStoreUri = persistedUri,
                        downloadUri = if (isMediaStoreUri) null else persistedUri,
                    )
                )
            }
            Timber.d("Marked song as downloaded: ${song.song.title}, URI: $persistedUri")
        }
    }

    private suspend fun removePersistedDownloads(songIds: Set<String>) {
        if (songIds.isEmpty()) return

        val songs = database.songsByIds(songIds.toList())
        val songsToPersist = mutableListOf<com.anitail.music.db.entities.SongEntity>()
        songs.forEach { song ->
            val mediaStoreUri = song.song.mediaStoreUri
            val downloadUri = song.song.downloadUri

            if (!mediaStoreUri.isNullOrEmpty()) {
                deletePersistedUri(mediaStoreUri)
            }
            if (!downloadUri.isNullOrEmpty() && downloadUri != mediaStoreUri) {
                deletePersistedUri(downloadUri)
            }

            if (!song.song.mediaStoreUri.isNullOrEmpty() ||
                !song.song.downloadUri.isNullOrEmpty() ||
                song.song.dateDownload != null
            ) {
                songsToPersist += song.song.copy(
                    mediaStoreUri = null,
                    downloadUri = null,
                    dateDownload = null,
                )
            }
        }

        if (songsToPersist.isNotEmpty()) {
            database.query {
                songsToPersist.forEach { songEntity ->
                    database.upsert(songEntity)
                }
            }
        }
    }

    private suspend fun exportToCustomPathIfEnabled(
        songId: String,
        sourceMediaStoreUri: String?
    ): Boolean {
        if (!customDownloadPathEnabled || customDownloadPathUri.isBlank()) return true
        val existingSong = database.song(songId).first()?.song
        val existingDownloadUri = existingSong?.downloadUri
        val alreadyOnCustomUri =
            !existingDownloadUri.isNullOrBlank() &&
                sourceMediaStoreUri == existingDownloadUri &&
                !existingDownloadUri.startsWith("content://media/") &&
                downloadExportHelper.verifyFileAccess(existingDownloadUri)
        if (alreadyOnCustomUri) return true

        val exportedUri = runCatching {
            downloadExportHelper.exportToCustomPath(songId, customDownloadPathUri)
        }.onFailure { error ->
            Timber.w(error, "Custom path export failed for MediaStore download %s", songId)
        }.getOrNull()

        if (exportedUri.isNullOrBlank()) return false

        promoteCustomUriAsPrimary(
            songId = songId,
            customUri = exportedUri,
            previousMediaStoreUri = sourceMediaStoreUri
        )
        return true
    }

    private suspend fun promoteCustomUriAsPrimary(
        songId: String,
        customUri: String,
        previousMediaStoreUri: String?,
    ) {
        val song = database.song(songId).first() ?: return
        val currentPrimaryUri = song.song.mediaStoreUri
        if (currentPrimaryUri != null && currentPrimaryUri != customUri) {
            deletePersistedUri(currentPrimaryUri)
        } else if (!previousMediaStoreUri.isNullOrBlank() && previousMediaStoreUri != customUri) {
            deletePersistedUri(previousMediaStoreUri)
        }

        database.query {
            database.upsert(
                song.song.copy(
                    mediaStoreUri = customUri,
                    downloadUri = customUri,
                    dateDownload = song.song.dateDownload ?: LocalDateTime.now(),
                )
            )
        }
    }

    private suspend fun deletePersistedUri(uriString: String) {
        if (uriString.isBlank()) return
        val uri = uriString.toUri()
        if (isMediaStoreContentUri(uriString)) {
            mediaStoreHelper.deleteFromMediaStore(uri)
            return
        }

        runCatching {
            val file = DocumentFile.fromSingleUri(context, uri)
            val deleted = file?.delete() == true
            if (!deleted && file?.exists() == true) {
                Timber.w("Failed to delete persisted file URI: %s", uriString)
            }
        }.onFailure { error ->
            Timber.w(error, "Failed deleting persisted file URI: %s", uriString)
        }
    }

    private suspend fun saveFileToCustomPath(
        song: Song,
        tempFile: File,
        mimeType: String,
        extension: String,
    ): String? = withContext(Dispatchers.IO) {
        val rootUri = runCatching { Uri.parse(customDownloadPathUri) }.getOrNull()
            ?: return@withContext null
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
        if (!root.canWrite()) return@withContext null

        val artistFolderName = sanitizeForFilesystem(resolvePrimaryArtist(song))
        val artistFolder = root.findFile(artistFolderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(artistFolderName)
            ?: return@withContext null

        val fileName = sanitizeForFilesystem("${song.song.title}.$extension")
        artistFolder.findFile(fileName)?.delete()

        val targetFile = artistFolder.createFile(mimeType, fileName) ?: return@withContext null
        val output = context.contentResolver.openOutputStream(targetFile.uri)
        if (output == null) {
            targetFile.delete()
            return@withContext null
        }

        output.use { out ->
            tempFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        targetFile.uri.toString()
    }

    private fun sanitizeForFilesystem(value: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown" }
            .take(200)
    }

    private fun isMediaStoreContentUri(uriString: String): Boolean {
        return uriString.startsWith("content://media/")
    }

    private fun isCustomPathWritable(): Boolean {
        if (customDownloadPathUri.isBlank()) return false
        val rootUri = runCatching { Uri.parse(customDownloadPathUri) }.getOrNull() ?: return false
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return false
        return root.canWrite()
    }

    private fun extensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("audio/mpeg") -> "mp3"
            mimeType.contains("audio/webm") -> "webm"
            mimeType.contains("audio/ogg") -> "ogg"
            mimeType.contains("audio/mp4") -> "m4a"
            mimeType.contains("audio/aac") -> "aac"
            mimeType.contains("audio/flac") -> "flac"
            else -> "m4a"
        }
    }

    private fun markCancelRequested(songId: String) {
        cancelRequested += songId
    }

    private fun clearCancelRequested(songId: String) {
        cancelRequested -= songId
    }

    private fun isCancelRequested(songId: String): Boolean = songId in cancelRequested

    private fun resolvePrimaryArtist(song: Song): String {
        val relationArtist = song.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        val entityArtist = song.song.artistName?.takeIf { it.isNotBlank() }
        return relationArtist ?: entityArtist ?: UNKNOWN_ARTIST_NAME
    }
}
