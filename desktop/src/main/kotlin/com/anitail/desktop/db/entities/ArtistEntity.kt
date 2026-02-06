package com.anitail.desktop.db.entities

import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Entity representing an artist in the database.
 * Mirrors Android's ArtistEntity for feature parity.
 */
data class ArtistEntity(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    val isLocalArtist: Boolean
        get() = id.startsWith("LA")

    fun toggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    companion object {
        private val charPool = ('A'..'Z') + ('a'..'z')

        fun generateArtistId(): String {
            val randomString = (1..8)
                .map { charPool[Random.nextInt(charPool.size)] }
                .joinToString("")
            return "LA$randomString"
        }
    }
}
