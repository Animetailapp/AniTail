package com.anitail.desktop.db.entities

import java.time.LocalDateTime

/**
 * Entity representing an album in the database.
 * Mirrors Android's AlbumEntity for feature parity.
 */
data class AlbumEntity(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0, // in seconds
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
) {
    fun toggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )
}
