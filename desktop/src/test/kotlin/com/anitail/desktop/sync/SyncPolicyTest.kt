package com.anitail.desktop.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPolicyTest {
    @Test
    fun returnsFalseWhenSyncDisabledEvenWithCookie() {
        val result = shouldStartSync(ytmSync = false, cookie = "SAPISID=abc")
        assertFalse(result)
    }

    @Test
    fun returnsFalseWhenCookieMissingEvenIfSyncEnabled() {
        val result = shouldStartSync(ytmSync = true, cookie = " ")
        assertFalse(result)
    }

    @Test
    fun returnsTrueWhenSyncEnabledAndCookiePresent() {
        val result = shouldStartSync(ytmSync = true, cookie = "SAPISID=abc")
        assertTrue(result)
    }
}
