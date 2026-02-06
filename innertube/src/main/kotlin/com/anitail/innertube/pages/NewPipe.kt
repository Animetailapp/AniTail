package com.anitail.innertube.pages

import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException

private class NewPipeDownloaderImpl(val innerTube: com.anitail.innertube.InnerTube) : Downloader() {

    private val client = innerTube.getHttpClient()

    var currentUserAgent: String = YouTubeClient.USER_AGENT_WEB

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .header("User-Agent", currentUserAgent)

        // Inject cookies and auth from InnerTube if available
        innerTube.cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        // Deofuscation player usually needs a referer
        if (!requestBuilder.run { build().header("Referer") }.isNullOrEmpty().let { it }) {
            // Already has referer
        } else {
            if (url.contains("youtube.com/s/player")) {
                requestBuilder.header("Referer", "https://www.youtube.com/")
            }
        }

        // Execute via OkHttp (using the engine's real client)
        val realClient = (client.engine as? io.ktor.client.engine.okhttp.OkHttpEngine)?.getBackend()
            ?: OkHttpClient()

        val response = realClient.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            val responseUrl = response.request.url.toString()
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", responseUrl)
        }

        var responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()

        // Polyfill for player JS to prevent getElementsByTagName errors in Rhino
        if (latestUrl.contains("youtube.com/s/player") && responseBodyToReturn != null) {
            val polyfill = """
                if (typeof document === 'undefined') {
                    var document = {
                        getElementsByTagName: function() { return []; },
                        getElementById: function() { return null; },
                        createElement: function() { return {}; }
                    };
                }
                if (typeof window === 'undefined') {
                    var window = { document: document };
                }
                // Mock for the specific object that might be missing getElementsByTagName
                Object.prototype.getElementsByTagName = Object.prototype.getElementsByTagName || function() { return []; };
            """.trimIndent()
            responseBodyToReturn = polyfill + "\n" + responseBodyToReturn
        }
        
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

private fun io.ktor.client.engine.okhttp.OkHttpEngine.getBackend(): OkHttpClient {
    // This is a hack to get the underlying OkHttpClient from Ktor
    val field = this::class.java.getDeclaredField("client")
    field.isAccessible = true
    return field.get(this) as OkHttpClient
}

object NewPipeUtils {
    private lateinit var downloader: NewPipeDownloaderImpl

    fun init(innerTube: com.anitail.innertube.InnerTube) {
        if (!::downloader.isInitialized) {
            downloader = NewPipeDownloaderImpl(innerTube)
            NewPipe.init(downloader)
        }
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        userAgent: String? = null
    ): Result<String> =
        runCatching {
            // Actualizar el User-Agent del downloader si se proporciona uno específico
            if (userAgent != null) {
                downloader.currentUserAgent = userAgent
            }

            println("NewPipeUtils: Datos base del formato -> URL: ${format.url != null}, Cipher: ${format.signatureCipher != null}")
            if (format.signatureCipher != null) {
                println("NewPipeUtils: signatureCipher raw -> ${format.signatureCipher}")
            }
            
            val url = format.url ?: format.signatureCipher?.let { signatureCipher ->
                println("NewPipeUtils: Deofuscando signatureCipher...")
                val params = parseQueryString(signatureCipher)
                val obfuscatedSignature = params["s"]
                    ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"]
                    ?: throw ParsingException("Could not parse cipher signature parameter")
                val urlString = params["url"]
                    ?: throw ParsingException("Could not parse cipher url")

                val urlBuilder = URLBuilder(urlString)

                // Copiar TODOS los parámetros adicionales del signatureCipher al URL final
                // (Especialmente importante para el parámetro 'n' si viene ahí)
                params.forEach { key, values ->
                    if (key != "s" && key != "sp" && key != "url") {
                        values.forEach { value ->
                            urlBuilder.parameters.append(key, value)
                        }
                    }
                }

                urlBuilder.parameters[signatureParam] =
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                        videoId,
                        obfuscatedSignature
                    )
                urlBuilder.buildString()
            } ?: throw ParsingException("Could not find format url")

            println("NewPipeUtils: Deofuscando parámetro 'n' (throttling)...")
            val originalUrl = url
            val finalUrl = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url
            )
            if (originalUrl == finalUrl) {
                println("NewPipeUtils: ADVERTENCIA: La deofuscación de 'n' no cambió el URL. Probablemente falló silenciosamente.")
            } else {
                println("NewPipeUtils: Deofuscación de 'n' completada con éxito.")
            }
            return@runCatching finalUrl
        }.onFailure {
            println("NewPipeUtils: ERROR en getStreamUrl: ${it.message}")
            it.printStackTrace()
        }
}
