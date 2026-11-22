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
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.anitail.music.utils.InnerTubeXPlayer
import com.anitail.music.utils.YTPlayerUtils
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrVideoStream
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@UnstableApi
class VideoAwareMediaSourceFactory(
    private val context: Context,
    private val audioMediaSourceFactory: MediaSource.Factory,
    private val directHttpDataSourceFactoryProvider: (Map<String, String>) -> DataSource.Factory,
) : MediaSource.Factory {
    private val videoSpecs = ConcurrentHashMap<String, YTPlayerUtils.PlaybackData>()
    private val initialPositionsMs = ConcurrentHashMap<String, Long>()
    private val liveSabrStreams = CopyOnWriteArraySet<SabrInputStream>()
    private val extractorsFactory = DefaultExtractorsFactory()

    @Volatile
    private var sabrProducersSuspended = false

    fun register(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData,
        initialPositionMs: Long = 0L,
    ) {
        videoSpecs[mediaId] = playbackData
        initialPositionsMs[mediaId] = initialPositionMs.coerceAtLeast(0L)
    }

    fun hasVideoSpec(mediaId: String): Boolean = videoSpecs.containsKey(mediaId)

    fun getVideoSpec(mediaId: String): YTPlayerUtils.PlaybackData? = videoSpecs[mediaId]

    fun clear(mediaId: String) {
        videoSpecs.remove(mediaId)
        initialPositionsMs.remove(mediaId)
    }

    fun releaseInactive(activeMediaId: String?) {
        val keys = (videoSpecs.keys + initialPositionsMs.keys).toSet()
        for (mediaId in keys) {
            if (mediaId != activeMediaId) {
                videoSpecs.remove(mediaId)
                initialPositionsMs.remove(mediaId)
            }
        }
    }

    fun releaseAll() {
        videoSpecs.clear()
        initialPositionsMs.clear()
        sabrProducersSuspended = false
        val remaining = liveSabrStreams.toList()
        liveSabrStreams.clear()
        remaining.forEach { it.closeAsync() }
    }

    fun suspendSabrProducers() {
        sabrProducersSuspended = true
        liveSabrStreams.forEach { it.suspendProducer() }
    }

    fun resumeSabrProducers(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        sabrProducersSuspended = false
        liveSabrStreams.forEach { it.resumeProducer(safePos) }
    }

    fun updatePlaybackPosition(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        liveSabrStreams.forEach { it.updatePlaybackPosition(safePos) }
    }

    fun beginPlaybackSeek(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        liveSabrStreams.forEach { it.beginPlaybackSeek(safePos) }
    }

    fun endPlaybackSeek(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        liveSabrStreams.forEach { it.endPlaybackSeek(safePos) }
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
        val spec =
            videoSpecs[mediaId]
                ?: return LifecycleManagedMediaSource(
                    audioMediaSourceFactory.createMediaSource(mediaItem),
                    emptyList(),
                )
        val directHttpFactory = directHttpDataSourceFactoryProvider(spec.streamHeaders)

        // 1. HLS manifest stream (IOS / live streams)
        if (spec.isHlsStream) {
            val hlsUri = spec.videoUrl?.takeIf { it.isNotBlank() } ?: spec.streamUrl
            Timber.tag(TAG).d("Creating HlsMediaSource for $mediaId client=${spec.streamClient}")
            return LifecycleManagedMediaSource(
                HlsMediaSource.Factory(directHttpFactory)
                    .createMediaSource(mediaItem.buildUpon().setUri(hlsUri).build()),
                emptyList(),
            )
        }

        // 2. SABR audio and/or SABR video
        if (spec.hasSabrVideo || spec.isSabr) {
            return buildSabrMediaSource(mediaItem, spec, directHttpFactory)
        }

        // 3. Muxed direct video+audio
        val videoUrl = spec.videoUrl
        if (!videoUrl.isNullOrBlank() && spec.isMuxedVideo) {
            Timber.tag(TAG).d("Creating muxed ProgressiveMediaSource for $mediaId height=${spec.videoHeight}")
            val muxedFactory = directFactoryFor(spec, directHttpFactory, spec.videoContentLengthBytes ?: spec.format.contentLength)
            return LifecycleManagedMediaSource(
                ProgressiveMediaSource.Factory(muxedFactory, extractorsFactory)
                    .createMediaSource(mediaItem.buildUpon().setUri(videoUrl).build()),
                emptyList(),
            )
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
            val audioFactory = directFactoryFor(spec, directHttpFactory, spec.format.contentLength)
            val videoFactory = directFactoryFor(spec, directHttpFactory, spec.videoContentLengthBytes)
            val audioSource =
                ProgressiveMediaSource.Factory(audioFactory, extractorsFactory)
                    .createMediaSource(audioMediaItem)
            val videoSource =
                ProgressiveMediaSource.Factory(videoFactory, extractorsFactory)
                    .createMediaSource(videoMediaItem)
            return LifecycleManagedMediaSource(
                MergingMediaSource(true, true, audioSource, videoSource),
                emptyList(),
            )
        }

        return LifecycleManagedMediaSource(
            audioMediaSourceFactory.createMediaSource(mediaItem),
            emptyList(),
        )
    }

    private fun directFactoryFor(
        spec: YTPlayerUtils.PlaybackData,
        directHttpFactory: DataSource.Factory,
        expectedContentLength: Long?,
    ): DataSource.Factory =
        if (spec.requireBoundedRange || spec.useRangeChunks || spec.rangeChunkSizeBytes > 0L) {
            ChunkedRangeDataSource.Factory(
                upstreamFactory = directHttpFactory,
                chunkSizeBytes =
                    spec.rangeChunkSizeBytes
                        .takeIf { it > 0L }
                        ?: ChunkedRangeDataSource.DEFAULT_CHUNK_SIZE_BYTES,
                expectedContentLength = expectedContentLength,
            )
        } else {
            directHttpFactory
        }

    @OptIn(ExperimentalSabrApi::class)
    private fun buildSabrMediaSource(
        mediaItem: MediaItem,
        spec: YTPlayerUtils.PlaybackData,
        directHttpFactory: DataSource.Factory,
    ): MediaSource {
        val mediaId = mediaItem.mediaId
        val startPositionMs = initialPositionsMs[mediaId] ?: 0L
        sabrProducersSuspended = false
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
                        startImmediately = false,
                        tempDirectory = context.cacheDir,
                    ).apply {
                        updatePlaybackPosition(startPositionMs)
                    }
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
                val audioFactory = directFactoryFor(spec, directHttpFactory, spec.format.contentLength)
                val audioItem =
                    mediaItem.buildUpon()
                        .setUri(spec.streamUrl)
                        .setMimeType(spec.format.mimeType.substringBefore(';'))
                        .build()
                ProgressiveMediaSource.Factory(audioFactory, extractorsFactory)
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
                            startImmediately = false,
                            tempDirectory = context.cacheDir,
                        ).apply {
                            updatePlaybackPosition(startPositionMs)
                        }
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
                    val videoFactory = directFactoryFor(spec, directHttpFactory, spec.videoContentLengthBytes)
                    val videoItem =
                        MediaItem.Builder()
                            .setUri(spec.videoUrl!!)
                            .apply { spec.videoMimeType?.substringBefore(';')?.let(::setMimeType) }
                            .build()
                    ProgressiveMediaSource.Factory(videoFactory, extractorsFactory)
                        .createMediaSource(videoItem)
                }

                else -> null
            }

        if (streams.isNotEmpty()) {
            liveSabrStreams.addAll(streams)
        }

        Timber.tag(TAG).d(
            "Creating SABR/merged MediaSource for $mediaId audioSabr=${spec.sabrBootstrap != null} videoSabr=${spec.sabrVideoBootstrap != null} height=${spec.videoHeight}",
        )
        val mergedOrAudio =
            if (videoSource != null) {
                MergingMediaSource(true, true, audioSource, videoSource)
            } else {
                audioSource
            }
        return LifecycleManagedMediaSource(mergedOrAudio, streams)
    }

    private inner class LifecycleManagedMediaSource(
        child: MediaSource,
        private val ownedStreams: List<SabrInputStream>,
    ) : WrappingMediaSource(child) {
        // Force replaceMediaItem to rebuild the MediaSource even when the mediaId/URI matches (Audio <-> Video).
        override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean = false

        override fun releaseSourceInternal() {
            try {
                super.releaseSourceInternal()
            } finally {
                ownedStreams.forEach { stream ->
                    liveSabrStreams.remove(stream)
                    stream.closeAsync()
                }
            }
        }
    }

    companion object {
        private const val TAG = "VideoMediaSource"
    }
}

