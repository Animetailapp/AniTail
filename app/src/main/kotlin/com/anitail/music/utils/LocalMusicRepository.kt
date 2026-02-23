package com.anitail.music.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.AlbumArtistMap
import com.anitail.music.db.entities.AlbumEntity
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.SongAlbumMap
import com.anitail.music.db.entities.SongArtistMap
import com.anitail.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed class LocalSyncStatus {
    data object Idle : LocalSyncStatus()
    data object Syncing : LocalSyncStatus()
    data object Completed : LocalSyncStatus()
    data class Error(val message: String) : LocalSyncStatus()
}

@Singleton
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val _syncStatus = MutableStateFlow<LocalSyncStatus>(LocalSyncStatus.Idle)
    val syncStatus: StateFlow<LocalSyncStatus> = _syncStatus.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasIt = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        Timber.d("LocalMusic: hasPermission() called - permission=$permission, granted=$hasIt, SDK=${Build.VERSION.SDK_INT}")
        return hasIt
    }

    fun getRequiredPermission(): String {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        Timber.d("LocalMusic: getRequiredPermission() = $perm")
        return perm
    }

    suspend fun syncLocalMusic() = withContext(Dispatchers.IO) {
        Timber.d("LocalMusic: syncLocalMusic() STARTED")
        if (!hasPermission()) {
            Timber.e("LocalMusic: syncLocalMusic() - Permission NOT granted, aborting")
            _syncStatus.value = LocalSyncStatus.Error("Permission not granted")
            return@withContext
        }

        Timber.d("LocalMusic: syncLocalMusic() - Permission granted, starting sync")
        _syncStatus.value = LocalSyncStatus.Syncing
        _syncProgress.value = 0f

        try {
            val contentResolver = context.contentResolver
            Timber.d("LocalMusic: syncLocalMusic() - Got contentResolver: $contentResolver")

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA,  // Full file path for folder browsing
            )

            // Include all audio files (m4a, webm, mp3, flac, etc.)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            Timber.d("LocalMusic: syncLocalMusic() - Querying MediaStore.Audio.Media.EXTERNAL_CONTENT_URI")
            Timber.d("LocalMusic: syncLocalMusic() - URI: ${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}")
            Timber.d("LocalMusic: syncLocalMusic() - Selection: $selection")

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                Timber.d("LocalMusic: syncLocalMusic() - Query returned cursor with ${cursor.count} rows")
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                val totalCount = cursor.count
                var processedCount = 0
                Timber.d("LocalMusic: syncLocalMusic() - Total songs to process: $totalCount")

                val artistCache = mutableMapOf<String, String>()
                val albumCache = mutableMapOf<Long, String>()
                val artistThumbnailCache = mutableMapOf<String, String?>() // Track first album art per artist
                val createdArtistIds = mutableSetOf<String>()
                val createdAlbumIds = mutableSetOf<String>()

                // Collect all entities first, then batch insert in single transaction
                val artistEntities = mutableListOf<ArtistEntity>()
                val albumEntities = mutableListOf<AlbumEntity>()
                val songEntities = mutableListOf<SongEntity>()
                val songArtistMaps = mutableListOf<SongArtistMap>()
                val songAlbumMaps = mutableListOf<SongAlbumMap>()
                val albumArtistMaps = mutableListOf<AlbumArtistMap>()

                while (cursor.moveToNext()) {
                    try {
                        val mediaStoreId = cursor.getLong(idCol)
                        Timber.v("LocalMusic: Processing song mediaStoreId=$mediaStoreId")
                        val title = cursor.getString(titleCol) ?: "Unknown"
                        val artistName = cursor.getString(artistCol)?.takeIf {
                            it.isNotBlank() && it != "<unknown>"
                        } ?: "Unknown Artist"
                        val albumName = cursor.getString(albumCol)?.takeIf {
                            it.isNotBlank() && it != "<unknown>"
                        } ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdCol)
                        val duration = cursor.getInt(durationCol) / 1000
                        val trackNumber = cursor.getInt(trackCol)
                        val year = cursor.getInt(yearCol).takeIf { it > 0 }
                        val filePath = cursor.getString(dataCol)

                        val songId = "LOCAL_$mediaStoreId"
                        val localArtistId = artistCache.getOrPut(artistName.lowercase()) {
                            "LA_LOCAL_${artistName.lowercase().hashCode()}"
                        }
                        val localAlbumId = albumCache.getOrPut(albumId) {
                            "LOCAL_ALBUM_$albumId"
                        }

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaStoreId
                        ).toString()

                        val albumArtUri = getAlbumArtUri(albumId)

                        Timber.d("LocalMusic: Song details - id=$songId, title=$title, artist=$artistName, album=$albumName, duration=$duration, contentUri=$contentUri")

                        // Track first album art for this artist
                        if (!artistThumbnailCache.containsKey(localArtistId) && albumArtUri != null) {
                            artistThumbnailCache[localArtistId] = albumArtUri
                        }

                        // Collect artist entity (deduped by cache)
                        if (createdArtistIds.add(localArtistId)) {
                            artistEntities.add(
                                ArtistEntity(
                                    id = localArtistId,
                                    name = artistName,
                                    thumbnailUrl = albumArtUri, // Use first album art as thumbnail
                                )
                            )
                        }

                        // Collect album entity (deduped by cache)
                        if (createdAlbumIds.add(localAlbumId)) {
                            albumEntities.add(
                                AlbumEntity(
                                    id = localAlbumId,
                                    title = albumName,
                                    year = year,
                                    thumbnailUrl = albumArtUri,
                                    songCount = 0,
                                    duration = 0,
                                )
                            )
                        }

                        songEntities.add(
                            SongEntity(
                                id = songId,
                                title = title,
                                duration = duration,
                                thumbnailUrl = albumArtUri,
                                albumId = localAlbumId,
                                albumName = albumName,
                                year = year,
                                isLocal = true,
                                mediaStoreUri = contentUri,
                                downloadUri = contentUri,
                                localPath = filePath,
                                inLibrary = LocalDateTime.now()
                            )
                        )

                        songArtistMaps.add(SongArtistMap(songId, localArtistId, 0))
                        songAlbumMaps.add(SongAlbumMap(songId, localAlbumId, trackNumber))
                        albumArtistMaps.add(AlbumArtistMap(localAlbumId, localArtistId, 0))

                        processedCount++
                        _syncProgress.value = processedCount.toFloat() / totalCount
                        if (processedCount % 50 == 0) {
                            Timber.d("LocalMusic: Sync progress - $processedCount / $totalCount songs collected")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "LocalMusic: Error processing local song at index $processedCount")
                    }
                }

                Timber.d("LocalMusic: Collected ${songEntities.size} songs, ${artistEntities.size} artists, ${albumEntities.size} albums - starting batch insert")

                // Single transaction for all inserts - much faster and synchronous completion
                database.transaction {
                    Timber.d("LocalMusic: Batch inserting ${artistEntities.size} artists")
                    artistEntities.forEach { upsert(it) }

                    Timber.d("LocalMusic: Batch inserting ${albumEntities.size} albums")
                    albumEntities.forEach { upsert(it) }

                    Timber.d("LocalMusic: Batch inserting ${songEntities.size} songs")
                    songEntities.forEach { upsert(it) }

                    Timber.d("LocalMusic: Batch inserting ${songArtistMaps.size} song-artist maps")
                    songArtistMaps.forEach { upsert(it) }

                    Timber.d("LocalMusic: Batch inserting ${songAlbumMaps.size} song-album maps")
                    songAlbumMaps.forEach { upsert(it) }

                    Timber.d("LocalMusic: Batch inserting ${albumArtistMaps.size} album-artist maps")
                    albumArtistMaps.distinctBy { it.albumId to it.artistId }.forEach { upsert(it) }
                }

                Timber.d("LocalMusic: Batch transaction submitted")
                Timber.d("LocalMusic: Finished processing all $processedCount songs")
            } ?: run {
                Timber.e("LocalMusic: syncLocalMusic() - MediaStore query returned NULL cursor!")
            }

            updateAlbumStats()
            _syncStatus.value = LocalSyncStatus.Completed
            _syncProgress.value = 1f
            Timber.d("LocalMusic: syncLocalMusic() COMPLETED SUCCESSFULLY")
        } catch (e: Exception) {
            Timber.e(e, "LocalMusic: syncLocalMusic() FAILED with exception: ${e.message}")
            e.printStackTrace()
            _syncStatus.value = LocalSyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun getAlbumArtUri(albumId: Long): String? {
        return try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            ).toString()
            Timber.v("LocalMusic: getAlbumArtUri($albumId) = $uri")
            uri
        } catch (e: Exception) {
            Timber.e(e, "LocalMusic: getAlbumArtUri($albumId) failed")
            null
        }
    }

    private suspend fun updateAlbumStats() = withContext(Dispatchers.IO) {
        database.query {
            // Update album song counts and durations via raw query would be complex,
            // so we'll leave them as-is for now since they're not critical for display
        }
    }

    fun resetSyncStatus() {
        Timber.d("LocalMusic: resetSyncStatus() called")
        _syncStatus.value = LocalSyncStatus.Idle
        _syncProgress.value = 0f
    }
}

