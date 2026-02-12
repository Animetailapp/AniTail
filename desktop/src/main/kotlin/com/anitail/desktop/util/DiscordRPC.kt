package com.anitail.desktop.util

import com.anitail.shared.model.LibraryItem
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage

class DesktopDiscordRPC(
    token: String,
) : KizzyRPC(
    token = token,
    os = "Android",
    browser = "Discord Android",
    device = "Generic Android Device",
    userAgent = SuperProperties.userAgent,
    superPropertiesBase64 = SuperProperties.superPropertiesBase64
) {

    suspend fun updateSong(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,
        timeEnd: Long
    ) = runCatching {
        val presenceState = buildString {
            append(item.artist)
            if (!albumName.isNullOrBlank()) {
                append(" - ")
                append(albumName)
            }
        }

        // Use a generic music icon as fallback if everything else fails
        val fallbackAsset = "https://cdn-icons-png.flaticon.com/512/3844/3844724.png"

        val largeAsset = item.artworkUrl?.let { RpcImage.ExternalImage(it, fallbackAsset) }
            ?: RpcImage.ExternalImage(fallbackAsset)

        val smallAsset = artistThumbnailUrl?.let {
            RpcImage.ExternalImage(
                it,
                fallbackAsset
            )
        }

        setActivity(
            name = "AniTail",
            details = item.title,
            state = presenceState,
            largeImage = largeAsset,
            smallImage = smallAsset,
            largeText = albumName ?: item.artist,
            smallText = item.artist,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${item.id}",
                "Join our discord" to "https://discord.gg/H8x3yNbc67",
            ),
            type = Type.LISTENING,
            startTime = timeStart,
            endTime = timeEnd,
            applicationId = APPLICATION_ID
        )
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
    }
}
