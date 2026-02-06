package com.anitail.desktop.player

import com.anitail.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Un servidor proxy local mínimo para evitar las restricciones de User-Agent de YouTube.
 * JavaFX MediaPlayer no permite configurar cabeceras HTTP, así que este proxy las añade.
 */
object PlaybackProxy {
    private var serverSocket: ServerSocket? = null
    private var port: Int = 0
    private val executor = Executors.newCachedThreadPool()
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    /**
     * Inicia el proxy en un puerto aleatorio y devuelve la URL base.
     */
    fun start(): String {
        synchronized(this) {
            if (serverSocket == null) {
                serverSocket = ServerSocket(0)
                port = serverSocket!!.localPort
                println("PlaybackProxy: Iniciado en puerto $port")
                thread(isDaemon = true, name = "PlaybackProxy") {
                    acceptConnections()
                }
            }
        }
        return "http://127.0.0.1:$port"
    }

    private fun acceptConnections() {
        while (true) {
            try {
                val socket = serverSocket?.accept() ?: break
                executor.execute { handleSocket(socket) }
            } catch (e: Exception) {
                if (serverSocket?.isClosed == true) break
                e.printStackTrace()
            }
        }
    }

    private fun handleSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val reader = input.bufferedReader()
            val requestLine = reader.readLine() ?: return
            // println("PlaybackProxy: REQUEST -> $requestLine")

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val path = parts[1]
            if (!path.startsWith("/stream")) {
                sendError(output, 404, "Not Found")
                return
            }

            var rangeHeader: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotBlank()) {
                if (line!!.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line!!.substring(6).trim()
                }
            }

            val query = path.substringAfter("?", "")
            val params = query.split("&").associate {
                val key = it.substringBefore("=")
                val value = it.substringAfter("=", "")
                key to value
            }

            val encodedUrl = params["url"]
            if (encodedUrl == null || encodedUrl.isBlank()) {
                sendError(output, 400, "Missing URL")
                return
            }

            val targetUrl = try {
                String(Base64.getUrlDecoder().decode(encodedUrl))
            } catch (e: Exception) {
                println("PlaybackProxy: Error decodificando URL Base64: ${e.message}")
                return
            }

            val userAgent = params["ua"]?.let {
                try {
                    String(Base64.getUrlDecoder().decode(it))
                } catch (e: Exception) {
                    null
                }
            } ?: YouTubeClient.USER_AGENT_WEB

            val referer = params["ref"]?.let {
                try {
                    String(Base64.getUrlDecoder().decode(it))
                } catch (e: Exception) {
                    null
                }
            } ?: if (params.containsKey("ref")) "" else "https://www.youtube.com/"

            println("PlaybackProxy: PROXYING -> $targetUrl")
            println("PlaybackProxy: HEADERS -> UA: $userAgent, Ref: $referer, Range: $rangeHeader")

            proxyRequest(targetUrl, rangeHeader, userAgent, referer, output)

        } catch (e: Exception) {
            println("PlaybackProxy: Error en handleSocket: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (ex: Exception) {
            }
        }
    }

    private fun proxyRequest(
        targetUrl: String,
        range: String?,
        userAgent: String,
        referer: String,
        output: OutputStream
    ) {
        try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("Referer", referer)

            if (range != null) {
                requestBuilder.addHeader("Range", range)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                println("PlaybackProxy: YT RESPONSE -> ${response.code} ${response.message}")

                val statusLine = "HTTP/1.1 ${response.code} ${response.message}\r\n"
                output.write(statusLine.toByteArray())

                response.headers.forEach { (name, value) ->
                    // Filtrar cabeceras que pueden causar conflictos o duplicados
                    if (!name.equals("Transfer-Encoding", ignoreCase = true) &&
                        !name.equals("Connection", ignoreCase = true) &&
                        !name.equals("Content-Encoding", ignoreCase = true)
                    ) {
                        output.write("$name: $value\r\n".toByteArray())
                    }
                }
                output.write("Connection: close\r\n\r\n".toByteArray())

                val body = response.body
                if (body != null) {
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalCopied = 0L
                    try {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalCopied += bytesRead
                        }
                        output.flush()
                        println("PlaybackProxy: Stream enviado con éxito. Total: $totalCopied bytes")
                    } catch (e: Exception) {
                        println("PlaybackProxy: Conexión cerrada por el cliente durante el streaming.")
                    }
                }
            }
        } catch (e: Exception) {
            println("PlaybackProxy: Error en proxyRequest: ${e.message}")
            sendError(output, 500, "Internal Server Error")
        }
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        try {
            output.write("HTTP/1.1 $code $message\r\n\r\n".toByteArray())
        } catch (e: Exception) {
        }
    }

    /**
     * Codifica una URL de YouTube para ser usada con el proxy, incluyendo headers opcionales.
     */
    fun createProxyUrl(url: String, userAgent: String? = null, referer: String? = null): String {
        val baseUrl = start()
        val encodedUrl = Base64.getUrlEncoder().encodeToString(url.toByteArray())
        val sb = StringBuilder("$baseUrl/stream?url=$encodedUrl")

        userAgent?.let {
            val encodedUA = Base64.getUrlEncoder().encodeToString(it.toByteArray())
            sb.append("&ua=$encodedUA")
        }
        referer?.let {
            val encodedRef = Base64.getUrlEncoder().encodeToString(it.toByteArray())
            sb.append("&ref=$encodedRef")
        }

        return sb.toString()
    }
}
