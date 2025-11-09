package com.anitail.music.downloads

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.utils.MediaStoreHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val contentResolver = context.contentResolver
    private val mediaStoreHelper = MediaStoreHelper(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    private val mediaObserver: ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onMediaStoreChanged(null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onMediaStoreChanged(uri)
            }

            private fun onMediaStoreChanged(uri: Uri?) {
                if (uri == null || uri.toString()
                        .contains(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.lastPathSegment ?: "")
                ) {
                    scope.launch {
                        refreshDownloads()
                    }
                }
            }
        }

    init {
        scope.launch {
            refreshDownloads()
        }
        contentResolver.registerContentObserver(
            mediaStoreHelper.audioCollectionUri(),
            true,
            mediaObserver
        )
    }

    fun observeDownloads(): StateFlow<List<DownloadedTrack>> = downloads

    suspend fun cleanupOrphans(): CleanupStats = withContext(Dispatchers.IO) {
        val songs = runCatching { database.songsWithMediaStoreUri() }
            .onFailure { Timber.e(it, "Failed to load downloaded songs from database") }
            .getOrDefault(emptyList())

        var clearedDatabase = 0
        var removedMediaStore = 0

        songs.forEach { song ->
            val uriString = song.song.mediaStoreUri ?: return@forEach
            val uri = uriString.toUri()
            val exists = runCatching {
                contentResolver.openFileDescriptor(uri, "r")?.use { }
                true
            }.getOrElse { throwable ->
                if (throwable is FileNotFoundException) {
                    false
                } else {
                    Timber.w(throwable, "Unable to verify MediaStore entry for ${song.song.id}")
                    true
                }
            }

            if (!exists) {
                val deleted = runCatching { contentResolver.delete(uri, null, null) }
                    .onFailure { Timber.w(it, "Failed to delete orphaned MediaStore entry: $uri") }
                    .getOrDefault(0)
                if (deleted > 0) removedMediaStore += deleted

                database.query {
                    upsert(
                        song.song.copy(
                            mediaStoreUri = null,
                            dateDownload = null
                        )
                    )
                }
                clearedDatabase++
            }
        }

        if (clearedDatabase > 0 || removedMediaStore > 0) {
            refreshDownloads()
        }

        CleanupStats(entriesCleared = clearedDatabase, mediaStoreDeletes = removedMediaStore)
    }

    suspend fun refreshDownloads() {
        val items = queryDownloads()
        _downloads.value = items
    }

    private suspend fun queryDownloads(): List<DownloadedTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(mediaStoreHelper.anitailRelativePathLikeArg())
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val collection = mediaStoreHelper.audioCollectionUri()
        val result = mutableListOf<DownloadedTrack>()

        runCatching {
            contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
        }.onFailure {
            Timber.e(it, "Failed to query MediaStore for downloads")
        }.getOrNull()?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val displayName =
                    cursor.getString(displayNameColumn) ?: uri.lastPathSegment.orEmpty()
                val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: displayName
                val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() }
                val album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() }
                val duration = if (!cursor.isNull(durationColumn)) cursor.getLong(durationColumn)
                    .takeIf { it > 0 } else null
                val size = if (!cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn)
                    .takeIf { it > 0 } else null
                val dateAdded = if (!cursor.isNull(dateAddedColumn)) cursor.getLong(dateAddedColumn)
                    .takeIf { it > 0 } else null

                result += DownloadedTrack(
                    mediaStoreId = id,
                    mediaUri = uri,
                    displayName = displayName,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    sizeBytes = size,
                    dateAddedSeconds = dateAdded,
                    song = null
                )
            }
        }

        // Deduplicate by mediaStoreId in case of duplicates from MediaStore query
        val uniqueResults = result.distinctBy { it.mediaStoreId }

        // First attempt: match by mediaStoreUri field in database
        val songsByUri = runCatching {
            database.songsWithMediaStoreUri().associateBy { it.song.mediaStoreUri }
        }.onFailure {
            Timber.e(it, "Failed to load songs with mediaStoreUri from database")
        }.getOrDefault(emptyMap())

        // Map tracks to songs
        uniqueResults.map { track ->
            var song = songsByUri[track.mediaUri.toString()]

            // If not found by URI, try to find by extracting ID from URI
            if (song == null && track.mediaStoreId != null) {
                // Try alternative ID-based lookup if needed
                Timber.w("No song found for MediaStore URI ${track.mediaUri}, mediaStoreId: ${track.mediaStoreId}")
            }

            track.copy(song = song)
        }
    }

    data class CleanupStats(
        val entriesCleared: Int,
        val mediaStoreDeletes: Int,
    )

    data class DownloadedTrack(
        val mediaStoreId: Long? = null,
        val mediaUri: Uri,
        val displayName: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val sizeBytes: Long?,
        val dateAddedSeconds: Long?,
        val song: Song?,
    )
}
