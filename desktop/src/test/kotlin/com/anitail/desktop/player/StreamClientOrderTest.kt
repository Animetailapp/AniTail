package com.anitail.desktop.player

import com.anitail.innertube.models.YouTubeClient
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamClientOrderTest {
    @Test
    fun buildClientOrderPrefersAndroidVrAndOmitsMweb() {
        val order = StreamClientOrder.build()

        assertEquals(
            listOf(
                YouTubeClient.WEB_REMIX,
                YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                YouTubeClient.WEB,
                YouTubeClient.ANDROID_VR_NO_AUTH,
                YouTubeClient.MOBILE,
                YouTubeClient.IOS,
                YouTubeClient.WEB_CREATOR,
            ),
            order
        )
    }
}
