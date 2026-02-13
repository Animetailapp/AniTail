package com.anitail.desktop.db.entities

/**
 * Relation map between a seed song and its related songs.
 * Mirrors Android's RelatedSongMap for quick picks parity.
 */
data class RelatedSongMap(
    val id: Long = 0,
    val songId: String,
    val relatedSongId: String,
)
