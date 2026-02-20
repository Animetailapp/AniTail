package com.anitail.desktop.db.entities

import java.time.LocalDateTime

/**
 * Entity representing a playback event for history/stats tracking.
 * Mirrors Android's Event entity for feature parity.
 */
data class EventEntity(
    val id: Long = 0,
    val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long, // in milliseconds
)
