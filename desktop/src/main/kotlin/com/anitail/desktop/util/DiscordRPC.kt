package com.anitail.desktop.util

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
            gatewayClient = DiscordGatewayClient(APPLICATION_ID, token)
            activeToken = token
        }
        return gatewayClient?.connect() == true
    }

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
                gatewayClient = DiscordGatewayClient(APPLICATION_ID, token)
                activeToken = token
            }
            val gw = gatewayClient ?: return@runCatching
            if (!gw.isConnected()) gw.connect()

            val songTitle = item.title.trim().ifEmpty { "Unknown" }.let { if (it.length < 2) "$it " else it }.take(128)
            val presenceState = item.artist.trim().ifEmpty { "Unknown artist" }.let { if (it.length < 2) "$it " else it }.take(128)
            val albumText = ("Album: " + (albumName?.takeIf { it.isNotBlank() } ?: "Unknown")).take(128)
            val videoId = extractVideoId(item.playbackUrl, item.id)

            val largeImageAsset = item.artworkUrl?.takeIf { it.isNotBlank() }?.let { gw.resolveExternalAsset(it) }
            val smallImageAsset = artistThumbnailUrl?.takeIf { it.isNotBlank() }?.let { gw.resolveExternalAsset(it) }

            val buttonLabels = JSONArray()
            val buttonUrls = JSONArray()
            if (!videoId.isNullOrBlank()) {
                buttonLabels.put("Listen on YouTube Music")
                buttonUrls.put("https://music.youtube.com/watch?v=$videoId")
            }
            buttonLabels.put("Join our discord")
            buttonUrls.put("https://discord.gg/H8x3yNbc67")

            val activity = JSONObject().apply {
                put("name", "AniTail Music")
                put("type", 2)
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
                put("buttons", buttonLabels)
                put("metadata", JSONObject().apply {
                    put("button_urls", buttonUrls)
                })
            }
            gw.setActivity(activity)
        }
    }

    suspend fun clearActivity() = withContext(Dispatchers.IO) {
        val _ = runCatching { gatewayClient?.clearActivity() }
    }

    private fun extractVideoId(playbackUrl: String?, fallbackId: String): String? {
        if (!playbackUrl.isNullOrBlank()) {
            Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return fallbackId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
    }

    companion object {
        private const val APPLICATION_ID = "1447278780795064401"
    }
}

private class DiscordGatewayClient(
    private val applicationId: String,
    private val token: String,
) {
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
                    if (!closed && code != 4004) {
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

    fun setActivity(activity: JSONObject) {
        pendingActivity = activity
        if (!isConnected()) {
            connect()
            return
        }
        if (ready) {
            sendPresence(listOf(activity))
        }
    }

    fun clearActivity() {
        pendingActivity = null
        if (isConnected() && ready) {
            sendPresence(emptyList())
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
                        pendingActivity?.let { sendPresence(listOf(it)) }
                    }
                    "RESUMED" -> {
                        ready = true
                        pendingActivity?.let { sendPresence(listOf(it)) }
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

    private fun sendPresence(activities: List<JSONObject>) {
        val ws = webSocket ?: return
        val activityArray = JSONArray().apply {
            activities.forEach { put(it) }
        }
        val msg = JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("since", System.currentTimeMillis())
                put("activities", activityArray)
                put("status", "online")
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
