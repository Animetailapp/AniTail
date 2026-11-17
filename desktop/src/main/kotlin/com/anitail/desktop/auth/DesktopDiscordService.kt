package com.anitail.desktop.auth

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
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
    const val DISCORD_APP_ID = "1359660031183818955"
    private const val DISCORD_URI_SCHEME = "discord-$DISCORD_APP_ID"
    const val DISCORD_REDIRECT_URI = "$DISCORD_URI_SCHEME:///authorize/callback"
    private const val DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize"
    private const val DISCORD_OAUTH_TOKEN = "https://discord.com/api/v10/oauth2/token"
    private const val DISCORD_SCOPES = "identify openid sdk.social_layer_presence"

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
            if (!response.isSuccessful) {
                println("[DiscordAuth] Token exchange failed HTTP ${response.code}: $body")
            }
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

        val usersMeRequest = Request.Builder()
            .url("https://discord.com/api/v10/users/@me")
            .header("Authorization", bearerHeader)
            .build()

        val oauthMeRequest = Request.Builder()
            .url("https://discord.com/api/v10/oauth2/@me")
            .header("Authorization", bearerHeader)
            .build()

        return runCatching {
            client.newCall(usersMeRequest).execute().use { response ->
                val body = response.body.string()
                if (response.isSuccessful && body.isNotBlank()) {
                    val user = JSONObject(body)
                    return@runCatching parseProfile(user)
                }
                println("[DiscordAuth] /users/@me HTTP ${response.code}: $body")
            }
            client.newCall(oauthMeRequest).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful || body.isBlank()) {
                    println("[DiscordAuth] /oauth2/@me HTTP ${response.code}: $body")
                    return@use null
                }
                val root = JSONObject(body)
                val user = root.optJSONObject("user") ?: return@use null
                parseProfile(user)
            }
        }.getOrNull()
    }

    private fun parseProfile(user: JSONObject): DiscordProfile {
        val id = user.optString("id")
        val avatarHash = user.optString("avatar")
        val discriminator = user.optString("discriminator")
        val username = user.optString("username").takeIf { it.isNotBlank() } ?: ""
        val globalName = user.optString("global_name").takeIf { it.isNotBlank() }
        val displayName = globalName ?: username.ifBlank { "Discord" }

        return DiscordProfile(
            name = displayName,
            username = username.ifBlank { displayName },
            avatarUrl = buildAvatarUrl(id = id, avatarHash = avatarHash, discriminator = discriminator),
        )
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

    suspend fun startBrowserOAuthLogin(
        timeoutMs: Int = 180_000,
    ): Result<DiscordAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val session = createAuthSession()
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { serverSocket ->
                serverSocket.soTimeout = timeoutMs
                val port = serverSocket.localPort
                registerCallbackRelay(port)
                openDiscordAuthUrl(session.authorizeUrl)

                val callbackUrl = serverSocket.accept().use { clientSocket ->
                    BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
                        .readLine()
                        .orEmpty()
                        .trim()
                }
                println("[DiscordAuth] Received OAuth callback: $callbackUrl")
                check(callbackUrl.startsWith("$DISCORD_URI_SCHEME:", ignoreCase = true)) {
                    "Unexpected Discord OAuth callback: $callbackUrl"
                }

                val query = callbackUrl.substringAfter("?", "")
                val params = parseQueryParams(query)
                val error = params["error"]
                if (!error.isNullOrBlank()) {
                    error(params["error_description"] ?: error)
                }
                val code = params["code"]?.takeIf { it.isNotBlank() }
                    ?: error("Missing authorization code")
                val state = params["state"].orEmpty()

                exchangeCode(
                    code = code,
                    verifier = session.pkceVerifier,
                    state = state,
                    expectedState = session.state,
                ).getOrThrow()
            }
        }
    }

    private fun openDiscordAuthUrl(authorizeUrl: String) {
        val discordAppUrl = if (authorizeUrl.startsWith("https://discord.com/oauth2/authorize")) {
            authorizeUrl.replace("https://discord.com", "discord://action")
        } else {
            authorizeUrl
        }
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "Desktop browser action is not supported"
        }
        try {
            Desktop.getDesktop().browse(URI(discordAppUrl))
        } catch (_: Exception) {
            if (discordAppUrl != authorizeUrl) {
                Desktop.getDesktop().browse(URI(authorizeUrl))
            } else {
                throw IllegalStateException("Unable to open Discord authorization URL")
            }
        }
    }

    private fun registerCallbackRelay(port: Int) {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return
        val baseKey = "Software\\Classes\\$DISCORD_URI_SCHEME"
        val commandKey = "$baseKey\\shell\\open\\command"
        val command =
            "powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -Command " +
                "\"\$c=New-Object Net.Sockets.TcpClient('127.0.0.1',$port);" +
                "\$w=New-Object IO.StreamWriter(\$c.GetStream());" +
                "\$w.WriteLine('%1');\$w.Flush();\$c.Close()\""

        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, baseKey)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, baseKey, "", "URL:AniTail Discord Auth")
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, baseKey, "URL Protocol", "")
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, commandKey)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, commandKey, "", command)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val key = URLDecoder.decode(pair.substring(0, idx), Charsets.UTF_8)
                val value = URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8)
                key to value
            }
        }.toMap()
    }
}
