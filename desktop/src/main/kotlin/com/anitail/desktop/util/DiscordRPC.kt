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
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DesktopDiscordRPC(
    private val tokenProvider: () -> String = { "" },
) {
    private val ipcClient = DiscordIpcClient(APPLICATION_ID)
    private var gatewayClient: DiscordGatewayClient? = null
    private var activeToken: String = ""

    private fun currentToken(): String = tokenProvider().trim()

    fun isRpcRunning(): Boolean {
        val token = currentToken()
        return if (token.isNotEmpty()) {
            gatewayClient?.isConnected() == true && activeToken == token
        } else {
            ipcClient.isConnected()
        }
    }

    fun connect(): Boolean {
        val token = currentToken()
        if (token.isNotEmpty()) {
            ipcClient.close()
            if (gatewayClient == null || activeToken != token) {
                gatewayClient?.close()
                gatewayClient = DiscordGatewayClient(APPLICATION_ID, token)
                activeToken = token
            }
            return gatewayClient?.connect() == true
        }
        gatewayClient?.close()
        gatewayClient = null
        activeToken = ""
        return ipcClient.connect()
    }

    fun closeRPC() {
        gatewayClient?.close()
        gatewayClient = null
        activeToken = ""
        ipcClient.close()
    }

    suspend fun updateSong(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,  // milliseconds
        timeEnd: Long     // milliseconds
    ) = withContext(Dispatchers.IO) {
        val _ = runCatching {
            val token = currentToken()
            val songTitle = item.title.trim().ifEmpty { "Unknown" }.let { if (it.length < 2) "$it " else it }.take(128)
            val presenceState = item.artist.trim().ifEmpty { "Unknown artist" }.let { if (it.length < 2) "$it " else it }.take(128)
            val albumText = ("Album: " + (albumName?.takeIf { it.isNotBlank() } ?: "Unknown")).take(128)
            val videoId = extractVideoId(item.playbackUrl, item.id)

            if (token.isNotEmpty()) {
                if (gatewayClient == null || activeToken != token) {
                    gatewayClient?.close()
                    gatewayClient = DiscordGatewayClient(APPLICATION_ID, token)
                    activeToken = token
                }
                val gw = gatewayClient ?: return@runCatching
                if (!gw.isConnected()) gw.connect()

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
                return@runCatching
            }

            // Fallback: Local Discord IPC (no token configured)
            val startSec = timeStart / 1000L
            val endSec = timeEnd / 1000L
            val largeImage = item.artworkUrl?.takeIf { it.isNotBlank() } ?: ASSET_LOGO
            val smallImage = artistThumbnailUrl?.takeIf { it.isNotBlank() }

            val buttons = JSONArray().apply {
                if (!videoId.isNullOrBlank()) {
                    put(JSONObject().apply {
                        put("label", "Listen on YouTube Music")
                        put("url", "https://music.youtube.com/watch?v=$videoId")
                    })
                }
                put(JSONObject().apply {
                    put("label", "Join our discord")
                    put("url", "https://discord.gg/H8x3yNbc67")
                })
            }

            val activityJson = JSONObject().apply {
                put("type", 2) // Listening
                put("details", songTitle)
                put("state", presenceState)
                put("timestamps", JSONObject().apply {
                    put("start", startSec)
                    if (endSec > startSec) put("end", endSec)
                })
                put("assets", JSONObject().apply {
                    put("large_image", largeImage)
                    put("large_text", albumText)
                    if (smallImage != null) {
                        put("small_image", smallImage)
                        put("small_text", presenceState)
                    }
                })
                put("buttons", buttons)
            }

            ipcClient.setActivity(activityJson)
        }
    }

    suspend fun clearActivity() = withContext(Dispatchers.IO) {
        val _ = runCatching {
            if (currentToken().isNotEmpty()) {
                gatewayClient?.clearActivity()
            } else {
                ipcClient.clearActivity()
            }
        }
    }

    private fun extractVideoId(playbackUrl: String?, fallbackId: String): String? {
        if (!playbackUrl.isNullOrBlank()) {
            Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(playbackUrl)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return fallbackId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
        // Name of the image asset registered in Discord Developer Portal
        // → https://discord.com/developers/applications/1271273225120125040/rich-presence/assets
        // Upload an image there named "logo" and it will appear here.
        private const val ASSET_LOGO = "logo"
    }
}

class DiscordIpcClient(private val clientId: String) {
    private var streamOut: OutputStream? = null
    private var streamIn: InputStream? = null
    private var socketChannel: SocketChannel? = null
    private var windowsPipe: RandomAccessFile? = null
    private var connected = false

    fun isConnected(): Boolean = connected

    fun connect(): Boolean {
        if (connected) return true

        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            connectWindows()
        } else {
            connectUnix()
        }
    }

    private fun connectWindows(): Boolean {
        for (i in 0..9) {
            try {
                val pipeFile = "\\\\.\\pipe\\discord-ipc-$i"
                val raf = RandomAccessFile(pipeFile, "rw")
                windowsPipe = raf
                streamOut = object : OutputStream() {
                    override fun write(b: Int) = raf.write(b)
                    override fun write(b: ByteArray) = raf.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
                    override fun close() = raf.close()
                }
                streamIn = object : InputStream() {
                    override fun read(): Int = raf.read()
                    override fun read(b: ByteArray): Int = raf.read(b)
                    override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
                    override fun close() = raf.close()
                }
                if (doHandshake()) {
                    connected = true
                    return true
                }
                close()
            } catch (_: Exception) {
                // Try next pipe index
            }
        }
        return false
    }

    private fun connectUnix(): Boolean {
        val dirs = listOfNotNull(
            System.getenv("XDG_RUNTIME_DIR"),
            System.getenv("TMPDIR"),
            System.getenv("TMP"),
            System.getenv("TEMP"),
            "/tmp",
        )
        for (dir in dirs) {
            for (i in 0..9) {
                try {
                    val socketPath = File(dir, "discord-ipc-$i")
                    if (!socketPath.exists()) continue
                    val address = UnixDomainSocketAddress.of(socketPath.absolutePath)
                    val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                    channel.connect(address)
                    socketChannel = channel
                    streamOut = channel.socket().getOutputStream()
                    streamIn = channel.socket().getInputStream()
                    if (doHandshake()) {
                        connected = true
                        return true
                    }
                    close()
                } catch (_: Exception) {
                    // Try next
                }
            }
        }
        return false
    }

    private fun doHandshake(): Boolean {
        return try {
            val payload = JSONObject().apply {
                put("v", 1)
                put("client_id", clientId)
            }
            send(0, payload.toString())
            val (op, response) = receiveFrame() ?: return false
            val evt = response.optString("evt")
            val ok = op == 1 && evt == "READY"
            if (!ok) {
                println("[DiscordRPC] Handshake rejected: op=$op response=$response")
            }
            ok
        } catch (e: Exception) {
            println("[DiscordRPC] Handshake failed: ${e.message}")
            false
        }
    }

    private fun send(op: Int, payload: String) {
        val out = streamOut ?: return
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val packet = ByteBuffer.allocate(8 + bytes.size).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(op)
            putInt(bytes.size)
            put(bytes)
        }.array()
        out.write(packet)
        out.flush()
    }

    private fun receiveFrame(): Pair<Int, JSONObject>? {
        val input = streamIn ?: return null
        val header = ByteArray(8)
        var read = 0
        while (read < 8) {
            val r = input.read(header, read, 8 - read)
            if (r == -1) return null
            read += r
        }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = buffer.int
        val length = buffer.int

        if (length <= 0 || length > 65536) return null  // sanity check

        val payloadBytes = ByteArray(length)
        var payloadRead = 0
        while (payloadRead < length) {
            val r = input.read(payloadBytes, payloadRead, length - payloadRead)
            if (r == -1) return null
            payloadRead += r
        }
        val json = runCatching {
            JSONObject(String(payloadBytes, Charsets.UTF_8))
        }.getOrNull() ?: return null
        return op to json
    }

    fun setActivity(activityJson: JSONObject) {
        if (!connected && !connect()) {
            println("[DiscordRPC] Could not connect to Discord IPC pipe/socket")
            return
        }
        if (!sendSetActivity(activityJson)) {
            if (activityJson.has("type")) {
                val fallback = JSONObject(activityJson.toString()).apply { remove("type") }
                if (!connected && !connect()) return
                sendSetActivity(fallback)
            }
        }
    }

    fun clearActivity() {
        if (!connected) return
        val payload = JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("args", JSONObject().apply {
                put("pid", ProcessHandle.current().pid().toInt())
                put("activity", JSONObject.NULL)
            })
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        try {
            send(1, payload.toString())
            val frame = receiveFrame()
            if (frame == null || frame.first == 2) {
                close()
            }
        } catch (_: Exception) {
            close()
        }
    }

    private fun sendSetActivity(activityJson: JSONObject): Boolean {
        val payload = JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("args", JSONObject().apply {
                put("pid", ProcessHandle.current().pid().toInt())
                put("activity", activityJson)
            })
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        return try {
            send(1, payload.toString())
            val frame = receiveFrame()
            if (frame == null) {
                println("[DiscordRPC] No response from Discord IPC; closing connection")
                close()
                false
            } else if (frame.first == 2 || frame.second.optString("evt") == "ERROR") {
                println("[DiscordRPC] SET_ACTIVITY rejected: op=${frame.first} data=${frame.second}")
                if (frame.first == 2) close()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            println("[DiscordRPC] SET_ACTIVITY failed: ${e.message}")
            close()
            false
        }
    }

    fun close() {
        val _ = runCatching { streamOut?.close() }
        val _ = runCatching { streamIn?.close() }
        val _ = runCatching { socketChannel?.close() }
        val _ = runCatching { windowsPipe?.close() }
        streamOut = null
        streamIn = null
        socketChannel = null
        windowsPipe = null
        connected = false
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

        val request = Request.Builder()
            .url("https://discord.com/api/v9/applications/$applicationId/external-assets")
            .post(body)
            .header("Authorization", token)
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
            10 -> { // HELLO
                val intervalMs = payload.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                startHeartbeat(ws, intervalMs)
                if (!sessionId.isNullOrBlank() && sequence != null) {
                    sendResume(ws)
                } else {
                    sendIdentify(ws)
                }
            }
            1 -> { // HEARTBEAT request
                sendHeartbeat(ws)
            }
            0 -> { // DISPATCH
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
            7 -> { // RECONNECT
                ws.close(4000, "Reconnect requested")
            }
            9 -> { // INVALID_SESSION
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
        val msg = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", token)
                put("capabilities", 65)
                put("properties", SuperProperties.superProperties)
                put("presence", JSONObject().apply {
                    put("status", "online")
                    put("since", 0)
                    put("activities", JSONArray())
                    put("afk", false)
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
