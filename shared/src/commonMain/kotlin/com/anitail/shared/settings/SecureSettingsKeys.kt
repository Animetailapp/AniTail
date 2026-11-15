package com.anitail.shared.settings

import com.anitail.shared.constants.DiscordPendingAuthSessionKey
import com.anitail.shared.constants.LyricsOpenRouterOAuthKey
import com.anitail.shared.constants.LyricsOpenRouterOAuthSessionKey

internal val secureSettingsKeys =
    setOf(
        DiscordPendingAuthSessionKey,
        "innerTubeCookie",
        "visitorData",
        "dataSyncId",
        "innerTubeAuthUser",
        "lastFmSession",
        "listenBrainzToken",
        "lyricsAiApiKey",
        "lyricsAiApiKeys",
        LyricsOpenRouterOAuthKey,
        LyricsOpenRouterOAuthSessionKey,
        "lyricsDeepLApiKey",
        "accountName",
        "accountEmail",
        "accountChannelHandle",
        "accountThumbnail",
        "discordAccessToken",
        "discordRefreshToken",
        "listenTogetherDesktopSession",
        "listenTogetherServerUrl",
        "listenTogetherCustomServers",
        "listenTogetherServerMetadata",
        "deviceConnectPairedDevices",
        "listenTogetherPairedRoomCode",
        "spotifySession",
    )
