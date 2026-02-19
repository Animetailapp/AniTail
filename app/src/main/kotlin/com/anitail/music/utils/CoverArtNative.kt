package com.anitail.music.utils

object CoverArtNative {
    init {
        System.loadLibrary("coverart")
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
