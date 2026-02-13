package com.anitail.desktop.db.entities

import com.anitail.desktop.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            if (channelId == null)
                YouTube.subscribeChannel(YouTube.getChannelId(id), bookmarkedAt == null)
            else
                YouTube.subscribeChannel(channelId, bookmarkedAt == null)
            this.cancel()
        }
    }

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
