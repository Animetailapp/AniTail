package com.anitail.desktop.player

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PipedResolver {
    private val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://piped-api.fernet.moe",
        "https://api.piped.privacydev.net",
        "https://piped-api.lunar.icu",
        "https://pipedapi.rivo.pw",
        "https://pipedapi.official-server.xyz",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.duckduckgo.com"
    )

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }
    }

    suspend fun resolveAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        for (instance in instances) {
            try {
                println("PipedResolver: Probando instancia $instance para videoId $videoId")
                val response = client.get("$instance/streams/$videoId") {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }
                
                val responseStatus = response.status.value
                val responseText = response.bodyAsText()

                if (responseStatus !in 200..299) {
                    println("PipedResolver: Instancia $instance devolvió status $responseStatus")
                    continue
                }

                val json = JSONObject(responseText)
                val audioStreams = json.optJSONArray("audioStreams")
                
                if (audioStreams != null && audioStreams.length() > 0) {
                    var bestUrl: String? = null
                    var maxBitrate: Int = -1

                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.getJSONObject(i)
                        val bitrate = stream.optInt("bitrate", 0)
                        
                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate
                            bestUrl = stream.optString("url", null)
                        }
                    }

                    if (bestUrl != null) {
                        println("PipedResolver: URL encontrada en $instance (Bitrate: $maxBitrate)")
                        return@withContext bestUrl
                    }
                }
            } catch (e: Exception) {
                println("PipedResolver: Error en instancia $instance: ${e.message}")
            }
        }
        println("PipedResolver: No se pudo resolver URL en ninguna instancia de Piped.")
        return@withContext null
    }
}
