package com.anitail.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackProxyRangeTest {
    @Test
    fun resolveRangeHeaderKeepsProvidedRange() {
        val url = "https://example.com/videoplayback?rqh=1"

        val resolved = PlaybackProxy.resolveRangeHeader("bytes=100-", url)

        assertEquals("bytes=100-", resolved)
    }

    @Test
    fun resolveRangeHeaderDefaultsWhenRqhRequiredAndMissing() {
        val url = "https://example.com/videoplayback?rqh=1&foo=bar"

        val resolved = PlaybackProxy.resolveRangeHeader(null, url)

        assertEquals("bytes=0-", resolved)
    }

    @Test
    fun resolveRangeHeaderReturnsNullWhenRqhNotRequired() {
        val url = "https://example.com/videoplayback?foo=bar"

        val resolved = PlaybackProxy.resolveRangeHeader(null, url)

        assertNull(resolved)
    }

    @Test
    fun shouldSendRefererSkipsBlank() {
        val shouldSend = PlaybackProxy.shouldSendReferer("")

        assertEquals(false, shouldSend)
    }

    @Test
    fun shouldSendCookieAllowsWhenRefererBlankButHostMatches() {
        // Mobile clients like IOS have empty referer but should still send cookies
        val shouldSend = PlaybackProxy.shouldSendCookie(
            cookie = "SAPISID=abc",
            targetUrl = "https://rr2---sn.example.googlevideo.com/videoplayback",
            referer = ""
        )

        assertEquals(true, shouldSend)
    }

    @Test
    fun shouldSendCookieAllowsWhenRefererPresentAndHostMatches() {
        val shouldSend = PlaybackProxy.shouldSendCookie(
            cookie = "SAPISID=abc",
            targetUrl = "https://rr2---sn.example.googlevideo.com/videoplayback",
            referer = "https://www.youtube.com/"
        )

        assertEquals(true, shouldSend)
    }

    @Test
    fun shouldSendCookieSkipsWhenHostDoesNotMatch() {
        val shouldSend = PlaybackProxy.shouldSendCookie(
            cookie = "SAPISID=abc",
            targetUrl = "https://example.com/videoplayback",
            referer = "https://www.youtube.com/"
        )

        assertEquals(false, shouldSend)
    }
}
