package com.anitail.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.datasource.cache.SimpleCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.anitail.innertube.YouTube
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.FormatEntity
import com.anitail.music.di.DownloadCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadExportHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @DownloadCache private val downloadCache: SimpleCache,
    private val coverArtEmbedder: CoverArtEmbedder,
) {
    companion object {
        private const val TAG = "DownloadExportHelper"
    }

    suspend fun exportToCustomPath(
        songId: String,
        customPathUri: String
    ): String? = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("=== Starting export for song: %s ===", songId)
        Timber.tag(TAG).d("Custom path URI: %s", customPathUri)

        try {
            val song = database.song(songId).first() ?: run {
                Timber.tag(TAG).w("Song not found in database: %s", songId)
                return@withContext null
            }

            val format = database.format(songId).first()
            val extension = getExtensionFromFormat(format)

            val firstArtist = song.artists.firstOrNull()?.name ?: "Unknown Artist"
            val allArtists = song.artists.joinToString(", ") { it.name }
                .ifEmpty { "Unknown Artist" }
            val title = song.song.title
            val sanitizedArtistFolder = sanitizeFilename(firstArtist)
            val sanitizedFilename = sanitizeFilename("$title.$extension")

            val parentUri = Uri.parse(customPathUri)
            val rootDoc = DocumentFile.fromTreeUri(context, parentUri) ?: run {
                Timber.tag(TAG).e("Cannot access custom path: %s", customPathUri)
                return@withContext null
            }

            if (!rootDoc.canWrite()) {
                Timber.tag(TAG).e("Cannot write to custom path: %s", customPathUri)
                return@withContext null
            }

            var artistFolder = rootDoc.findFile(sanitizedArtistFolder)
            if (artistFolder == null || !artistFolder.isDirectory) {
                artistFolder = rootDoc.createDirectory(sanitizedArtistFolder)
                if (artistFolder == null) {
                    Timber.tag(TAG).e("Failed to create artist folder: %s", sanitizedArtistFolder)
                    return@withContext null
                }
            }

            val existingFile = artistFolder.findFile(sanitizedFilename)
            if (existingFile != null) {
                existingFile.delete()
            }

            val mimeType = format?.mimeType ?: "audio/mp4"
            val newFile = artistFolder.createFile(mimeType, sanitizedFilename) ?: run {
                Timber.tag(TAG).e("Failed to create file: %s", sanitizedFilename)
                return@withContext null
            }

            val outputStream = context.contentResolver.openOutputStream(newFile.uri) ?: run {
                Timber.tag(TAG).e("Failed to open output stream for: %s", newFile.uri)
                newFile.delete()
                return@withContext null
            }

            outputStream.use { out ->
                val cachedSpans = downloadCache.getCachedSpans(songId)
                if (cachedSpans.isEmpty()) {
                    Timber.tag(TAG).w("No cached data found for: %s", songId)
                    newFile.delete()
                    return@withContext null
                }

                val sortedSpans = cachedSpans.sortedBy { it.position }
                for (span in sortedSpans) {
                    span.file?.inputStream()?.use { input ->
                        input.copyTo(out)
                    }
                }
            }

            val exportedUri = newFile.uri.toString()

            val bitrateKbps = (format?.bitrate ?: 0) / 1000
            if (extension == "m4a" && bitrateKbps >= 128) {
                try {
                    val artworkData = fetchArtworkData(song.song.thumbnailUrl)

                    var albumName = song.album?.title ?: song.song.albumName
                    var year = (song.album?.year ?: song.song.year)?.toString()
                    var albumArtist: String? = null
                    var trackNumber = 0
                    var totalTracks = 0

                    if ((albumName == null || year == null) && song.song.albumId != null) {
                        try {
                            val albumPage = YouTube.album(song.song.albumId!!).getOrNull()
                            if (albumPage != null) {
                                if (albumName == null) albumName = albumPage.album.title
                                if (year == null) year = albumPage.album.year?.toString()
                                albumArtist = albumPage.album.artists?.firstOrNull()?.name
                                totalTracks = albumPage.songs.size
                                val trackIndex = albumPage.songs.indexOfFirst { it.id == songId }
                                if (trackIndex >= 0) {
                                    trackNumber = trackIndex + 1
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "Failed to fetch album info from YouTube")
                        }
                    }

                    val embedSuccess = coverArtEmbedder.embedMetadataIntoFile(
                        fileUri = newFile.uri,
                        artworkData = artworkData,
                        title = song.song.title,
                        artist = allArtists,
                        album = albumName,
                        year = year,
                        albumArtist = albumArtist,
                        trackNumber = trackNumber,
                        totalTracks = totalTracks
                    )

                    if (!embedSuccess) {
                        Timber.tag(TAG).w("Metadata embedding failed, file exported without metadata")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error embedding metadata, continuing without metadata")
                }
            }

            database.updateDownloadUri(songId, exportedUri)
            exportedUri
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "IO error exporting song: %s", songId)
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error exporting song: %s", songId)
            null
        }
    }

    suspend fun deleteFromCustomPath(songId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val downloadUri = database.getDownloadUri(songId)
            if (downloadUri.isNullOrBlank()) return@withContext true

            val file = DocumentFile.fromSingleUri(context, Uri.parse(downloadUri))
            val deleted = file?.delete() ?: false
            if (deleted || file?.exists() == false) {
                database.updateDownloadUri(songId, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed deleting exported song %s", songId)
            false
        }
    }

    fun verifyPathAccess(uri: String): Boolean {
        return try {
            val parsedUri = Uri.parse(uri)
            val docFile = DocumentFile.fromTreeUri(context, parsedUri)
            val canRead = docFile?.canRead() == true
            val canWrite = docFile?.canWrite() == true
            canRead && canWrite
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error verifying path access: %s", uri)
            false
        }
    }

    fun verifyFileAccess(uri: String): Boolean {
        return try {
            val parsedUri = Uri.parse(uri)
            val docFile = DocumentFile.fromSingleUri(context, parsedUri)
            val exists = docFile?.exists() == true
            val canRead = docFile?.canRead() == true
            exists && canRead
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error verifying file access: %s", uri)
            false
        }
    }

    private fun getExtensionFromFormat(format: FormatEntity?): String = when {
        format == null -> "m4a"
        format.mimeType.contains("audio/webm") -> "ogg"
        format.mimeType.contains("audio/mp4") -> "m4a"
        format.mimeType.contains("audio/mpeg") -> "mp3"
        format.mimeType.contains("audio/ogg") -> "ogg"
        else -> "m4a"
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }

    private suspend fun fetchArtworkData(thumbnailUrl: String?): ByteArray? {
        if (thumbnailUrl.isNullOrEmpty()) return null

        return try {
            val imageLoader = ImageLoader.Builder(context).build()
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.toByteArray()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching artwork")
            null
        }
    }
}
