package com.anitail.music.discord

data class DiscordActivity(
    val activityType: Int = 2,
    val name: String?,
    val state: String,
    val details: String?,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val largeImage: String?,
    val largeText: String?,
    val smallImage: String?,
    val smallText: String?,
    val button1Label: String?,
    val button1Url: String?,
    val button2Label: String?,
    val button2Url: String?,
    val partyId: String? = null,
    val partyCurrentSize: Int = 0,
    val partyMaxSize: Int = 0,
    val joinSecret: String? = null,
) {
    companion object {
        const val TYPE_PLAYING = 0
        const val TYPE_STREAMING = 1
        const val TYPE_LISTENING = 2
        const val TYPE_WATCHING = 3
        const val TYPE_CUSTOM_STATUS = 4
        const val TYPE_COMPETING = 5
    }
}
