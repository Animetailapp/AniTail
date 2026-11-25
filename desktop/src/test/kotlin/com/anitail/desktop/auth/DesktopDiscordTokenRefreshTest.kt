package com.anitail.desktop.auth

import com.anitail.desktop.security.SecureSecretsStore
import com.anitail.desktop.security.SensitiveKeys
import com.anitail.desktop.storage.DesktopPreferences
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDiscordTokenRefreshTest {
    private class InMemorySecureStore : SecureSecretsStore {
        private val map = ConcurrentHashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        override fun clearAll() {
            map.clear()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "Timed out waiting for async preference persistence")
    }

    @Test
    fun `computeExpiresAtMillis and shouldRefreshProactively respect 24h margin`() {
        val now = 1_700_000_000_000L
        val expiresAt = DesktopDiscordService.computeExpiresAtMillis(now, 604_800L)
        assertEquals(now + 604_800_000L, expiresAt)
        assertEquals(0L, DesktopDiscordService.computeExpiresAtMillis(now, 0L))

        assertFalse(DesktopDiscordService.shouldRefreshProactively(now, expiresAt))
        assertTrue(
            DesktopDiscordService.shouldRefreshProactively(
                expiresAt - 23L * 60L * 60L * 1000L,
                expiresAt,
            ),
        )
        assertTrue(DesktopDiscordService.shouldRefreshProactively(expiresAt + 1_000L, expiresAt))
        assertFalse(DesktopDiscordService.shouldRefreshProactively(now, 0L))
    }

    @Test
    fun `formatBearerToken avoids double prefixing`() {
        assertEquals("", DesktopDiscordService.formatBearerToken("   "))
        assertEquals("Bearer abc123", DesktopDiscordService.formatBearerToken("abc123"))
        assertEquals("Bearer abc123", DesktopDiscordService.formatBearerToken("  abc123  "))
        assertEquals("Bearer abc123", DesktopDiscordService.formatBearerToken("Bearer abc123"))
        assertEquals("Bearer abc123", DesktopDiscordService.formatBearerToken("bearer   abc123 "))
    }

    @Test
    fun `DesktopPreferences persists discordTokenExpiryEpochMillis and clearDiscordSession resets all fields`() {
        val tempFile = Files.createTempFile("anitail-prefs-discord", ".json")
        val secureStore = InMemorySecureStore()
        try {
            val prefs = DesktopPreferences.createForTest(tempFile, secureStore)
            val expectedExpiry = 1_700_604_800_000L
            prefs.setDiscordToken("acc_token")
            prefs.setDiscordRefreshToken("ref_token")
            prefs.setDiscordTokenExpiryEpochMillis(expectedExpiry)
            prefs.setDiscordName("Display")
            prefs.setDiscordUsername("user")
            prefs.setDiscordAvatarUrl("https://cdn.discordapp.com/avatars/1/a.png")
            prefs.flush()

            awaitCondition {
                secureStore.get(SensitiveKeys.DISCORD_TOKEN) == "acc_token" &&
                    secureStore.get(SensitiveKeys.DISCORD_REFRESH_TOKEN) == "ref_token"
            }

            val reloaded = DesktopPreferences.createForTest(tempFile, secureStore)
            assertEquals("acc_token", reloaded.discordToken.value)
            assertEquals("ref_token", reloaded.discordRefreshToken.value)
            assertEquals(expectedExpiry, reloaded.discordTokenExpiryEpochMillis.value)
            assertEquals("Display", reloaded.discordName.value)
            assertEquals("user", reloaded.discordUsername.value)

            reloaded.clearDiscordSession()
            reloaded.flush()

            awaitCondition {
                secureStore.get(SensitiveKeys.DISCORD_TOKEN) == null &&
                    secureStore.get(SensitiveKeys.DISCORD_REFRESH_TOKEN) == null
            }

            assertEquals("", reloaded.discordToken.value)
            assertEquals("", reloaded.discordRefreshToken.value)
            assertEquals(0L, reloaded.discordTokenExpiryEpochMillis.value)
            assertEquals("", reloaded.discordName.value)
            assertEquals("", reloaded.discordUsername.value)
            assertEquals("", reloaded.discordAvatarUrl.value)
            assertEquals(null, secureStore.get(SensitiveKeys.DISCORD_TOKEN))
            assertEquals(null, secureStore.get(SensitiveKeys.DISCORD_REFRESH_TOKEN))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
