package com.anitail.desktop.player

import com.anitail.innertube.YouTube
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlaybackProxyTest {
    @Test
    fun createProxyUrlIncludesCookieForYouTubeHosts() {
        val cookie = "SAPISID=abc; __Secure-3PAPISID=def"
        val previous = YouTube.cookie
        try {
            YouTube.cookie = cookie
            val url = PlaybackProxy.createProxyUrl(
                "https://rr2---sn-hpa7zn66.googlevideo.com/videoplayback?foo=1"
            )
            val params = parseQueryParams(url)
            val encoded = params["ck"]
            assertNotNull(encoded)
            val decoded = String(Base64.getUrlDecoder().decode(encoded))
            assertEquals(cookie, decoded)
        } finally {
            YouTube.cookie = previous
        }
    }

    @Test
    fun createProxyUrlOmitsCookieWhenMissing() {
        val previous = YouTube.cookie
        try {
            YouTube.cookie = null
            val url = PlaybackProxy.createProxyUrl(
                "https://rr2---sn-hpa7zn66.googlevideo.com/videoplayback?foo=1"
            )
            val params = parseQueryParams(url)
            assertNull(params["ck"])
        } finally {
            YouTube.cookie = previous
        }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { entry ->
            val key = entry.substringBefore("=")
            val value = entry.substringAfter("=", "")
            key to value
        }
    }
}
