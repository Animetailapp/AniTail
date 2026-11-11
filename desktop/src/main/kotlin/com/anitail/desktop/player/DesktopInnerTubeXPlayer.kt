package com.anitail.desktop.player

import com.anitail.desktop.security.DesktopPaths
import com.anitail.desktop.storage.AudioQuality
import com.anitail.innertube.YouTube as InnertubeYouTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** InnerTubeX-based stream extraction engine for Desktop. */
object DesktopInnerTubeXPlayer {
    private const val TAG = "DesktopInnerTubeXPlayer"
    private const val STREAM_CLIENT_FAILURE_TTL_MS = 5 * 60 * 1000L

    @Volatile
    private var currentBundle: ExtractionBundle? = null
    private val bundleMutex = Mutex()
    private val streamClientFailures = ConcurrentHashMap<String, FailedStreamClients>()

    suspend fun prewarm() {
        runCatching {
            bundle().extractor.prewarm()
        }.onFailure {
            System.err.println("$TAG: prewarm failed: ${it.message}")
        }
    }

    suspend fun extractStream(
        videoId: String,
        audioQuality: AudioQuality,
        allowBoundedRange: Boolean = true,
    ): Result<ExtractedStream> =
        try {
            val hints = ContentHints().withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = allowBoundedRange,
            )
            val excludedClients = failedStreamClients(videoId)
            val resolvedQuality = audioQuality.toInnerTubeX()
            val stream =
                requireNotNull(
                    resolveWithClientFallback(excludedClients) { clients, useLowQuality ->
                        bundle().extractor.extract(
                            videoId = videoId,
                            hints = hints,
                            excludedClients = clients,
                            audioQuality = if (useLowQuality) InnerTubeXAudioQuality.LOW else resolvedQuality,
                            clientPlaybackNonce = generateClientPlaybackNonce(),
                        )
                    },
                ) { "InnerTubeX returned no playable stream" }
            check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }

            Result.success(stream)
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            val cause = error.cause
            Result.failure(if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) cause else error)
        } catch (error: Exception) {
            Result.failure(error)
        }

    fun markStreamClientFailed(
        videoId: String,
        clientName: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        streamClientFailures.compute(videoId) { _, failures ->
            FailedStreamClients(failures?.clientNames.orEmpty() + clientName, nowMs)
        }
    }

    fun clearStreamClientFailures() {
        streamClientFailures.clear()
    }

    private fun failedStreamClients(
        videoId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Set<String> {
        val failures = streamClientFailures[videoId] ?: return emptySet()
        if ((nowMs - failures.failedAtMs) !in 0 until STREAM_CLIENT_FAILURE_TTL_MS) {
            streamClientFailures.remove(videoId, failures)
            return emptySet()
        }
        return failures.clientNames
    }

    private suspend fun <T : Any> resolveWithClientFallback(
        excludedClients: Set<String>,
        extract: suspend (Set<String>, Boolean) -> T?,
    ): T? {
        if (excludedClients.isEmpty()) return extract(emptySet(), false)

        val alternative =
            try {
                extract(excludedClients, false)
            } catch (error: StreamResolveException) {
                if (error.reason != StreamResolveException.Reason.NO_PLAYABLE_STREAM) throw error
                null
            }
        if (alternative != null) return alternative

        println("$TAG: Alternate stream clients exhausted; retrying all clients at low quality")
        return extract(emptySet(), true)
    }

    suspend fun refreshAfterStreamRejection(): Boolean = bundle().cipherService.refreshAfterStreamRejection()

    private suspend fun bundle(): ExtractionBundle {
        val transport = InnertubeYouTube.extractionTransport()
        currentBundle?.takeIf { it.transportGeneration == transport.generation }?.let { return it }
        return bundleMutex.withLock {
            val latestTransport = InnertubeYouTube.extractionTransport()
            currentBundle?.takeIf { it.transportGeneration == latestTransport.generation }?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(
                    com.metrolist.innertubex.InnerTubeLogEvent(
                        level = InnerTubeLogLevel.WARN,
                        tag = TAG,
                        message = "old cipher disposal failed",
                        details = mapOf("exceptionType" to (error::class.simpleName ?: "unknown")),
                    ),
                )
            }

            val remoteStore = RemotePlayerConfigStore(latestTransport.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latestTransport.httpClient, remoteStore, logger)
            val extractor =
                InnerTubeExtractor(
                    configParser =
                        YtConfigParserImpl(
                            latestTransport.httpClient,
                            latestTransport.innerTube,
                            remoteStore,
                            logger,
                        ).withEmbeddedConfigFallback(),
                    cipherService = cipherService,
                    innerTube = latestTransport.innerTube,
                    tokenProvider = null,
                    logger = logger,
                )
            ExtractionBundle(latestTransport.generation, cipherService, extractor).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        DesktopPlayerConfigRepository()
    }

    private val logger =
        InnerTubeLogger { event ->
            val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val message = "[$TAG][${event.tag}] ${event.message}" + details.takeUnless { event.details.isEmpty() }.orEmpty()
            when (event.level) {
                InnerTubeLogLevel.ERROR -> System.err.println(message)
                InnerTubeLogLevel.WARN -> System.err.println(message)
                else -> println(message)
            }
        }

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private data class FailedStreamClients(
        val clientNames: Set<String>,
        val failedAtMs: Long,
    )

    private class DesktopPlayerConfigRepository : PlayerConfigRepository {
        private val configFile: File = DesktopPaths.appDataDir().resolve("innertubex_player_config.json").toFile()
        private val metaFile: File = DesktopPaths.appDataDir().resolve("innertubex_player_config_meta.json").toFile()

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL

        override var cachedJson: String
            get() {
                if (configFile.exists() && configFile.length() > 0) {
                    val content = try {
                        configFile.readText()
                    } catch (_: Exception) {
                        null
                    }
                    if (!content.isNullOrBlank()) return content
                }
                return try {
                    DesktopPlayerConfigRepository::class.java.classLoader
                        ?.getResourceAsStream("player_configs.json")
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }
            set(value) {
                try {
                    configFile.parentFile?.mkdirs()
                    configFile.writeText(value)
                } catch (_: Exception) {
                    // Ignored
                }
            }

        private fun readMeta(): JSONObject {
            if (!metaFile.exists()) return JSONObject()
            return try {
                JSONObject(metaFile.readText())
            } catch (_: Exception) {
                JSONObject()
            }
        }

        private fun writeMeta(mutate: (JSONObject) -> Unit) {
            try {
                val json = readMeta()
                mutate(json)
                metaFile.parentFile?.mkdirs()
                metaFile.writeText(json.toString())
            } catch (_: Exception) {
                // Ignored
            }
        }

        override var cachedAtMs: Long
            get() = readMeta().optLong("cached_at_ms", 0L)
            set(value) = writeMeta { it.put("cached_at_ms", value) }

        override var cachedSourceUrl: String
            get() = readMeta().optString("source_url", "")
            set(value) = writeMeta { it.put("source_url", value) }

        override var cachedEtag: String
            get() = readMeta().optString("etag", "")
            set(value) = writeMeta { it.put("etag", value) }

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/MetrolistGroup/faraday/master/registry/player_configs.json"
        }
    }

    private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(
                videoId: String,
                useLoginCookies: Boolean,
            ) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }

    private fun AudioQuality.toInnerTubeX(): InnerTubeXAudioQuality =
        when (this) {
            AudioQuality.HIGH, AudioQuality.AUTO -> InnerTubeXAudioQuality.HIGH
            AudioQuality.MEDIUM -> InnerTubeXAudioQuality.AUTO
            AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
        }
}

