package com.anitail.desktop.player

import com.anitail.desktop.YouTube
import com.anitail.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.URI
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

            val cookie = decodeBase64Param(params["ck"])?.takeIf { it.isNotBlank() }

            println("PlaybackProxy: PROXYING -> $targetUrl")
            val resolvedRange = resolveRangeHeader(rangeHeader, targetUrl)
            val sendReferer = shouldSendReferer(referer)
            val sendCookie = shouldSendCookie(cookie, targetUrl, referer)
            println(
                "PlaybackProxy: HEADERS -> UA: $userAgent, Ref: ${if (sendReferer) referer else "<none>"}, Range: $resolvedRange, Cookie: $sendCookie"
            )

            proxyRequest(
                targetUrl = targetUrl,
                range = resolvedRange,
                userAgent = userAgent,
                referer = if (sendReferer) referer else null,
                cookie = if (sendCookie) cookie else null,
                output = output
            )

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
        referer: String?,
        cookie: String?,
        output: OutputStream
    ) {
        try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
            if (!userAgent.isNullOrEmpty()) {
                requestBuilder.header("User-Agent", userAgent)
                
                // Inject Client Hints if simulating Chrome (Music/Web client)
                if (userAgent.contains("Chrome")) {
                    requestBuilder.header("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
                    requestBuilder.header("sec-ch-ua-mobile", "?1")
                    requestBuilder.header("sec-ch-ua-platform", "\"Android\"")
                    requestBuilder.header("sec-ch-ua-model", "\"Nexus 5\"")
                    requestBuilder.header("sec-ch-ua-arch", "\"\"")
                    requestBuilder.header("sec-ch-ua-bitness", "\"64\"")
                    requestBuilder.header("sec-ch-ua-full-version", "\"144.0.7559.133\"")
                    requestBuilder.header("sec-fetch-dest", "empty")
                    requestBuilder.header("sec-fetch-mode", "cors")
                    requestBuilder.header("sec-fetch-site", "same-origin")
                }
            }

            if (!referer.isNullOrBlank()) {
                requestBuilder.addHeader("Referer", referer)
            }

            if (!cookie.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", cookie)
            }

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

        val cookie = YouTube.cookie?.takeIf { it.isNotBlank() }
        if (cookie != null && shouldAttachCookie(url)) {
            val encodedCookie = Base64.getUrlEncoder().encodeToString(cookie.toByteArray())
            sb.append("&ck=$encodedCookie")
        }

        return sb.toString()
    }

    private fun decodeBase64Param(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { String(Base64.getUrlDecoder().decode(value)) }.getOrNull()
    }

    internal fun resolveRangeHeader(rangeHeader: String?, targetUrl: String): String? {
        if (!rangeHeader.isNullOrBlank()) return rangeHeader
        val rqh = extractQueryParam(targetUrl, "rqh")
        return if (rqh == "1") "bytes=0-" else null
    }

    internal fun shouldSendReferer(referer: String): Boolean = referer.isNotBlank()

    internal fun shouldSendCookie(cookie: String?, targetUrl: String, referer: String): Boolean {
        if (cookie.isNullOrBlank()) return false
        // Allow cookies for googlevideo.com even without referer (mobile clients like IOS)
        return shouldAttachCookie(targetUrl)
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return query.split("&")
            .map { entry -> entry.split("=", limit = 2) }
            .firstOrNull { it.firstOrNull() == key }
            ?.getOrNull(1)
    }

    private fun shouldAttachCookie(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host.endsWith("googlevideo.com") || host.endsWith("youtube.com")
    }
}
