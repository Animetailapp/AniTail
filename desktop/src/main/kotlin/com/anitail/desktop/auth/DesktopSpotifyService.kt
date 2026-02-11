package com.anitail.desktop.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class SpotifyAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class SpotifyUserProfile(
    val id: String,
    val name: String,
)

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val imageUrl: String?,
)

data class SpotifyPlaylistsPage(
    val total: Int,
    val items: List<SpotifyPlaylistSummary>,
    val hasMore: Boolean,
    val nextOffset: Int,
)

sealed class SpotifyServiceResult<out T> {
    data class Success<T>(val value: T) : SpotifyServiceResult<T>()
    data class Error(val message: String) : SpotifyServiceResult<Nothing>()
}

object DesktopSpotifyService {
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val PROFILE_URL = "https://api.spotify.com/v1/me"
    private const val PLAYLISTS_URL = "https://api.spotify.com/v1/me/playlists"
    const val DEFAULT_REDIRECT_URI: String = "http://127.0.0.1:8888"
    private const val DEFAULT_SCOPE: String =
        "user-library-read playlist-read-private playlist-read-collaborative"

    private val client = OkHttpClient.Builder().build()

    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String = DEFAULT_REDIRECT_URI,
    ): String {
        val encodedClientId = urlEncode(clientId.trim())
        val encodedRedirect = urlEncode(redirectUri)
        val encodedScope = urlEncode(DEFAULT_SCOPE)
        return "https://accounts.spotify.com/authorize" +
            "?client_id=$encodedClientId" +
            "&response_type=code" +
            "&redirect_uri=$encodedRedirect" +
            "&scope=$encodedScope"
    }

    fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String,
        authorizationCode: String,
        redirectUri: String = DEFAULT_REDIRECT_URI,
    ): SpotifyServiceResult<SpotifyAuthTokens> {
        if (clientId.isBlank() || clientSecret.isBlank() || authorizationCode.isBlank()) {
            return SpotifyServiceResult.Error("Missing Spotify credentials or authorization code.")
        }

        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Authorization", basicAuth(clientId, clientSecret))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(
                formBody(
                    "grant_type" to "authorization_code",
                    "code" to authorizationCode.trim(),
                    "redirect_uri" to redirectUri,
                )
            )
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SpotifyServiceResult.Error(parseSpotifyError(bodyText))
                }
                val json = JSONObject(bodyText)
                val accessToken = json.optString("access_token", "")
                if (accessToken.isBlank()) {
                    return@use SpotifyServiceResult.Error("Spotify token response was empty.")
                }
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                SpotifyServiceResult.Success(
                    SpotifyAuthTokens(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                    )
                )
            }
        }.getOrElse {
            SpotifyServiceResult.Error(it.message ?: "Failed to request Spotify token.")
        }
    }

    fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): SpotifyServiceResult<SpotifyAuthTokens> {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            return SpotifyServiceResult.Error("Missing Spotify credentials or refresh token.")
        }

        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Authorization", basicAuth(clientId, clientSecret))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(
                formBody(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken.trim(),
                )
            )
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SpotifyServiceResult.Error(parseSpotifyError(bodyText))
                }
                val json = JSONObject(bodyText)
                val accessToken = json.optString("access_token", "")
                if (accessToken.isBlank()) {
                    return@use SpotifyServiceResult.Error("Spotify refresh response was empty.")
                }
                val updatedRefreshToken = json.optString("refresh_token", refreshToken)
                val expiresIn = json.optLong("expires_in", 3600L)
                SpotifyServiceResult.Success(
                    SpotifyAuthTokens(
                        accessToken = accessToken,
                        refreshToken = updatedRefreshToken.ifBlank { refreshToken },
                        expiresInSeconds = expiresIn,
                    )
                )
            }
        }.getOrElse {
            SpotifyServiceResult.Error(it.message ?: "Failed to refresh Spotify token.")
        }
    }

    fun fetchUserProfile(accessToken: String): SpotifyServiceResult<SpotifyUserProfile> {
        val token = accessToken.trim()
        if (token.isBlank()) {
            return SpotifyServiceResult.Error("Missing Spotify access token.")
        }

        val request = Request.Builder()
            .url(PROFILE_URL)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SpotifyServiceResult.Error(parseSpotifyError(bodyText))
                }
                val json = JSONObject(bodyText)
                val id = json.optString("id", "")
                val name = json.optString("display_name", "").ifBlank {
                    json.optString("id", "Spotify")
                }
                SpotifyServiceResult.Success(
                    SpotifyUserProfile(
                        id = id,
                        name = name,
                    )
                )
            }
        }.getOrElse {
            SpotifyServiceResult.Error(it.message ?: "Failed to fetch Spotify profile.")
        }
    }

    fun fetchPlaylists(
        accessToken: String,
        offset: Int = 0,
        limit: Int = 50,
    ): SpotifyServiceResult<SpotifyPlaylistsPage> {
        val token = accessToken.trim()
        if (token.isBlank()) {
            return SpotifyServiceResult.Error("Missing Spotify access token.")
        }

        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 50)
        val request = Request.Builder()
            .url("$PLAYLISTS_URL?offset=$safeOffset&limit=$safeLimit")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SpotifyServiceResult.Error(parseSpotifyError(bodyText))
                }
                val json = JSONObject(bodyText)
                val total = json.optInt("total", 0)
                val itemsArray = json.optJSONArray("items") ?: JSONArray()
                val items = buildList(itemsArray.length()) {
                    for (index in 0 until itemsArray.length()) {
                        val item = itemsArray.optJSONObject(index) ?: continue
                        val id = item.optString("id", "")
                        if (id.isBlank()) continue
                        val name = item.optString("name", "").ifBlank { "Spotify Playlist" }
                        val imageUrl = item.optJSONArray("images")
                            ?.optJSONObject(0)
                            ?.optString("url")
                            ?.takeIf { it.isNotBlank() }
                        add(
                            SpotifyPlaylistSummary(
                                id = id,
                                name = name,
                                imageUrl = imageUrl,
                            )
                        )
                    }
                }
                val nextOffset = safeOffset + items.size
                SpotifyServiceResult.Success(
                    SpotifyPlaylistsPage(
                        total = total,
                        items = items,
                        hasMore = nextOffset < total,
                        nextOffset = nextOffset,
                    )
                )
            }
        }.getOrElse {
            SpotifyServiceResult.Error(it.message ?: "Failed to fetch Spotify playlists.")
        }
    }

    private fun formBody(vararg pairs: Pair<String, String>): okhttp3.RequestBody {
        val encoded = pairs.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return encoded.toRequestBody()
    }

    private fun basicAuth(clientId: String, clientSecret: String): String {
        val value = "${clientId.trim()}:${clientSecret.trim()}"
        val encoded = Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }

    private fun parseSpotifyError(body: String): String {
        if (body.isBlank()) return "Spotify request failed."
        return runCatching {
            val json = JSONObject(body)
            if (json.has("error_description")) {
                json.optString("error_description")
            } else if (json.has("error")) {
                val error = json.opt("error")
                when (error) {
                    is JSONObject -> {
                        val message = error.optString("message", "")
                        if (message.isNotBlank()) message else error.optString("status", "Spotify error")
                    }

                    else -> error.toString()
                }
            } else {
                body
            }
        }.getOrDefault(body)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
