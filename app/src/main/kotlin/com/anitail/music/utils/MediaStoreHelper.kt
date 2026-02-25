package com.anitail.music.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream

/**
 * Helper class for MediaStore operations to save downloaded music files
 * to the public Music/Anitail folder accessible by other apps.
 *
 * Files stored via MediaStore:
 * - Survive app uninstall
 * - Are accessible to other music players
 * - Are properly indexed by the system media scanner
 * - Respect Android 10+ scoped storage requirements
 */
class MediaStoreHelper(private val context: Context) {

    companion object {
        internal const val ANITAIL_FOLDER_NAME = "Anitail"
        private const val MAX_FILENAME_LENGTH = 200

        // Supported audio MIME types
        private val MIME_TYPE_MAP = mapOf(
            "opus" to "audio/opus",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "webm" to "audio/webm",
            "ogg" to "audio/ogg",
            "mp3" to "audio/mpeg",
            "aac" to "audio/aac",
            "flac" to "audio/flac"
        )
    }

    /**
     * Save a downloaded audio file to MediaStore in the Music/Anitail folder
     *
     * @param inputStream The audio file data stream
     * @param fileName Desired filename (will be sanitized)
     * @param mimeType Audio MIME type (e.g., "audio/opus")
     * @param title Song title for metadata
     * @param artist Artist name for metadata
     * @param album Album name for metadata (optional)
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveToMediaStore(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
        year: Int? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sanitizedFileName = sanitizeFileName(fileName)
            val contentResolver = context.contentResolver

            Timber.d("=== MediaStore Save Starting ===")
            Timber.d("File: $sanitizedFileName")
            Timber.d("MIME: $mimeType")
            Timber.d("Title: $title")
            Timber.d("Artist: $artist")
            Timber.d("Android SDK: ${Build.VERSION.SDK_INT}")

            // Log permission status for debugging
            val hasPermission = PermissionHelper.hasMediaStoreWritePermission(context)
            Timber.d("Permission status: ${if (hasPermission) "GRANTED" else "DENIED"}")

            // Prepare ContentValues with metadata
            // Organize files: Music/Anitail/{Artist}/{Album}/Song.mp3 or Music/Anitail/{Artist}/Song.mp3
            val sanitizedArtist = sanitizeFolderName(artist)
            val relativePath = buildRelativePathInternal(artist = sanitizedArtist, album = album)
            Timber.d("RELATIVE_PATH: $relativePath")

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
                album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                durationMs?.let { put(MediaStore.Audio.Media.DURATION, it) }
                year?.let { put(MediaStore.Audio.Media.YEAR, it) }
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                // Set relative path for Android 10+ (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Timber.d("Using scoped storage (Android 10+)")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                } else {
                    Timber.d("Using legacy storage (Android <10)")
                }
            }

            // Insert the file entry into MediaStore
            val audioCollection = audioCollectionUri()

            Timber.d("Audio collection URI: $audioCollection")

            val audioUri = contentResolver.insert(audioCollection, contentValues)

            if (audioUri == null) {
                Timber.e("❌ Failed to create MediaStore entry for: $sanitizedFileName")
                Timber.e("This usually means permission denied or invalid path")
                return@withContext null
            }

            Timber.d("✓ MediaStore entry created: $audioUri")

            // Write the actual file content
            var bytesWritten = 0L
            contentResolver.openOutputStream(audioUri)?.use { outputStream ->
                bytesWritten = inputStream.copyTo(outputStream)
                Timber.d("✓ Wrote $bytesWritten bytes to MediaStore")
            } ?: run {
                Timber.e("❌ Failed to open output stream for: $audioUri")
                contentResolver.delete(audioUri, null, null)
                return@withContext null
            }

            // Mark file as ready (remove IS_PENDING flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                val updateCount = contentResolver.update(audioUri, contentValues, null, null)
                Timber.d("✓ Marked as ready (IS_PENDING=0), updated $updateCount row(s)")
            }

            Timber.d("=== MediaStore Save Complete ===")
            Timber.d("✓ Successfully saved to MediaStore: $audioUri")
            Timber.d("✓ Location: $relativePath/$sanitizedFileName")
            audioUri

        } catch (e: Exception) {
            Timber.e(e, "Error saving to MediaStore: ${e.message}")
            null
        }
    }

    /**
     * Save a file from a temporary location to MediaStore
     *
     * @param tempFile Temporary file to move to MediaStore
     * @param fileName Desired filename
     * @param mimeType Audio MIME type
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional)
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveFileToMediaStore(
        tempFile: File,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
        year: Int? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!tempFile.exists()) {
                Timber.e("Temp file does not exist: ${tempFile.absolutePath}")
                return@withContext null
            }

            val fileSize = tempFile.length()
            Timber.d("Saving temp file to MediaStore: $fileName, size: $fileSize bytes, path: ${tempFile.absolutePath}")

            if (fileSize == 0L) {
                Timber.e("Temp file is empty: ${tempFile.absolutePath}")
                return@withContext null
            }

            tempFile.inputStream().use { inputStream ->
                saveToMediaStore(
                    inputStream = inputStream,
                    fileName = fileName,
                    mimeType = mimeType,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    year = year
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving file to MediaStore: ${e.message}")
            null
        }
    }

    /**
     * Check if a file already exists in MediaStore
     *
     * @param title Song title to search for
     * @param artist Artist name to search for
     * @param durationMs Duration in milliseconds to reduce false positives (optional)
     * @return Uri of existing file, or null if not found
     */
    suspend fun findExistingFile(
        title: String,
        artist: String,
        durationMs: Long? = null,
    ): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.RELATIVE_PATH
                )

                // Search only in Anitail folder
                val selectionParts = mutableListOf(
                    "${MediaStore.Audio.Media.TITLE} = ?",
                    "${MediaStore.Audio.Media.ARTIST} = ?",
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                )
                val selectionArgs = mutableListOf(title, artist, anitailRelativePathLikeArg())
                val durationToleranceMs = 2_000L
                if (durationMs != null && durationMs > 0L) {
                    val minDuration = (durationMs - durationToleranceMs).coerceAtLeast(0L)
                    val maxDuration = durationMs + durationToleranceMs
                    selectionParts += "${MediaStore.Audio.Media.DURATION} BETWEEN ? AND ?"
                    selectionArgs += minDuration.toString()
                    selectionArgs += maxDuration.toString()
                }

                val selection = selectionParts.joinToString(" AND ")

                context.contentResolver.query(
                    audioCollectionUri(),
                    projection,
                    selection,
                    selectionArgs.toTypedArray(),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val id = cursor.getLong(idColumn)
                        val contentUri = audioCollectionUri()
                        Uri.withAppendedPath(contentUri, id.toString())
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error finding existing file: ${e.message}")
                null
            }
        }

    /**
     * Delete a file from MediaStore
     *
     * @param uri Uri of the file to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteFromMediaStore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                Timber.d("Deleted from MediaStore: $uri")
                true
            } else {
                Timber.w("Failed to delete from MediaStore: $uri")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting from MediaStore: ${e.message}")
            false
        }
    }

    /**
     * Get MIME type from file extension
     *
     * @param extension File extension (e.g., "opus", "m4a")
     * @return MIME type string, or "audio/mpeg" as default
     */
    fun getMimeType(extension: String): String {
        return MIME_TYPE_MAP[extension.lowercase()] ?: "audio/mpeg"
    }

    /**
     * Sanitize a filename to be safe for filesystem use
     * Removes invalid characters and limits length
     *
     * @param fileName Original filename
     * @return Sanitized filename
     */
    private fun sanitizeFileName(fileName: String): String {
        // Remove invalid characters for filenames
        var sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // Remove leading/trailing whitespace and dots
        sanitized = sanitized.trim().trimStart('.')

        // Limit filename length
        if (sanitized.length > MAX_FILENAME_LENGTH) {
            val extension = sanitized.substringAfterLast('.', "")
            val nameWithoutExt = sanitized.substringBeforeLast('.')
            val maxNameLength = MAX_FILENAME_LENGTH - extension.length - 1
            sanitized = "${nameWithoutExt.take(maxNameLength)}.$extension"
        }

        // Ensure we have a valid filename
        if (sanitized.isBlank()) {
            sanitized = "audio_${System.currentTimeMillis()}.opus"
        }

        return sanitized
    }

    /**
     * Sanitize a folder name to be safe for filesystem use
     * Removes invalid characters and limits length
     *
     * @param name Original folder name (artist or album name)
     * @return Sanitized folder name
     */
    private fun sanitizeFolderName(name: String): String {
        if (name.isBlank()) return "Unknown"

        // Remove invalid characters for folder names
        var sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // Remove leading/trailing whitespace
        sanitized = sanitized.trim()

        // Limit folder name length (100 chars for compatibility)
        if (sanitized.length > 100) {
            sanitized = sanitized.take(100)
        }

        // Ensure we have a valid folder name
        return sanitized.ifBlank { "Unknown" }
    }

    /**
     * Get the default public Music/Anitail folder path (for display purposes)
     * Note: On Android 10+, direct file access is restricted
     * Note: This does not override custom-folder export logic.
     *
     * @return Folder path string
     */
    fun getAnitailFolderPath(): String {
        return "${Environment.DIRECTORY_MUSIC}/$ANITAIL_FOLDER_NAME"
    }

    /**
     * Calculate the total size of all files in the Anitail folder
     *
     * @return Total size in bytes of all Anitail files
     */
    suspend fun getAnitailFolderSize(): Long = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(MediaStore.Audio.Media.SIZE)
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$ANITAIL_FOLDER_NAME%")

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                var totalSize = 0L
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeColumn)
                    totalSize += size
                }
                totalSize
            } ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Error calculating Anitail folder size: ${e.message}")
            0L
        }
    }

    /**
     * Delete all files in the Anitail folder from MediaStore
     *
     * @return Number of files deleted
     */
    suspend fun deleteAnitailFolderContents(): Int = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$ANITAIL_FOLDER_NAME%")

            val urisToDelete = mutableListOf<Uri>()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    urisToDelete.add(Uri.withAppendedPath(contentUri, id.toString()))
                }
            }

            var deletedCount = 0
            urisToDelete.forEach { uri ->
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    deletedCount++
                    Timber.d("Deleted from MediaStore: $uri")
                }
            }

            Timber.d("Deleted $deletedCount files from Anitail folder")
            deletedCount
        } catch (e: Exception) {
            Timber.e(e, "Error deleting Anitail folder contents: ${e.message}")
            0
        }
    }

    internal fun buildRelativePathInternal(artist: String, album: String?): String {
        return if (!album.isNullOrBlank()) {
            val sanitizedAlbum = sanitizeFolderName(album)
            "${Environment.DIRECTORY_MUSIC}/$ANITAIL_FOLDER_NAME/$artist/$sanitizedAlbum"
        } else {
            "${Environment.DIRECTORY_MUSIC}/$ANITAIL_FOLDER_NAME/$artist"
        }
    }

    internal fun audioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    internal fun anitailRelativePathLikeArg(): String = "%$ANITAIL_FOLDER_NAME%"
}
