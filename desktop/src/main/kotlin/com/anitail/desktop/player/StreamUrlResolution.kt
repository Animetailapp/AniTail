package com.anitail.desktop.player

import com.anitail.innertube.NewPipeExtractor
import com.anitail.innertube.models.YouTubeClient
import com.anitail.innertube.models.response.PlayerResponse

internal fun interface StreamUrlResolver {
    fun resolve(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        userAgentOverride: String?
    ): Result<String>
}

internal object StreamUrlResolution {
    fun resolveStreamUrl(
        resolver: StreamUrlResolver,
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient
    ): Result<String> {
        // Keep the default WEB user-agent for NewPipe deobfuscation.
        return resolver.resolve(format, videoId, null)
    }
}

internal object NewPipeStreamUrlResolver : StreamUrlResolver {
    override fun resolve(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        userAgentOverride: String?
    ): Result<String> {
        return runCatching {
            NewPipeExtractor.getStreamUrl(format, videoId)
                ?: error("Could not resolve stream URL")
        }
    }
}
