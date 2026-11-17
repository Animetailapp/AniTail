package com.anitail.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)

data class YouTubeChannelAccount(
    val name: String,
    val handle: String?,
    val byline: String?,
    val thumbnailUrl: String?,
    val dataSyncId: String?,
    val pageId: String?,
    val isSelected: Boolean,
)
