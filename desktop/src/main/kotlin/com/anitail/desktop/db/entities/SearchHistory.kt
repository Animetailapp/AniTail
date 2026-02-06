package com.anitail.desktop.db.entities

import java.time.LocalDateTime

/**
 * Entity representing a search history entry.
 */
data class SearchHistory(
    val id: Long = 0,
    val query: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
