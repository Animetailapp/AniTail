package com.anitail.desktop.db.entities

/**
 * Junction table for many-to-many relationship between playlists and songs.
 */
data class PlaylistSongMap(
    val id: Int = 0,
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    val setVideoId: String? = null,
)
