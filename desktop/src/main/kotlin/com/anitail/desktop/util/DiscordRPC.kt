package com.anitail.desktop.util

import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class DesktopDiscordRPC {
    private val client = DiscordIpcClient(APPLICATION_ID)

    fun isRpcRunning(): Boolean = client.isConnected()

    fun connect(): Boolean = client.connect()

    fun closeRPC() {
        client.close()
    }

    suspend fun updateSong(
        item: LibraryItem,
        artistThumbnailUrl: String?,
        albumName: String?,
        timeStart: Long,  // milliseconds
        timeEnd: Long     // milliseconds
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val presenceState = item.artist.takeIf { it.isNotBlank() } ?: "Unknown artist"

            // Discord expects Unix timestamps in SECONDS, not milliseconds
            val startSec = timeStart / 1000L
            val endSec = timeEnd / 1000L

            // Modern Discord clients (2022+) accept raw HTTPS URLs as image values in IPC,
            // same as how Android's Discord Social SDK passes thumbnailUrl directly.
            val largeImage = item.artworkUrl?.takeIf { it.isNotBlank() } ?: ASSET_LOGO
            val smallImage = artistThumbnailUrl?.takeIf { it.isNotBlank() }

            val activityJson = JSONObject().apply {
                put("type", 2) // Listening
                put("name", "AniTail Music")
                put("details", item.title.take(128))
                put("state", presenceState.take(128))
                put("timestamps", JSONObject().apply {
                    put("start", startSec)
                    if (endSec > startSec) put("end", endSec)
                })
                put("assets", JSONObject().apply {
                    put("large_image", largeImage)
                    put("large_text", ("Album: " + (albumName?.takeIf { it.isNotBlank() } ?: "Unknown")).take(128))
                    if (smallImage != null) {
                        put("small_image", smallImage)
                        put("small_text", presenceState.take(128))
                    }
                })
            }

            client.setActivity(activityJson)
        }
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
            val response = receive() ?: return false
            // READY event means success; anything else (ERROR) is failure
            val evt = response.optString("evt")
            evt == "READY" || (evt != "ERROR" && response.optString("cmd") != "ERROR")
        } catch (_: Exception) {
            false
        }
    }

    private fun send(op: Int, payload: String) {
        val out = streamOut ?: return
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(op)
            putInt(bytes.size)
        }.array()
        out.write(header)
        out.write(bytes)
        out.flush()
    }

    private fun receive(): JSONObject? {
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
        return runCatching {
            JSONObject(String(payloadBytes, Charsets.UTF_8))
        }.getOrNull()
    }

    fun setActivity(activityJson: JSONObject) {
        if (!connected && !connect()) return
        val payload = JSONObject().apply {
            put("cmd", "SET_ACTIVITY")
            put("args", JSONObject().apply {
                put("pid", ProcessHandle.current().pid().toInt())
                put("activity", activityJson)
            })
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        try {
            send(1, payload.toString())
            // Consume Discord's response (required to keep pipe state valid)
            val response = receive()
            // If Discord reports an error, disconnect so next call retries
            if (response == null || response.optString("evt") == "ERROR") {
                connected = false
            }
        } catch (_: Exception) {
            close()
        }
    }

    fun close() {
        runCatching { streamOut?.close() }
        runCatching { streamIn?.close() }
        runCatching { socketChannel?.close() }
        runCatching { windowsPipe?.close() }
        streamOut = null
        streamIn = null
        socketChannel = null
        windowsPipe = null
        connected = false
    }
}
