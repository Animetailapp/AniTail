package com.anitail.music.utils

import timber.log.Timber

object CoverArtNative {
    val isAvailable: Boolean = try {
        System.loadLibrary("coverart")
        true
    } catch (t: Throwable) {
        Timber.tag("CoverArtNative").e(t, "Failed to load libcoverart.so native library")
        false
    }

    external fun embedMetadata(
        inputPath: String,
        outputPath: String,
        artworkData: ByteArray?,
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        albumArtist: String?,
        trackNumber: Int,
        totalTracks: Int
    ): Boolean

    external fun defragmentFile(
        inputPath: String,
        outputPath: String
    ): Boolean
}

