package com.anitail.music.betterlyrics.models

import kotlinx.serialization.Serializable

@Serializable
data class TTMLResponse(
    val ttml: String? = null,
)
