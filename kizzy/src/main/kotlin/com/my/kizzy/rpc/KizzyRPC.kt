/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRPC.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.rpc

import com.my.kizzy.gateway.DiscordWebSocket
import com.my.kizzy.gateway.GatewayConnectionState
import com.my.kizzy.gateway.entities.presence.Activity
import com.my.kizzy.gateway.entities.presence.Assets
import com.my.kizzy.gateway.entities.presence.Metadata
import com.my.kizzy.gateway.entities.presence.Presence
import com.my.kizzy.gateway.entities.presence.Timestamps
import com.my.kizzy.repository.KizzyRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Modified by Zion Huang
 */
open class KizzyRPC(
    private val token: String,
    os: String = "Android",
    browser: String = "Discord Android",
    device: String = "Generic Android Device",
    private val userAgent: String = "Discord-Android/314013;RNA",
    private val superPropertiesBase64: String? = null
) {
    private val kizzyRepository = KizzyRepository()
    private val discordWebSocket = DiscordWebSocket(token, os, browser, device)
    private val httpClient = HttpClient()

    val connectionState: StateFlow<GatewayConnectionState> = discordWebSocket.connectionState
    val lastActivityAt: StateFlow<Long?> = discordWebSocket.lastActivityAt
    val lastError: StateFlow<Throwable?> = discordWebSocket.lastError

    fun closeRPC() {
        discordWebSocket.close()
        runBlocking { ArtworkCache.clear() }
    }

    fun isRpcRunning(): Boolean {
        return discordWebSocket.isWebSocketConnected()
    }

    suspend fun connect() {
        discordWebSocket.connect()
    }

    suspend fun restartGateway() {
        discordWebSocket.restart()
    }

    suspend fun setActivity(
        name: String,
        state: String?,
        details: String?,
        largeImage: RpcImage?,
        smallImage: RpcImage?,
        largeText: String? = null,
        smallText: String? = null,
        buttons: List<Pair<String, String>>? = null,
        startTime: Long,
        endTime: Long,
        type: Type = Type.LISTENING,
        streamUrl: String? = null,
        applicationId: String? = null,
        status: String? = "online",
        since: Long? = null,
    ) {
        if (!isRpcRunning()) discordWebSocket.connect()

        // Try to resolve external URLs via Discord `external-assets` first (requires applicationId + token).
        val resolvedLargeImage = when {
            largeImage == null -> null
            largeImage is RpcImage.DiscordImage -> largeImage.resolveImage(kizzyRepository)
            largeImage is RpcImage.ExternalImage -> {
                var maybe: String? = null
                if (applicationId != null) {
                    // Retry a few times because Discord may take a short moment to create external assets
                    repeat(3) { attempt ->
                        if (maybe != null) return@repeat
                        maybe = runCatching {
                            fetchExternalAsset(httpClient, applicationId, token, largeImage.image, userAgent, superPropertiesBase64)
                        }.getOrNull()
                        if (maybe == null) delay((200L * (attempt + 1)))
                    }
                }
                maybe ?: largeImage.resolveImage(kizzyRepository)
            }
            else -> largeImage.resolveImage(kizzyRepository)
        }

        val resolvedSmallImage = when {
            smallImage == null -> null
            smallImage is RpcImage.DiscordImage -> smallImage.resolveImage(kizzyRepository)
            smallImage is RpcImage.ExternalImage -> {
                var maybe: String? = null
                if (applicationId != null) {
                    repeat(3) { attempt ->
                        if (maybe != null) return@repeat
                        maybe = runCatching {
                            fetchExternalAsset(httpClient, applicationId, token, smallImage.image, userAgent, superPropertiesBase64)
                        }.getOrNull()
                        if (maybe == null) delay((200L * (attempt + 1)))
                    }
                }
                maybe ?: smallImage.resolveImage(kizzyRepository)
            }
            else -> smallImage.resolveImage(kizzyRepository)
        }

        val presence = Presence(
            activities = listOf(
                Activity(
                    name = name,
                    state = state,
                    details = details,
                    type = type.value,
                    timestamps = Timestamps(
                        start = startTime,
                        end = endTime
                    ),
                    assets = Assets(
                        largeImage = resolvedLargeImage,
                        smallImage = resolvedSmallImage,
                        largeText = largeText,
                        smallText = smallText
                    ),
                    buttons = buttons?.map { it.first },
                    metadata = Metadata(buttonUrls = buttons?.map { it.second }),
                    applicationId = applicationId,
                    url = streamUrl
                )
            ),
            afk = true,
            since = since,
            status = status ?: "online"
        )
        discordWebSocket.sendActivity(presence)
    }

    enum class Type(val value: Int) {
        PLAYING(0),
        STREAMING(1),
        LISTENING(2),
        WATCHING(3),
        COMPETING(5)
    }

    companion object {
        suspend fun getUserInfo(
            token: String,
            userAgent: String = "Discord-Android/314013;RNA",
            superPropertiesBase64: String? = null
        ): Result<UserInfo> = runCatching {
            val client = HttpClient()
            val response = client.get("https://discord.com/api/v9/users/@me") {
                header("Authorization", token)
                header("User-Agent", userAgent)
                if (superPropertiesBase64 != null) {
                    header("X-Super-Properties", superPropertiesBase64)
                }
            }.bodyAsText()
            val json = JSONObject(response)
            val username = json.getString("username")
            val name = json.optString("global_name", username)
            val userId = json.getString("id")
            // Avatar is optional
            val avatarHash = if (json.has("avatar") && !json.isNull("avatar")) json.getString("avatar") else null
            val avatarUrl = if (avatarHash != null) {
                val format = if (avatarHash.startsWith("a_")) "gif" else "png"
                "https://cdn.discordapp.com/avatars/$userId/$avatarHash.$format"
            } else null
            client.close()

            UserInfo(username, name, avatarUrl)
        }
    }
}
