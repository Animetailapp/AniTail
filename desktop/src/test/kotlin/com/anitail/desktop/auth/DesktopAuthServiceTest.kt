package com.anitail.desktop.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAuthServiceTest {
    @Test
    fun isLoggedInRequiresCookie() {
        val tempFile = Files.createTempFile("anitail-cred", ".json")
        try {
            Files.writeString(tempFile, """{"dataSyncId":"sync-only"}""")
            val service = DesktopAuthService(tempFile)
            assertFalse(service.isLoggedIn)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun isLoggedInTrueWhenCookiePresent() {
        val tempFile = Files.createTempFile("anitail-cred", ".json")
        try {
            Files.writeString(tempFile, """{"cookie":"SAPISID=abc"}""")
            val service = DesktopAuthService(tempFile)
            assertTrue(service.isLoggedIn)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
