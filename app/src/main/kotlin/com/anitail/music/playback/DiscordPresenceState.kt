package com.anitail.music.playback

/**
 * Represents the high level state of the Discord Rich Presence integration.
 */
enum class DiscordGatewayStatus {
    DISABLED,
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

/**
 * Aggregated snapshot consumed by UI to render gateway status, last sync, and manual refresh state.
 */
data class DiscordPresenceState(
    val status: DiscordGatewayStatus = DiscordGatewayStatus.DISABLED,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val manualRefreshInFlight: Boolean = false,
)
