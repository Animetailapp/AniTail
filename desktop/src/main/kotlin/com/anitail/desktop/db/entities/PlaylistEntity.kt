package com.anitail.desktop.db.entities

import com.anitail.desktop.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Entity representing a playlist in the database.
 * Mirrors Android's PlaylistEntity for feature parity.
 */
data class PlaylistEntity(
    val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val backgroundImageUrl: String? = null
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        private val charPool = ('A'..'Z') + ('a'..'z')

        fun generatePlaylistId(): String {
            val randomString = (1..8)
                .map { charPool[Random.nextInt(charPool.size)] }
                .joinToString("")
            return "LP$randomString"
        }
    }

    val shareLink: String?
        get() = browseId?.let { "https://music.youtube.com/playlist?list=$it" }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            if (browseId != null)
                YouTube.likePlaylist(browseId, bookmarkedAt == null)
            this.cancel()
        }
    }
}
