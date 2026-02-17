package com.anitail.music.utils

object CoverArtNative {
    fun embedMetadata(
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
    ): Boolean = com.metrolist.music.utils.CoverArtNative.embedMetadata(
        inputPath = inputPath,
        outputPath = outputPath,
        artworkData = artworkData,
        title = title,
        artist = artist,
        album = album,
        year = year,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        totalTracks = totalTracks
    )

    fun defragmentFile(
        inputPath: String,
        outputPath: String
    ): Boolean = com.metrolist.music.utils.CoverArtNative.defragmentFile(
        inputPath = inputPath,
        outputPath = outputPath
    )
}
