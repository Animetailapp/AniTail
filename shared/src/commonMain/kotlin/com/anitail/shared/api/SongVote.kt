package com.anitail.shared.api

import kotlinx.serialization.Serializable

@Serializable
internal enum class SongVote { LIKE, DISLIKE, INDIFFERENT }
