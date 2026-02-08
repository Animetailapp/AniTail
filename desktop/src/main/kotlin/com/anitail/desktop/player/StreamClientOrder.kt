package com.anitail.desktop.player

import com.anitail.innertube.models.YouTubeClient

internal object StreamClientOrder {
    fun build(): List<YouTubeClient> {
        val mainClient = YouTubeClient.WEB_REMIX
        val prioritized = listOf(
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.WEB,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.MOBILE,
            YouTubeClient.IOS,
            YouTubeClient.WEB_CREATOR,
        )
        return prioritized.distinct()
    }
}
