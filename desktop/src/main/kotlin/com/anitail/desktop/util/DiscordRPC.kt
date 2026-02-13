package com.anitail.desktop.util

import com.anitail.shared.model.LibraryItem
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage


class DesktopDiscordRPC(
    token: String,
) : KizzyRPC(token) {

    suspend fun updateSong(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,
        timeEnd: Long
    ) = runCatching {
        val presenceState = buildString {
            append(item.artist)
        }
        val fallbackAsset = FALLBACK_DISCORD_ASSET
        val largeAsset = item.artworkUrl?.let { RpcImage.ExternalImage(it, fallbackAsset) }
            ?: RpcImage.DiscordImage(fallbackAsset)
        val smallAsset = artistThumbnailUrl?.let {
            RpcImage.ExternalImage(
                it,
                fallbackAsset
            )
        } ?: RpcImage.DiscordImage(fallbackAsset)

        setActivity(
            name = "AniTail Music",
            details = item.title,
            state = presenceState,
            largeImage = largeAsset,
            smallImage = smallAsset,
            largeText = "Album: " + (albumName ?: "Unknown"),
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
        private const val FALLBACK_DISCORD_ASSET =
            "emojis/1372344240465645711.webp?quality=lossless"
    }
}
