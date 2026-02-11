package com.anitail.desktop.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class DiscordProfile(
    val name: String,
    val username: String,
    val avatarUrl: String?,
)

object DesktopDiscordService {
    private val client = OkHttpClient.Builder().build()

    fun fetchProfile(token: String): DiscordProfile? {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) return null

        val request = Request.Builder()
            .url("https://discord.com/api/v10/users/@me")
            .header("Authorization", trimmedToken)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null

                val json = JSONObject(body)
                val id = json.optString("id")
                val avatarHash = json.optString("avatar")
                val discriminator = json.optString("discriminator")
                val username = json.optString("username").takeIf { it.isNotBlank() } ?: ""
                val globalName = json.optString("global_name").takeIf { it.isNotBlank() }
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
}
