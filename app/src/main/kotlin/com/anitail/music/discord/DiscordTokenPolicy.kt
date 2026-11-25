package com.anitail.music.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class DiscordOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
)

object DiscordTokenPolicy {
    const val PROACTIVE_REFRESH_MARGIN_MS = 24L * 60L * 60L * 1000L
    const val REFRESH_FAILURE_COOLDOWN_MS = 30_000L

    fun parseTokenResponse(rawJson: String): DiscordOAuthTokens? {
        if (rawJson.isBlank()) return null
        val json = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val accessToken = json["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val refreshToken = json["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val expiresInSec = json["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L
        return DiscordOAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSec = expiresInSec,
        )
    }

    fun computeExpiresAtMillis(nowMillis: Long, expiresInSec: Long): Long =
        if (expiresInSec > 0L) nowMillis + expiresInSec * 1000L else 0L

    fun shouldRefreshProactively(nowMillis: Long, expiresAtMillis: Long): Boolean =
        expiresAtMillis > 0L && nowMillis + PROACTIVE_REFRESH_MARGIN_MS >= expiresAtMillis

    fun isPermanentTokenError(httpCode: Int, responseBody: String): Boolean =
        httpCode == 401 || (httpCode == 400 && responseBody.contains("invalid_grant", ignoreCase = true))

    fun isGatewayAuthFailure(statusCode: Int, errorCode: Int, errorDetail: Int): Boolean =
        statusCode == 0 && (errorDetail == 4004 || errorDetail == 401)
}

