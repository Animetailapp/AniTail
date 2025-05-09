package com.anitail.music.models.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tracks(
    @SerialName("total")
    val totalTracksCount: Int
)