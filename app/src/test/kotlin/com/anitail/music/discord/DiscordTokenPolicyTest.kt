package com.anitail.music.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordTokenPolicyTest {

    @Test
    fun `parseTokenResponse extracts access token refresh token and expires_in`() {
        val raw = """
            {
              "token_type": "Bearer",
              "access_token": "acc_123",
              "expires_in": 604800,
              "refresh_token": "ref_456",
              "scope": "identify openid sdk.social_layer_presence"
            }
        """.trimIndent()

        val parsed = DiscordTokenPolicy.parseTokenResponse(raw)
        assertNotNull(parsed)
        assertEquals("acc_123", parsed?.accessToken)
        assertEquals("ref_456", parsed?.refreshToken)
        assertEquals(604800L, parsed?.expiresInSec)
    }

    @Test
    fun `parseTokenResponse handles missing refresh_token and rejects blank access_token or invalid json`() {
        val withoutRefresh = """{"access_token": "acc_only", "expires_in": 3600}"""
        val parsed = DiscordTokenPolicy.parseTokenResponse(withoutRefresh)
        assertNotNull(parsed)
        assertEquals("acc_only", parsed?.accessToken)
        assertNull(parsed?.refreshToken)
        assertEquals(3600L, parsed?.expiresInSec)

        assertNull(DiscordTokenPolicy.parseTokenResponse("""{"access_token": "   "}"""))
        assertNull(DiscordTokenPolicy.parseTokenResponse("""{"refresh_token": "ref_only"}"""))
        assertNull(DiscordTokenPolicy.parseTokenResponse("not-json"))
        assertNull(DiscordTokenPolicy.parseTokenResponse(""))
    }

    @Test
    fun `computeExpiresAtMillis and shouldRefreshProactively enforce 24h refresh margin`() {
        val now = 1_700_000_000_000L
        val sevenDaysSec = 604_800L
        val expiresAt = DiscordTokenPolicy.computeExpiresAtMillis(now, sevenDaysSec)

        assertEquals(now + sevenDaysSec * 1000L, expiresAt)
        assertEquals(0L, DiscordTokenPolicy.computeExpiresAtMillis(now, 0L))
        assertEquals(0L, DiscordTokenPolicy.computeExpiresAtMillis(now, -5L))

        // Fresh 7-day token should not refresh proactively
        assertFalse(DiscordTokenPolicy.shouldRefreshProactively(now, expiresAt))

        // 23 hours remaining (< 24h margin) should trigger proactive refresh
        val twentyThreeHoursLeft = expiresAt - 23L * 60L * 60L * 1000L
        assertTrue(DiscordTokenPolicy.shouldRefreshProactively(twentyThreeHoursLeft, expiresAt))

        // Already expired should trigger proactive refresh
        assertTrue(DiscordTokenPolicy.shouldRefreshProactively(expiresAt + 1_000L, expiresAt))

        // Unknown expiry (0L) should not trigger proactive refresh
        assertFalse(DiscordTokenPolicy.shouldRefreshProactively(now, 0L))
    }

    @Test
    fun `isPermanentTokenError distinguishes permanent auth errors from transient errors`() {
        assertTrue(DiscordTokenPolicy.isPermanentTokenError(401, ""))
        assertTrue(DiscordTokenPolicy.isPermanentTokenError(400, """{"error":"invalid_grant"}"""))
        assertTrue(DiscordTokenPolicy.isPermanentTokenError(400, """{"error":"INVALID_GRANT"}"""))

        assertFalse(DiscordTokenPolicy.isPermanentTokenError(400, """{"error":"invalid_request"}"""))
        assertFalse(DiscordTokenPolicy.isPermanentTokenError(429, "rate limited"))
        assertFalse(DiscordTokenPolicy.isPermanentTokenError(500, "internal server error"))
        assertFalse(DiscordTokenPolicy.isPermanentTokenError(503, "service unavailable"))
    }

    @Test
    fun `isGatewayAuthFailure detects disconnected 4004 and 401 only`() {
        assertTrue(DiscordTokenPolicy.isGatewayAuthFailure(statusCode = 0, errorCode = 2, errorDetail = 4004))
        assertTrue(DiscordTokenPolicy.isGatewayAuthFailure(statusCode = 0, errorCode = 2, errorDetail = 401))

        assertFalse(DiscordTokenPolicy.isGatewayAuthFailure(statusCode = 0, errorCode = 2, errorDetail = 1000))
        assertFalse(DiscordTokenPolicy.isGatewayAuthFailure(statusCode = 4, errorCode = 2, errorDetail = 4004))
    }
}

