package com.anitail.music.utils

import android.content.Context
import com.anitail.music.R
import com.anitail.music.db.entities.Song
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage

class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(token) {

    suspend fun updateSong(
        song: Song,
        timeStart: Long,
        timeEnd: Long
    ) = runCatching {
        val primaryArtist = song.artists.firstOrNull()?.name ?: song.song.artistName
        val albumTitle = song.album?.title
        val presenceState = buildString {
            append(primaryArtist ?: context.getString(R.string.unknown))
            if (!albumTitle.isNullOrBlank()) {
                append(" • ")
                append(albumTitle)
            }
        }
        val fallbackAsset = FALLBACK_DISCORD_ASSET
        val largeAsset = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it, fallbackAsset) }
            ?: RpcImage.DiscordImage(fallbackAsset)
        val smallAsset =
            song.artists.firstOrNull()?.thumbnailUrl?.let {
                RpcImage.ExternalImage(
                    it,
                    fallbackAsset
                )
            }
                ?: RpcImage.DiscordImage(fallbackAsset)

        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = song.song.title,
            state = presenceState,
            largeImage = largeAsset,
            smallImage = smallAsset,
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.song.id}",
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