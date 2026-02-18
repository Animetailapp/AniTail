package com.anitail.music.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.anitail.music.constants.AudioQuality
import com.anitail.music.constants.AudioQualityKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.utils.MediaStoreHelper
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
import javax.inject.Inject
import javax.inject.Singleton
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
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mediaStoreHelper = MediaStoreHelper(context)
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)

    // Concurrent download limiter (max 3 simultaneous downloads)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // Mutex to protect against duplicate downloads from concurrent requests
    private val downloadMutex = Mutex()

    // Download state tracking
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Download queue
    private val downloadQueue = mutableListOf<Song>()
    private val activeDownloads = mutableMapOf<String, Job>()

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2.0
        private const val UNKNOWN_ARTIST_NAME = "Unknown Artist"
        private const val MIN_PROGRESS_UPDATE_BYTES = 256 * 1024L
        private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 500L
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
    fun downloadSong(song: Song) {
        scope.launch {

            MediaStoreDownloadService.Companion.start(context)
            downloadMutex.withLock {
                // Check if already downloading or completed
                val currentState = _downloadStates.value[song.id]
                if (currentState?.status == DownloadState.Status.DOWNLOADING ||
                    currentState?.status == DownloadState.Status.COMPLETED
                ) {
                    Timber.Forest.d("Song ${song.song.title} is already downloading or completed (status: ${currentState.status})")
                    return@launch
                }

                // Check if already downloaded in database (has mediaStoreUri)
                if (!song.song.mediaStoreUri.isNullOrEmpty()) {
                    Timber.Forest.d("Song ${song.song.title} is already downloaded in database: ${song.song.mediaStoreUri}")
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )
                    return@launch
                }

                // Check if already exists in MediaStore
                val primaryArtist = resolvePrimaryArtist(song)

                val existingFile = mediaStoreHelper.findExistingFile(
                    title = song.song.title,
                    artist = primaryArtist
                )
                if (existingFile != null) {
                    Timber.Forest.d("Song ${song.song.title} already exists in MediaStore: $existingFile")
                    updateDownloadState(
                        song.id,
                        DownloadState(
                            songId = song.id,
                            status = DownloadState.Status.COMPLETED,
                            progress = 1f
                        )
                    )
                    markSongAsDownloaded(song.id, existingFile.toString())
                    return@launch
                }

                // Add to queue
                synchronized(downloadQueue) {
                    if (!downloadQueue.any { it.id == song.id }) {
                        downloadQueue.add(song)
                        updateDownloadState(
                            song.id,
                            DownloadState(
                                songId = song.id,
                                status = DownloadState.Status.QUEUED
                            )
                        )
                    }
                }
            }
            processQueue()
        }
    }

    /**
     * Cancel a download
     *
     * @param songId The ID of the song to cancel
     */
    fun cancelDownload(songId: String) {
        scope.launch {

            activeDownloads[songId]?.cancel()
            activeDownloads.remove(songId)

            synchronized(downloadQueue) {
                downloadQueue.removeAll { it.id == songId }
            }

            updateDownloadState(
                songId,
                DownloadState(
                    songId = songId,
                    status = DownloadState.Status.CANCELLED
                )
            )
        }
    }

    /**
     * Retry a failed download
     *
     * @param songId The ID of the song to retry
     */
    fun retryDownload(songId: String) {
        scope.launch {
            val song = database.song(songId).first() ?: run {
                Timber.Forest.e("Song not found in database: $songId")
                return@launch
            }

            // Reset download state
            updateDownloadState(
                songId,
                DownloadState(
                    songId = songId,
                    status = DownloadState.Status.QUEUED
                )
            )

            downloadSong(song)
        }
    }

    /**
     * Process the download queue
     */
    private fun processQueue() {
        val song = synchronized(downloadQueue) {
            downloadQueue.firstOrNull()
        } ?: return

        if (!downloadSemaphore.tryAcquire()) {
            return
        }

        synchronized(downloadQueue) {
            downloadQueue.remove(song)
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

    /**
     * Perform the actual download with retry logic
     */
    private suspend fun performDownload(song: Song): Unit = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${song.id}.part")
        var retryAttempt = 0
        var lastError: Exception? = null
        var lockedItag = 0

        runCatching {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        try {
            while (retryAttempt <= MAX_RETRY_ATTEMPTS) {
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
                    val extension = "mp3"
                    val mimeType = mediaStoreHelper.getMimeType(extension)

                    val fileName = "$artist - $title.$extension"
                    val uri = mediaStoreHelper.saveFileToMediaStore(
                        tempFile = tempFile,
                        fileName = fileName,
                        mimeType = mimeType,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        year = year
                    )

                    if (uri != null) {
                        updateDownloadState(
                            song.id,
                            DownloadState(
                                songId = song.id,
                                status = DownloadState.Status.COMPLETED,
                                progress = 1f
                            )
                        )
                        markSongAsDownloaded(song.id, uri.toString())
                        return@withContext
                    } else {
                        throw Exception("Failed to save file to MediaStore")
                    }
                } catch (e: Exception) {
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

            connection.getInputStream().use { input ->
                FileOutputStream(outputFile, appendMode).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
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
        _downloadStates.value = _downloadStates.value + (songId to state)
    }

    /**
     * Mark a song as downloaded in the database with MediaStore URI
     */
    private suspend fun markSongAsDownloaded(songId: String, mediaStoreUri: String) {
        val song = database.song(songId).first()
        if (song != null) {
            database.query {
                database.upsert(
                    song.song.copy(
                        dateDownload = LocalDateTime.now(),
                        mediaStoreUri = mediaStoreUri
                    )
                )
            }
            Timber.d("Marked song as downloaded: ${song.song.title}, URI: $mediaStoreUri")
        }
    }

    private fun resolvePrimaryArtist(song: Song): String {
        val relationArtist = song.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        val entityArtist = song.song.artistName?.takeIf { it.isNotBlank() }
        return relationArtist ?: entityArtist ?: UNKNOWN_ARTIST_NAME
    }
}
