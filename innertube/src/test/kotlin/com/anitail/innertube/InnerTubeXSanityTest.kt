package com.anitail.innertube

import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
import org.junit.Assert.assertNotNull
import org.junit.Test

class InnerTubeXSanityTest {
    @Test
    fun testInnerTubeXLoads() {
        assertNotNull(YouTubeClient.WEB_REMIX)
        val locale = YouTubeLocale("US", "en-US")
        assertNotNull(locale)
    }
}

