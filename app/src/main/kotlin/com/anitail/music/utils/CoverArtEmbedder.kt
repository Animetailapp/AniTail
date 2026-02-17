package com.anitail.music.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CoverArtEmbedder"
    }

    suspend fun embedMetadataIntoFile(
        fileUri: Uri,
        artworkData: ByteArray?,
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        albumArtist: String? = null,
        trackNumber: Int = 0,
        totalTracks: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "coverart_temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val inputFile = File(tempDir, "input_${System.currentTimeMillis()}.m4a")
        val outputFile = File(tempDir, "output_${System.currentTimeMillis()}.m4a")

        try {
            val docFile = DocumentFile.fromSingleUri(context, fileUri)
            if (docFile == null || !docFile.exists()) {
                Timber.tag(TAG).e("File does not exist: %s", fileUri)
                return@withContext false
            }

            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(inputFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Timber.tag(TAG).e("Failed to open input stream for: %s", fileUri)
                return@withContext false
            }

            val success = CoverArtNative.embedMetadata(
                inputPath = inputFile.absolutePath,
                outputPath = outputFile.absolutePath,
                artworkData = artworkData,
                title = title,
                artist = artist,
                album = album,
                year = year,
                albumArtist = albumArtist,
                trackNumber = trackNumber,
                totalTracks = totalTracks
            )

            if (!success) {
                Timber.tag(TAG).e("Native embedMetadata failed")
                return@withContext false
            }

            context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                outputFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                Timber.tag(TAG).e("Failed to open output stream for: %s", fileUri)
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error embedding metadata")
            false
        } finally {
            if (inputFile.exists()) {
                inputFile.delete()
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    suspend fun embedMetadataIntoLocalFile(
        filePath: String,
        artworkData: ByteArray?,
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        albumArtist: String? = null,
        trackNumber: Int = 0,
        totalTracks: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            Timber.tag(TAG).e("Input file does not exist: %s", filePath)
            return@withContext false
        }

        val outputFile = File(inputFile.parent, "temp_${inputFile.name}")

        try {
            val success = CoverArtNative.embedMetadata(
                inputPath = inputFile.absolutePath,
                outputPath = outputFile.absolutePath,
                artworkData = artworkData,
                title = title,
                artist = artist,
                album = album,
                year = year,
                albumArtist = albumArtist,
                trackNumber = trackNumber,
                totalTracks = totalTracks
            )

            if (!success) {
                Timber.tag(TAG).e("Native embedMetadata failed")
                return@withContext false
            }

            if (inputFile.delete() && outputFile.renameTo(inputFile)) {
                true
            } else {
                Timber.tag(TAG).e("Failed to replace original file")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error embedding metadata into local file")
            outputFile.delete()
            false
        }
    }
}
