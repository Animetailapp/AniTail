package com.anitail.desktop.db.entities

/**
 * Relation map between songs and artists.
 * Mirrors Android's SongArtistMap for parity.
 */
data class SongArtistMap(
    val songId: String,
    val artistId: String,
    val position: Int = 0,
)
