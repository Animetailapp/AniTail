package com.anitail.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.PlaybackException
import com.anitail.innertube.NewPipeExtractor
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.anitail.innertube.models.YouTubeClient.Companion.IOS
import com.anitail.innertube.models.YouTubeClient.Companion.MOBILE
import com.anitail.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.anitail.innertube.models.YouTubeClient.Companion.WEB
import com.anitail.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.anitail.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.anitail.innertube.models.response.PlayerResponse
import com.anitail.music.constants.AudioQuality
import com.anitail.music.playback.CachedStreamUrl
import com.anitail.music.playback.StreamUrlCache
import com.anitail.music.utils.cipher.CipherDeobfuscator
import com.anitail.music.utils.potoken.PoTokenGenerator
import com.anitail.music.utils.potoken.PoTokenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L
    private val webRemixFailures = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun markWebRemixFailed(videoId: String) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    private fun hasRecentWebRemixFailure(videoId: String): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if ((System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS) {
            webRemixFailures.remove(videoId, failedAt)
            return false
        }
        return true
    }

    fun clearWebRemixFailures() {
        webRemixFailures.clear()
    }

    private val cipherRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.anitail.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR
    )

    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    /**
     * Best-effort warm-up of the PoToken generator.
     */
    suspend fun prewarmPoToken() {
        val sessionId = YouTube.visitorData ?: return
        if (!MAIN_CLIENT.useWebPoTokens) return
        runCatching {
            withContext(Dispatchers.IO) {
                poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, sessionId)
            }
        }.onFailure { Timber.tag(TAG).w(it, "PoToken prewarm skipped: ${it.message}") }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
        val streamHeaders: Map<String, String> = emptyMap(),
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        targetItag: Int = 0,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")

        // 1. Check StreamUrlCache first
        val cachedStream = StreamUrlCache.get(videoId, targetItag)
        if (cachedStream != null) {
            Timber.tag(logTag).d("Using valid cached stream URL from StreamUrlCache")
        }

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: $signatureTimestamp")

        // 2. Generate PoToken for Web Client
        var poToken: PoTokenResult? = null
        val sessionId = YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully for playback")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).w(e, "PoToken generation failed: ${e.message}")
            }
        }

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse: PlayerResponse? = null
        var mainPlayerResponseError: Throwable? = null
        try {
            val response = YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = MAIN_CLIENT,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken?.playerRequestPoToken
            ).getOrThrow()

            if (isPlayable(response.playabilityStatus.status)) {
                mainPlayerResponse = response
            } else {
                Timber.tag(logTag).d("MAIN_CLIENT playabilityStatus not OK: ${response.playabilityStatus.status}, reason: ${response.playabilityStatus.reason}")
                mainPlayerResponseError = Exception(response.playabilityStatus.reason ?: "Playability status not OK")
                mainPlayerResponse = response
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Failed to get player response with MAIN_CLIENT")
            mainPlayerResponseError = e
        }

        if (mainPlayerResponse == null || !isPlayable(mainPlayerResponse.playabilityStatus.status)) {
            for (client in STREAM_FALLBACK_CLIENTS) {
                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) continue
                try {
                    val response = YouTube.player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = client,
                        signatureTimestamp = if (client.useSignatureTimestamp) signatureTimestamp else null,
                    ).getOrNull()
                    if (isPlayable(response?.playabilityStatus?.status)) {
                        Timber.tag(logTag).d("Successfully fetched playable player response using fallback client: ${client.clientName}")
                        mainPlayerResponse = response
                        mainPlayerResponseError = null
                        break
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).d("Fallback client ${client.clientName} failed to fetch player response: ${e.message}")
                }
            }
        }

        if (mainPlayerResponse == null) {
            throw (mainPlayerResponseError ?: Exception("Failed to fetch player response"))
        }

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var successClient: YouTubeClient = MAIN_CLIENT

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                if (hasRecentWebRemixFailure(videoId)) {
                    Timber.tag(logTag).d("Skipping MAIN_CLIENT ($MAIN_CLIENT) due to recent failure")
                    continue
                }
                // try with streams from main client first
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                val clientSigTimestamp = if (client.useSignatureTimestamp) signatureTimestamp else null
                streamPlayerResponse =
                    YouTube.player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = client,
                        signatureTimestamp = clientSigTimestamp,
                        poToken = clientPoToken,
                    ).getOrNull()
            }

            // process current client response
            val response = streamPlayerResponse
            if (response != null && isPlayable(response.playabilityStatus.status)) {
                Timber.tag(logTag).d("Player response status OK for client: ${client.clientName}")

                format =
                    findFormat(
                        response,
                        audioQuality,
                        connectivityManager,
                        targetItag,
                    )

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${client.clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    continue
                }

                // Apply n-transform and PoToken for web clients
                val currentClient = client
                val needsNTransform = currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")

                if (needsNTransform) {
                    try {
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl)
                        val needsPoToken = currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null
                        if (needsPoToken) {
                            val separator = if ("?" in streamUrl) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "N-transform or pot append failed: ${e.message}")
                    }
                }

                streamExpiresInSeconds = response.streamingData?.expiresInSeconds ?: 21600
                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    /** skip [validateStatus] for last client */
                    Timber.tag(logTag).d("Using last fallback client without validation: ${client.clientName}")
                    successClient = client
                    break
                }

                if (currentClient.clientName == "WEB_REMIX") {
                    Timber.tag(logTag).d("WEB_REMIX - skipping HEAD validation, letting ExoPlayer try directly")
                    successClient = currentClient
                    break
                }

                if (validateStatus(streamUrl, currentClient.streamHeaders())) {
                    // working stream found
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    successClient = currentClient
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")
                    if (needsNTransform) {
                        cipherRefreshScope.launch {
                            if (CipherDeobfuscator.onStreamRejected()) clearWebRemixFailures()
                        }
                    }
                }
            } else {
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            throw Exception("Bad stream player response")
        }

        if (!isPlayable(streamPlayerResponse.playabilityStatus.status)) {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        val streamHeaders = successClient.streamHeaders()

        // Cache in StreamUrlCache
        StreamUrlCache.put(
            mediaId = videoId,
            url = streamUrl,
            requestHeaders = streamHeaders,
            clientName = successClient.clientName,
            expiresInSeconds = streamExpiresInSeconds,
            itag = format.itag,
        )

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        PlaybackData(
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = playbackTracking,
            format = format,
            streamUrl = streamUrl,
            streamExpiresInSeconds = streamExpiresInSeconds,
            streamClient = successClient.clientName,
            streamHeaders = streamHeaders,
        )
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        val result = YouTube.player(videoId, playlistId, client = MAIN_CLIENT)
        if (result.isSuccess && isPlayable(result.getOrNull()?.playabilityStatus?.status)) {
            Timber.tag(logTag).d("Successfully fetched metadata")
            return result
        }

        Timber.tag(logTag).d("MAIN_CLIENT failed to fetch metadata or playabilityStatus not OK, trying fallback clients")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val isLoggedIn = YouTube.cookie != null
        for (client in STREAM_FALLBACK_CLIENTS) {
            if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) continue
            try {
                val res = YouTube.player(
                    videoId = videoId,
                    playlistId = playlistId,
                    client = client,
                    signatureTimestamp = if (client.useSignatureTimestamp) signatureTimestamp else null
                ).getOrNull()
                if (res != null && isPlayable(res.playabilityStatus?.status)) {
                    Timber.tag(logTag).d("Successfully fetched playable player response for metadata using fallback client: ${client.clientName}")
                    return Result.success(res)
                }
            } catch (e: Exception) {
                Timber.tag(logTag).d("Fallback client ${client.clientName} failed to fetch player response for metadata: ${e.message}")
            }
        }
        return result
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        targetItag: Int = 0,
    ): PlayerResponse.StreamingData.Format? {
        if (targetItag > 0) {
            val exactFormat = playerResponse.streamingData?.adaptiveFormats?.find { it.itag == targetItag }
            if (exactFormat != null) {
                Timber.tag(logTag).d("Using exact format itag: $targetItag")
                return exactFormat
            }
            Timber.tag(logTag).w("Requested itag $targetItag not found, falling back to auto")
        }

        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }

    data class AudioFormatOption(
        val itag: Int,
        val bitrate: Int,
        val bitrateKbps: Int,
        val mimeType: String,
        val codec: String,
    ) {
        val displayName: String
            get() = "${bitrateKbps}kbps ${codec.uppercase()}"

        val isM4a: Boolean
            get() = codec.equals("M4A", ignoreCase = true) || mimeType.contains("mp4a")

        val supportsMetadata: Boolean
            get() = isM4a && bitrateKbps >= 128
    }

    suspend fun getAllAvailableAudioFormats(
        videoId: String,
    ): Result<List<AudioFormatOption>> = runCatching {
        val isLoggedIn = YouTube.cookie != null
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val allClients = listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS.toList()
        val uniqueFormats = linkedMapOf<Int, AudioFormatOption>()

        allClients.forEach { client ->
            if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) return@forEach

            val response = YouTube.player(
                videoId = videoId,
                client = client,
                signatureTimestamp = if (client.useSignatureTimestamp) signatureTimestamp else null,
            ).getOrNull() ?: return@forEach

            if (!isPlayable(response.playabilityStatus.status)) return@forEach

            response.streamingData?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio }
                ?.forEach { format ->
                    if (uniqueFormats.containsKey(format.itag)) return@forEach

                    val codec = when {
                        format.mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                        format.mimeType.contains("mp4a", ignoreCase = true) -> "M4A"
                        else -> format.mimeType.substringAfter("audio/").substringBefore(";").uppercase()
                    }

                    uniqueFormats[format.itag] = AudioFormatOption(
                        itag = format.itag,
                        bitrate = format.bitrate,
                        bitrateKbps = format.bitrate / 1000,
                        mimeType = format.mimeType,
                        codec = codec,
                    )
                }
        }

        uniqueFormats.values
            .sortedWith(
                compareByDescending<AudioFormatOption> { it.bitrate }
                    .thenBy { it.codec }
            )
    }

    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)

            requestHeaders.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val isSuccessful = response.isSuccessful
                Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
                return isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }

    private fun YouTubeClient.streamHeaders(): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "en-US,en;q=0.9")

            when (clientName) {
                "WEB_REMIX" -> {
                    put("Referer", "https://music.youtube.com/")
                    put("Origin", "https://music.youtube.com")
                }

                "WEB_CREATOR" -> {
                    put("Referer", "https://studio.youtube.com/")
                    put("Origin", "https://studio.youtube.com")
                }

                else -> {
                    put("Referer", "https://www.youtube.com/")
                    put("Origin", "https://www.youtube.com")
                }
            }
        }

    /**
     * Wrapper around the [CipherDeobfuscator.signatureTimestamp] and [NewPipeExtractor.getSignatureTimestamp]
     */
    private suspend fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")

        // First attempt with CipherDeobfuscator
        val cipherSts = try {
            CipherDeobfuscator.signatureTimestamp()
                ?.also { Timber.tag(logTag).d("Signature timestamp from cipher player: $it") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(logTag).w(e, "Cipher player STS fetch failed")
            null
        }

        if (cipherSts != null) return cipherSts

        // Fallback to NewPipeExtractor
        return NewPipeExtractor.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).d("Signature timestamp obtained via NewPipe: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Finds stream URL by evaluating direct url, CipherDeobfuscator, and NewPipeExtractor
     */
    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")

        // 1. Check if format already contains direct URL
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        // 2. Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, attempting CipherDeobfuscator")
            try {
                val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
                if (customDeobfuscatedUrl != null) {
                    Timber.tag(logTag).d("Stream URL obtained via CipherDeobfuscator")
                    return customDeobfuscatedUrl
                }
            } catch (e: Exception) {
                Timber.tag(logTag).w(e, "CipherDeobfuscator deobfuscation failed")
            }
        }

        // 3. Fallback to NewPipe signature deobfuscator
        return try {
            NewPipeExtractor.getStreamUrl(format, videoId)
                .also {
                    if (it != null) {
                        Timber.tag(logTag).d("Stream URL obtained via NewPipeExtractor")
                    } else {
                        Timber.tag(logTag).d("Stream URL not found via NewPipeExtractor")
                    }
                }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Failed to get stream URL via NewPipe")
            reportException(e)
            null
        }
    }

    private fun isPlayable(status: String?): Boolean {
        return status == "OK" || status == "CONTENT_CHECK_REQUIRED"
    }
}
