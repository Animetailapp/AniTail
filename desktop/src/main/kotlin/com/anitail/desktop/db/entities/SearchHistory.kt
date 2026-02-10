package com.anitail.desktop.db.entities

/**
 * Entity representing a search history entry.
 */
data class SearchHistory(
    val id: Long = 0,
    val query: String,
)
