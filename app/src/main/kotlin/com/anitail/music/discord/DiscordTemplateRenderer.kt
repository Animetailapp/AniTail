package com.anitail.music.discord

import com.anitail.music.db.entities.Song

object DiscordTemplateRenderer {
    fun render(template: String, song: Song): String {
        var result = template
            .replace("{song.name}", song.song.title)
            .replace("{song.id}", song.song.id)
            .replace("{artist.name}", song.artists.joinToString { it.name }.ifEmpty { DiscordDefaults.UNKNOWN_ARTIST })
            .replace("{album.name}", song.album?.title ?: DiscordDefaults.UNKNOWN_ALBUM)
        return result
    }

    fun render(
        template: String,
        title: String,
        artist: String,
        album: String?,
        songId: String = "",
    ): String {
        var result = template
            .replace("{song.name}", title)
            .replace("{song.id}", songId)
            .replace("{artist.name}", artist)
            .replace("{album.name}", album ?: DiscordDefaults.UNKNOWN_ALBUM)
        return result
    }

    val PLACEHOLDERS = listOf(
        "{song.name}",
        "{artist.name}",
        "{album.name}",
        "{song.id}",
    )
}
