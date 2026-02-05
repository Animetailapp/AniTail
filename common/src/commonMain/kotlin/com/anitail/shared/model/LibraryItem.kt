package com.anitail.shared.model

data class LibraryItem(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playbackUrl: String,
    val durationMs: Long? = null,
)
