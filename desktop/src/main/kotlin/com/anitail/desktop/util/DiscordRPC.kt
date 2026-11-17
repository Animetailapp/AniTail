package com.anitail.desktop.util

import com.anitail.desktop.auth.DesktopDiscordService
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DesktopDiscordRPC(
    private val tokenProvider: () -> String = { "" },
    private val refreshTokenProvider: () -> String = { "" },
    private val onTokenRefreshed: (String, String) -> Unit = { _, _ -> },
) {
    private var gatewayClient: DiscordGatewayClient? = null
    private var activeToken: String = ""

    private fun currentToken(): String = tokenProvider().trim()

    fun isRpcRunning(): Boolean {
        val token = currentToken()
        return token.isNotEmpty() && gatewayClient?.isConnected() == true && activeToken == token
    }

    fun connect(): Boolean {
        val token = currentToken()
        if (token.isEmpty()) {
            closeRPC()
            return false
        }
        if (gatewayClient == null || activeToken != token) {
            gatewayClient?.close()
            gatewayClient = createGatewayClient(token)
            activeToken = token
        }
        return gatewayClient?.connect() == true
    }

    private fun createGatewayClient(token: String): DiscordGatewayClient =
        DiscordGatewayClient(
            applicationId = APPLICATION_ID,
            initialToken = token,
            refreshTokenProvider = refreshTokenProvider,
            onTokenRefreshed = { newAccess, newRefresh ->
                activeToken = newAccess
                onTokenRefreshed(newAccess, newRefresh)
            },
        )

    fun closeRPC() {
        gatewayClient?.close()
        gatewayClient = null
        activeToken = ""
    }

    suspend fun updateSong(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,  // milliseconds
        timeEnd: Long,    // milliseconds
    ) = withContext(Dispatchers.IO) {
        val _ = runCatching {
            val token = currentToken()
            if (token.isEmpty()) return@runCatching

            if (gatewayClient == null || activeToken != token) {
                gatewayClient?.close()
                gatewayClient = createGatewayClient(token)
                activeToken = token
            }
            val gw = gatewayClient ?: return@runCatching
            if (!gw.isConnected()) gw.connect()

            val prefs = DesktopPreferences.getInstance()
            val advancedMode = prefs.discordAdvancedMode.value
            val rawSongTitle = item.title.trim().ifEmpty { "Unknown" }
            val rawArtist = item.artist.trim().ifEmpty { "Unknown artist" }
            val rawAlbum = albumName?.takeIf { it.isNotBlank() } ?: "Unknown"
            val videoId = extractVideoId(item.playbackUrl, item.id).orEmpty()

            val songTitle = if (advancedMode) {
                renderTemplate(prefs.discordDetailsTemplate.value.ifBlank { "{song.name}" }, rawSongTitle, rawArtist, rawAlbum, videoId)
            } else {
                rawSongTitle
            }.let { if (it.length < 2) "$it " else it }.take(128)

            val presenceState = if (advancedMode) {
                renderTemplate(prefs.discordStateTemplate.value.ifBlank { "{artist.name}" }, rawSongTitle, rawArtist, rawAlbum, videoId)
            } else {
                rawArtist
            }.let { if (it.length < 2) "$it " else it }.take(128)

            val activityType = if (advancedMode) prefs.discordActivityType.value else 2
            val activityName = if (advancedMode) {
                prefs.discordActivityName.value.trim().ifEmpty { "AniTail Music" }
            } else {
                "AniTail Music"
            }.take(128)
            val userStatus = if (advancedMode) prefs.discordUserStatus.value.ifBlank { "online" } else "online"
            val albumText = ("Album: $rawAlbum").take(128)

            val largeImageAsset = item.artworkUrl?.takeIf { it.isNotBlank() }?.let { gw.resolveExternalAsset(it) }
            val smallImageAsset = artistThumbnailUrl?.takeIf { it.isNotBlank() }?.let { gw.resolveExternalAsset(it) }

            val buttonLabels = JSONArray()
            val buttonUrls = JSONArray()
            if (advancedMode) {
                if (prefs.discordButton1Enabled.value) {
                    val label = renderTemplate(prefs.discordButton1Label.value, rawSongTitle, rawArtist, rawAlbum, videoId).take(32)
                    val url = renderTemplate(prefs.discordButton1Url.value, rawSongTitle, rawArtist, rawAlbum, videoId)
                    if (label.isNotBlank() && url.startsWith("http")) {
                        buttonLabels.put(label)
                        buttonUrls.put(url)
                    }
                }
                if (prefs.discordButton2Enabled.value) {
                    val label = renderTemplate(prefs.discordButton2Label.value, rawSongTitle, rawArtist, rawAlbum, videoId).take(32)
                    val url = renderTemplate(prefs.discordButton2Url.value, rawSongTitle, rawArtist, rawAlbum, videoId)
                    if (label.isNotBlank() && url.startsWith("http")) {
                        buttonLabels.put(label)
                        buttonUrls.put(url)
                    }
                }
            } else {
                if (videoId.isNotBlank()) {
                    buttonLabels.put("Listen on YouTube Music")
                    buttonUrls.put("https://music.youtube.com/watch?v=$videoId")
                }
                buttonLabels.put("Join our discord")
                buttonUrls.put("https://discord.gg/H8x3yNbc67")
            }

            val activity = JSONObject().apply {
                put("name", activityName)
                put("type", activityType)
                put("application_id", APPLICATION_ID)
                put("details", songTitle)
                put("state", presenceState)
                put("status_display_type", 1)
                put("timestamps", JSONObject().apply {
                    put("start", timeStart)
                    if (timeEnd > timeStart) put("end", timeEnd)
                })
                put("assets", JSONObject().apply {
                    if (largeImageAsset != null) put("large_image", largeImageAsset)
                    put("large_text", albumText)
                    if (smallImageAsset != null) {
                        put("small_image", smallImageAsset)
                        put("small_text", presenceState)
                    }
                })
                if (buttonLabels.length() > 0) {
                    put("buttons", buttonLabels)
                    put("metadata", JSONObject().apply {
                        put("button_urls", buttonUrls)
                    })
                }
            }
            gw.setActivity(activity, userStatus)
        }
    }

    suspend fun clearActivity() = withContext(Dispatchers.IO) {
        val _ = runCatching { gatewayClient?.clearActivity() }
    }

    private fun renderTemplate(
        template: String,
        songName: String,
        artistName: String,
        albumName: String,
        songId: String,
    ): String = template
        .replace("{song.name}", songName)
        .replace("{artist.name}", artistName)
        .replace("{album.name}", albumName)
        .replace("{song.id}", songId)

    private fun extractVideoId(playbackUrl: String?, fallbackId: String): String? {
        if (!playbackUrl.isNullOrBlank()) {
            Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return fallbackId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
    }

    companion object {
        private const val APPLICATION_ID = DesktopDiscordService.DISCORD_APP_ID
    }
}

private class DiscordGatewayClient(
    private val applicationId: String,
    initialToken: String,
    private val refreshTokenProvider: () -> String,
    private val onTokenRefreshed: (String, String) -> Unit,
) {
    @Volatile private var token: String = initialToken
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val assetCache = ConcurrentHashMap<String, String>()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var sequence: Long? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var resumeGatewayUrl: String? = null
    @Volatile private var pendingActivity: JSONObject? = null
    @Volatile private var pendingStatus: String = "online"
    private var heartbeatJob: Job? = null

    fun isConnected(): Boolean = connected && webSocket != null

    fun connect(): Boolean {
        if (closed) return false
        if (isConnected()) return true

        val gatewayUrl = (resumeGatewayUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_GATEWAY_URL)
            .let { if (it.contains("?")) it else "$it/?v=10&encoding=json" }

        val request = Request.Builder()
            .url(gatewayUrl)
            .header("User-Agent", SuperProperties.userAgent)
            .build()

        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(webSocket, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connected = false
                    ready = false
                    heartbeatJob?.cancel()
                    if (closed) return
                    if (code == 4004) {
                        val refresh = refreshTokenProvider().trim()
                        if (refresh.isNotEmpty()) {
                            scope.launch {
                                val refreshed = DesktopDiscordService.refreshToken(refresh).getOrNull()
                                if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                                    token = refreshed.accessToken
                                    onTokenRefreshed(refreshed.accessToken, refreshed.refreshToken)
                                    sessionId = null
                                    sequence = null
                                    connect()
                                }
                            }
                        }
                    } else {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("[DiscordGateway] WebSocket failure: ${t.message}")
                    connected = false
                    ready = false
                    heartbeatJob?.cancel()
                    if (!closed) {
                        scheduleReconnect()
                    }
                }
            },
        )
        return true
    }

    fun setActivity(activity: JSONObject, status: String = "online") {
        pendingActivity = activity
        pendingStatus = status
        if (!isConnected()) {
            connect()
            return
        }
        if (ready) {
            sendPresence(listOf(activity), status)
        }
    }

    fun clearActivity() {
        pendingActivity = null
        if (isConnected() && ready) {
            sendPresence(emptyList(), pendingStatus)
        }
    }

    fun resolveExternalAsset(imageUrl: String): String? {
        if (imageUrl.startsWith("mp:")) return imageUrl
        assetCache[imageUrl]?.let { return it }

        val body = JSONObject().apply {
            put("urls", JSONArray().put(imageUrl))
        }.toString().toRequestBody("application/json".toMediaType())

        val bearerToken = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val request = Request.Builder()
            .url("https://discord.com/api/v9/applications/$applicationId/external-assets")
            .post(body)
            .header("Authorization", bearerToken)
            .header("User-Agent", SuperProperties.userAgent)
            .header("X-Super-Properties", SuperProperties.superPropertiesBase64)
            .build()

        val resolved = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body.string()
                val arr = JSONArray(raw)
                val path = arr.optJSONObject(0)?.optString("external_asset_path").orEmpty()
                if (path.isNotBlank()) "mp:$path" else null
            }
        }.getOrNull()

        if (resolved != null) {
            if (assetCache.size >= 100) assetCache.clear()
            assetCache[imageUrl] = resolved
        }
        return resolved
    }

    fun close() {
        closed = true
        connected = false
        ready = false
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        scope.cancel()
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (!payload.isNull("s")) {
            sequence = payload.optLong("s")
        }
        when (payload.optInt("op", -1)) {
            10 -> {
                val intervalMs = payload.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                startHeartbeat(ws, intervalMs)
                if (!sessionId.isNullOrBlank() && sequence != null) {
                    sendResume(ws)
                } else {
                    sendIdentify(ws)
                }
            }
            1 -> sendHeartbeat(ws)
            0 -> {
                when (payload.optString("t")) {
                    "READY" -> {
                        val data = payload.optJSONObject("d")
                        sessionId = data?.optString("session_id")?.takeIf { it.isNotBlank() }
                        resumeGatewayUrl = data?.optString("resume_gateway_url")?.takeIf { it.isNotBlank() }
                        ready = true
                        pendingActivity?.let { sendPresence(listOf(it), pendingStatus) }
                    }
                    "RESUMED" -> {
                        ready = true
                        pendingActivity?.let { sendPresence(listOf(it), pendingStatus) }
                    }
                }
            }
            7 -> ws.close(4000, "Reconnect requested")
            9 -> {
                if (!payload.optBoolean("d", false)) {
                    sessionId = null
                    sequence = null
                }
                scope.launch {
                    delay(1500)
                    if (!closed && isConnected()) sendIdentify(ws)
                }
            }
        }
    }

    private fun startHeartbeat(ws: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay((intervalMs * 0.5).toLong().coerceAtLeast(1000L))
            while (isActive && !closed) {
                sendHeartbeat(ws)
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat(ws: WebSocket) {
        val msg = JSONObject().apply {
            put("op", 1)
            put("d", sequence ?: JSONObject.NULL)
        }
        ws.send(msg.toString())
    }

    private fun sendIdentify(ws: WebSocket) {
        val bearerToken = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val msg = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", bearerToken)
                put("intents", 0)
                put("properties", JSONObject().apply {
                    put("os", SuperProperties.osName)
                    put("browser", "AniTail")
                    put("device", SuperProperties.deviceName)
                })
                put("compress", false)
            })
        }
        ws.send(msg.toString())
    }

    private fun sendResume(ws: WebSocket) {
        val msg = JSONObject().apply {
            put("op", 6)
            put("d", JSONObject().apply {
                put("token", token)
                put("session_id", sessionId)
                put("seq", sequence ?: 0L)
            })
        }
        ws.send(msg.toString())
    }

    private fun sendPresence(activities: List<JSONObject>, status: String = "online") {
        val ws = webSocket ?: return
        val activityArray = JSONArray().apply {
            activities.forEach { put(it) }
        }
        val msg = JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("since", System.currentTimeMillis())
                put("activities", activityArray)
                put("status", status)
                put("afk", false)
            })
        }
        ws.send(msg.toString())
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(3000)
            if (!closed && !isConnected()) {
                connect()
            }
        }
    }

    companion object {
        private const val DEFAULT_GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
    }
}
