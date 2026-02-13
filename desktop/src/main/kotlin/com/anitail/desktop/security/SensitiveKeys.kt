package com.anitail.desktop.security

object SensitiveKeys {
    const val YOUTUBE_VISITOR_DATA = "youtube.visitor_data"
    const val YOUTUBE_DATA_SYNC_ID = "youtube.data_sync_id"
    const val YOUTUBE_COOKIE = "youtube.cookie"

    const val DISCORD_TOKEN = "discord.token"
    const val SPOTIFY_ACCESS_TOKEN = "spotify.access_token"
    const val SPOTIFY_REFRESH_TOKEN = "spotify.refresh_token"
    const val LASTFM_SESSION_KEY = "lastfm.session_key"
    const val PROXY_PASSWORD = "proxy.password"

    val ALL = listOf(
        YOUTUBE_VISITOR_DATA,
        YOUTUBE_DATA_SYNC_ID,
        YOUTUBE_COOKIE,
        DISCORD_TOKEN,
        SPOTIFY_ACCESS_TOKEN,
        SPOTIFY_REFRESH_TOKEN,
        LASTFM_SESSION_KEY,
        PROXY_PASSWORD,
    )
}
