package com.anitail.desktop.auth

import com.anitail.desktop.security.SecureSecretsStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAuthServiceTest {
    private class TestSecureStore : SecureSecretsStore {
        private val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun clearAll() { map.clear() }
    }

    @Test
    fun isLoggedInRequiresCookie() {
        val tempFile = Files.createTempFile("anitail-cred", ".json")
        try {
            Files.writeString(tempFile, """{"dataSyncId":"sync-only"}""")
            val service = DesktopAuthService(tempFile, TestSecureStore())
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
            val service = DesktopAuthService(tempFile, TestSecureStore())
            assertTrue(service.isLoggedIn)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
