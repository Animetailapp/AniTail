@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anitail.music.playback.video

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.anitail.music.utils.InnerTubeXPlayer
import com.anitail.music.utils.YTPlayerUtils
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrVideoStream
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class VideoAwareMediaSourceFactory(
    private val context: Context,
    private val audioMediaSourceFactory: MediaSource.Factory,
    private val directHttpDataSourceFactoryProvider: (Map<String, String>) -> DataSource.Factory,
) : MediaSource.Factory {
    private val videoSpecs = ConcurrentHashMap<String, YTPlayerUtils.PlaybackData>()
    private val activeSabrStreams = ConcurrentHashMap<String, MutableList<SabrInputStream>>()
    private val extractorsFactory = DefaultExtractorsFactory()

    fun register(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData,
    ) {
        clear(mediaId)
        videoSpecs[mediaId] = playbackData
    }

    fun hasVideoSpec(mediaId: String): Boolean = videoSpecs.containsKey(mediaId)

    fun getVideoSpec(mediaId: String): YTPlayerUtils.PlaybackData? = videoSpecs[mediaId]

    fun clear(mediaId: String) {
        videoSpecs.remove(mediaId)
        closeSabrStreams(mediaId)
    }

    fun releaseInactive(activeMediaId: String?) {
        val keys = (videoSpecs.keys + activeSabrStreams.keys).toSet()
        for (mediaId in keys) {
            if (mediaId != activeMediaId) {
                videoSpecs.remove(mediaId)
                closeSabrStreams(mediaId)
            }
        }
    }

    fun releaseAll() {
        videoSpecs.clear()
        val ids = activeSabrStreams.keys.toList()
        ids.forEach(::closeSabrStreams)
    }

    fun updatePlaybackPosition(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        activeSabrStreams.values.forEach { list ->
            synchronized(list) {
                list.forEach { it.updatePlaybackPosition(safePos) }
            }
        }
    }

    fun beginPlaybackSeek(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        activeSabrStreams.values.forEach { list ->
            synchronized(list) {
                list.forEach { it.beginPlaybackSeek(safePos) }
            }
        }
    }

    fun endPlaybackSeek(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        activeSabrStreams.values.forEach { list ->
            synchronized(list) {
                list.forEach { it.endPlaybackSeek(safePos) }
            }
        }
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        audioMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        audioMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val mediaId = mediaItem.mediaId
        val spec = videoSpecs[mediaId] ?: return audioMediaSourceFactory.createMediaSource(mediaItem)
        val directHttpFactory = directHttpDataSourceFactoryProvider(spec.streamHeaders)

        // 1. HLS manifest stream (IOS / live streams)
        if (spec.isHlsStream) {
            val hlsUri = spec.videoUrl?.takeIf { it.isNotBlank() } ?: spec.streamUrl
            Timber.tag(TAG).d("Creating HlsMediaSource for $mediaId client=${spec.streamClient}")
            return HlsMediaSource.Factory(directHttpFactory)
                .createMediaSource(mediaItem.buildUpon().setUri(hlsUri).build())
        }

        // 2. SABR audio and/or SABR video
        if (spec.hasSabrVideo || spec.isSabr) {
            return buildSabrMediaSource(mediaItem, spec, directHttpFactory)
        }

        // 3. Muxed direct video+audio
        val videoUrl = spec.videoUrl
        if (!videoUrl.isNullOrBlank() && spec.isMuxedVideo) {
            Timber.tag(TAG).d("Creating muxed ProgressiveMediaSource for $mediaId height=${spec.videoHeight}")
            return ProgressiveMediaSource.Factory(directHttpFactory, extractorsFactory)
                .createMediaSource(mediaItem.buildUpon().setUri(videoUrl).build())
        }

        // 4. Separate adaptive audio + video direct URLs
        if (!videoUrl.isNullOrBlank()) {
            Timber.tag(TAG).d(
                "Creating MergingMediaSource(direct audio + direct video) for $mediaId height=${spec.videoHeight}",
            )
            val audioMediaItem =
                mediaItem.buildUpon()
                    .setUri(spec.streamUrl)
                    .setMimeType(spec.format.mimeType.substringBefore(';'))
                    .build()
            val videoMediaItem =
                MediaItem.Builder()
                    .setUri(videoUrl)
                    .apply { spec.videoMimeType?.substringBefore(';')?.let(::setMimeType) }
                    .build()
            val audioSource =
                ProgressiveMediaSource.Factory(directHttpFactory, extractorsFactory)
                    .createMediaSource(audioMediaItem)
            val videoSource =
                ProgressiveMediaSource.Factory(directHttpFactory, extractorsFactory)
                    .createMediaSource(videoMediaItem)
            return MergingMediaSource(true, true, audioSource, videoSource)
        }

        return audioMediaSourceFactory.createMediaSource(mediaItem)
    }

    @OptIn(ExperimentalSabrApi::class)
    private fun buildSabrMediaSource(
        mediaItem: MediaItem,
        spec: YTPlayerUtils.PlaybackData,
        directHttpFactory: DataSource.Factory,
    ): MediaSource {
        val mediaId = mediaItem.mediaId
        closeSabrStreams(mediaId)
        val streams = mutableListOf<SabrInputStream>()
        val httpClient = InnerTubeXPlayer.sabrHttpClient()
        val logger = InnerTubeXPlayer.innertubeLogger()

        val audioSource: MediaSource =
            if (spec.sabrBootstrap != null) {
                val bootstrap = spec.sabrBootstrap
                val audioSabr =
                    SabrInputStream(
                        mediaSize = bootstrap.contentLengthBytes ?: spec.format.contentLength,
                        streamFactory = { initial, position ->
                            sabrAudioPlaybackChunks(
                                httpClient = httpClient,
                                bootstrap = bootstrap,
                                playbackPositionMs = position,
                                initialPlayerTimeMs = initial,
                                onResponse = { diagnostics ->
                                    VideoBandwidthEstimator.recordSample(
                                        diagnostics.responseBytes,
                                        diagnostics.networkReadMs,
                                    )
                                },
                                logger = logger,
                            )
                        },
                        expectedDurationMs = bootstrap.durationMs.takeIf { it > 0L },
                        tempDirectory = context.cacheDir,
                    )
                streams += audioSabr
                val audioItem =
                    mediaItem.buildUpon()
                        .setUri("anitail://sabr/$mediaId")
                        .setMimeType(spec.format.mimeType.substringBefore(';'))
                        .build()
                ProgressiveMediaSource.Factory(
                    DataSource.Factory { SeekableMediaDataSource(audioSabr) },
                    extractorsFactory,
                ).createMediaSource(audioItem)
            } else {
                val audioItem =
                    mediaItem.buildUpon()
                        .setUri(spec.streamUrl)
                        .setMimeType(spec.format.mimeType.substringBefore(';'))
                        .build()
                ProgressiveMediaSource.Factory(directHttpFactory, extractorsFactory)
                    .createMediaSource(audioItem)
            }

        val videoSource: MediaSource? =
            when {
                spec.sabrVideoBootstrap?.selectedVideoFormat != null -> {
                    val videoBootstrap = spec.sabrVideoBootstrap
                    val videoSabr =
                        SabrInputStream(
                            mediaSize = videoBootstrap.selectedVideoContentLengthBytes ?: spec.videoContentLengthBytes,
                            streamFactory = { initial, position ->
                                SabrVideoStream(
                                    httpClient = httpClient,
                                    bootstrap = videoBootstrap,
                                    playbackPositionMs = position,
                                    onResponse = { diagnostics ->
                                        VideoBandwidthEstimator.recordSample(
                                            diagnostics.responseBytes,
                                            diagnostics.networkReadMs,
                                        )
                                    },
                                    initialPlayerTimeMs = initial,
                                    logger = logger,
                                ).chunks()
                            },
                            expectedDurationMs = videoBootstrap.durationMs.takeIf { it > 0L },
                            tempDirectory = context.cacheDir,
                        )
                    streams += videoSabr
                    val videoItem =
                        MediaItem.Builder()
                            .setUri("anitail://sabr-video/$mediaId")
                            .apply { spec.videoMimeType?.substringBefore(';')?.let(::setMimeType) }
                            .build()
                    ProgressiveMediaSource.Factory(
                        DataSource.Factory { SeekableMediaDataSource(videoSabr) },
                        extractorsFactory,
                    ).createMediaSource(videoItem)
                }

                spec.hasDirectVideo && spec.videoUrl != spec.streamUrl -> {
                    val videoItem =
                        MediaItem.Builder()
                            .setUri(spec.videoUrl!!)
                            .apply { spec.videoMimeType?.substringBefore(';')?.let(::setMimeType) }
                            .build()
                    ProgressiveMediaSource.Factory(directHttpFactory, extractorsFactory)
                        .createMediaSource(videoItem)
                }

                else -> null
            }

        if (streams.isNotEmpty()) {
            activeSabrStreams[mediaId] = streams
        }

        Timber.tag(TAG).d(
            "Creating SABR/merged MediaSource for $mediaId audioSabr=${spec.sabrBootstrap != null} videoSabr=${spec.sabrVideoBootstrap != null} height=${spec.videoHeight}",
        )
        return if (videoSource != null) {
            MergingMediaSource(true, true, audioSource, videoSource)
        } else {
            audioSource
        }
    }

    private fun closeSabrStreams(mediaId: String) {
        val list = activeSabrStreams.remove(mediaId) ?: return
        synchronized(list) {
            list.forEach { it.closeAsync() }
            list.clear()
        }
    }

    companion object {
        private const val TAG = "VideoMediaSource"
    }
}

