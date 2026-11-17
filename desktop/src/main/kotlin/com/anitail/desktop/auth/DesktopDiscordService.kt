package com.anitail.desktop.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class DiscordProfile(
    val name: String,
    val username: String,
    val avatarUrl: String?,
)

data class DiscordAuthSession(
    val authorizeUrl: String,
    val redirectUri: String,
    val pkceVerifier: String,
    val state: String,
)

data class DiscordAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val scope: String,
)

object DesktopDiscordService {
    const val DISCORD_APP_ID = "1447278780795064401"
    const val DISCORD_REDIRECT_URI = "metrolist-auth://discord/callback"
    private const val DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize"
    private const val DISCORD_OAUTH_TOKEN = "https://discord.com/api/v10/oauth2/token"
    private const val DISCORD_SCOPES = "openid sdk.social_layer_presence"

    private val client = OkHttpClient.Builder().build()
    private val random = SecureRandom()

    fun createAuthSession(): DiscordAuthSession {
        val verifierBytes = ByteArray(64).also { random.nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challengeBytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
        val stateBytes = ByteArray(16).also { random.nextBytes(it) }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

        val authorizeUrl = buildString {
            append(DISCORD_OAUTH_AUTHORIZE)
            append("?client_id=").append(encode(DISCORD_APP_ID))
            append("&response_type=code")
            append("&redirect_uri=").append(encode(DISCORD_REDIRECT_URI))
            append("&scope=").append(encode(DISCORD_SCOPES))
            append("&state=").append(encode(state))
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(encode(challenge))
        }

        return DiscordAuthSession(
            authorizeUrl = authorizeUrl,
            redirectUri = DISCORD_REDIRECT_URI,
            pkceVerifier = verifier,
            state = state,
        )
    }

    fun exchangeCode(
        code: String,
        verifier: String,
        state: String,
        expectedState: String,
    ): Result<DiscordAuthResult> = runCatching {
        require(state == expectedState) { "OAuth state mismatch" }
        performTokenExchange(
            mapOf(
                "client_id" to DISCORD_APP_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to DISCORD_REDIRECT_URI,
                "code_verifier" to verifier,
            ),
        )
    }

    fun refreshToken(refreshToken: String): Result<DiscordAuthResult> = runCatching {
        performTokenExchange(
            mapOf(
                "client_id" to DISCORD_APP_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )
    }

    private fun performTokenExchange(params: Map<String, String>): DiscordAuthResult {
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder()
            .url(DISCORD_OAUTH_TOKEN)
            .post(form)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "Discord OAuth token error HTTP ${response.code}: $body" }
            val json = JSONObject(body)
            return DiscordAuthResult(
                accessToken = json.optString("access_token"),
                refreshToken = json.optString("refresh_token"),
                expiresInSec = json.optLong("expires_in", 0L),
                scope = json.optString("scope", DISCORD_SCOPES),
            )
        }
    }

    fun fetchProfile(token: String): DiscordProfile? {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) return null

        val bearerHeader = if (trimmedToken.startsWith("Bearer ", ignoreCase = true)) {
            trimmedToken
        } else {
            "Bearer $trimmedToken"
        }

        val request = Request.Builder()
            .url("https://discord.com/api/v10/oauth2/@me")
            .header("Authorization", bearerHeader)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                if (body.isBlank()) return@use null

                val root = JSONObject(body)
                val user = root.optJSONObject("user") ?: return@use null
                val id = user.optString("id")
                val avatarHash = user.optString("avatar")
                val discriminator = user.optString("discriminator")
                val username = user.optString("username").takeIf { it.isNotBlank() } ?: ""
                val globalName = user.optString("global_name").takeIf { it.isNotBlank() }
                val displayName = globalName ?: username.ifBlank { "Discord" }

                DiscordProfile(
                    name = displayName,
                    username = username.ifBlank { displayName },
                    avatarUrl = buildAvatarUrl(id = id, avatarHash = avatarHash, discriminator = discriminator),
                )
            }
        }.getOrNull()
    }

    private fun buildAvatarUrl(
        id: String,
        avatarHash: String?,
        discriminator: String?,
    ): String? {
        if (id.isBlank()) return null
        val hash = avatarHash?.takeIf { it.isNotBlank() }
        if (hash != null) {
            val ext = if (hash.startsWith("a_")) "gif" else "png"
            return "https://cdn.discordapp.com/avatars/$id/$hash.$ext?size=256"
        }

        val defaultIndex = discriminator?.toIntOrNull()?.mod(5)
            ?: (((id.toLongOrNull() ?: 0L) shr 22) % 6).toInt()
        return "https://cdn.discordapp.com/embed/avatars/$defaultIndex.png"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
