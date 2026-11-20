package com.anitail.music.playback.video

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import com.anitail.innertube.models.SongItem
import com.anitail.music.R
import com.anitail.music.constants.MusicVideoPlaybackEnabledKey
import com.anitail.music.constants.MusicVideoQuality
import com.anitail.music.constants.MusicVideoQualityKey
import com.anitail.music.constants.VideoViewerVisibleKey
import com.anitail.music.constants.targetHeight
import com.anitail.music.extensions.toEnum
import com.anitail.music.models.MediaMetadata
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

class AndroidVideoModeController(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val connectivityManager: ConnectivityManager,
    private val scope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val musicVideoResolver: MusicVideoResolver,
    private val videoMediaSourceFactory: VideoAwareMediaSourceFactory,
    private val getCurrentMetadata: () -> MediaMetadata?,
    private val getCurrentMediaId: () -> String?,
    private val getCurrentPositionMs: () -> Long,
    private val getPlaybackState: () -> Int,
    private val getIsPlaying: () -> Boolean,
    private val getPlayWhenReady: () -> Boolean,
    private val isVideoPlaybackAllowed: () -> Boolean,
    private val fetchVideoPlaybackData: suspend (metadata: MediaMetadata, maxVideoHeight: Int?) -> YTPlayerUtils.PlaybackData?,
    private val fetchTrackMetadata: suspend (videoId: String) -> SongItem?,
    private val commitMediaReplacement: (
        targetMetadata: MediaMetadata,
        targetPositionMs: Long,
        resumePlayback: Boolean,
        replaceItem: Boolean,
    ) -> Boolean,
    private val onVideoModeCommitted: (isVideo: Boolean) -> Unit,
) {
    private val _isVideoModeActive = MutableStateFlow(false)
    val isVideoModeActive: StateFlow<Boolean> = _isVideoModeActive.asStateFlow()

    private val _isVideoModeResolving = MutableStateFlow(false)
    val isVideoModeResolving: StateFlow<Boolean> = _isVideoModeResolving.asStateFlow()

    private val _isVideoViewerVisible = MutableStateFlow(dataStore.get(VideoViewerVisibleKey, false))
    val isVideoViewerVisible: StateFlow<Boolean> = _isVideoViewerVisible.asStateFlow()

    private val _currentVideoPlaybackData = MutableStateFlow<YTPlayerUtils.PlaybackData?>(null)
    val currentVideoPlaybackData: StateFlow<YTPlayerUtils.PlaybackData?> = _currentVideoPlaybackData.asStateFlow()

    private val _videoErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val videoErrorEvent: SharedFlow<String> = _videoErrorEvent.asSharedFlow()

    private val _playbackInstanceId = MutableStateFlow(1L)

    private var videoModeJob: Job? = null
    private var sabrAutoQualityJob: Job? = null
    private var videoModeGeneration = 0L
    private var videoModeSongId: String? = null
    private var videoModePositionMap: CounterpartPositionMap? = null
    private var videoModeOriginalAudio: MediaMetadata? = null

    @Volatile
    var isCommittingTransition: Boolean = false
        private set

    init {
        sabrAutoQualityJob = scope.launch { runSabrAutoQuality() }
    }

    fun toggleVideoMode() {
        if (!dataStore.get(MusicVideoPlaybackEnabledKey, true) || _isVideoModeResolving.value) return
        if (!isVideoPlaybackAllowed()) return
        getCurrentMetadata() ?: return
        setVideoMode(!_isVideoViewerVisible.value)
    }

    fun setVideoMode(
        wantVideo: Boolean,
        silent: Boolean = false,
    ) {
        if (!isVideoPlaybackAllowed()) return
        if (wantVideo && !dataStore.get(MusicVideoPlaybackEnabledKey, true)) return

        if (!wantVideo && _isVideoModeResolving.value && !_isVideoModeActive.value) {
            clearVideoMode(resetViewerPreference = true)
            return
        }

        if (wantVideo && !_isVideoModeActive.value) {
            requestVideoMode(wantVideo = true, silent = silent)
        } else if (!wantVideo && _isVideoModeActive.value) {
            requestVideoMode(wantVideo = false, silent = silent)
        } else {
            setVideoViewerVisible(wantVideo)
        }
    }

    fun changeMusicVideoResolution(quality: MusicVideoQuality) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[MusicVideoQualityKey] = quality.name
            }
            if (_isVideoModeActive.value) {
                requestVideoMode(wantVideo = true, silent = false, forceReloadSameQuality = true)
            }
        }
    }

    fun onTrackTransition(metadata: MediaMetadata?) {
        if (isCommittingTransition) return
        val instanceId = _playbackInstanceId.value + 1L
        _playbackInstanceId.value = instanceId

        val previousSongId = videoModeSongId
        clearVideoMode(resetViewerPreference = false)
        videoMediaSourceFactory.releaseInactive(metadata?.id)
        if (previousSongId != null && previousSongId != metadata?.id) {
            videoMediaSourceFactory.clear(previousSongId)
        }

        if (metadata == null || metadata.isLocalTrack) return
        if (!isVideoPlaybackAllowed()) return

        val enabled = dataStore.get(MusicVideoPlaybackEnabledKey, true)
        val wantVideo =
            shouldRequestQueueVideo(
                isVideoSong = metadata.isVideoSong,
                isVideoModeActive = _isVideoViewerVisible.value,
                isLocal = metadata.isLocalTrack,
                isPodcast = isPodcastType(metadata.musicVideoType),
                videoPlaybackEnabled = enabled,
            )
        if (wantVideo) {
            Timber.tag(TAG).d("Auto-requesting video mode on track transition for ${metadata.id}")
            requestVideoMode(wantVideo = true, silent = true)
        }
    }

    fun onVideoPlaybackSettingChanged(enabled: Boolean) {
        if (!enabled && (_isVideoModeActive.value || _isVideoModeResolving.value)) {
            setVideoMode(wantVideo = false, silent = true)
            setVideoViewerVisible(false)
        }
    }

    fun fallbackToAudioOnError() {
        val current = getCurrentMetadata()
        Timber.tag(TAG).w("Video playback error for ${current?.id}; falling back to audio mode")
        _videoErrorEvent.tryEmit(context.getString(R.string.music_video_unavailable))
        if (current != null && _isVideoModeActive.value) {
            requestVideoMode(wantVideo = false, silent = true)
        } else {
            clearVideoMode(resetViewerPreference = false)
        }
    }

    private fun requestVideoMode(
        wantVideo: Boolean,
        silent: Boolean,
        forceReloadSameQuality: Boolean = false,
    ) {
        if (wantVideo && !dataStore.get(MusicVideoPlaybackEnabledKey, true)) return
        if (!isVideoPlaybackAllowed()) return

        val sourceMetadata = getCurrentMetadata() ?: return
        if (sourceMetadata.isLocalTrack) {
            Timber.tag(TAG).d("Ignoring video mode request for local track ${sourceMetadata.id}")
            return
        }

        val wasVideoModeActive = _isVideoModeActive.value
        val isPodcastEpisode = isPodcastType(sourceMetadata.musicVideoType)
        val generation = ++videoModeGeneration
        val expectedInstanceId = _playbackInstanceId.value
        val expectedSourceId = sourceMetadata.id

        videoModeJob?.cancel()
        _isVideoModeResolving.value = true

        videoModeJob =
            scope.launch {
                try {
                    val existingOriginal =
                        originalAudioForVideo(
                            videoId = sourceMetadata.id,
                            currentVideoId = videoModeSongId,
                            originalAudio = videoModeOriginalAudio,
                        )

                    var targetMetadata = sourceMetadata
                    var positionMap: CounterpartPositionMap? = null

                    if (wantVideo) {
                        if (!sourceMetadata.isVideoSong && !isPodcastEpisode) {
                            when (val result = withContext(Dispatchers.IO) { musicVideoResolver.resolveMusicVideo(sourceMetadata.toSongItem()) }) {
                                is MusicVideoResult.Found -> {
                                    Timber.tag(TAG).d(
                                        "Counterpart video found source=${sourceMetadata.id} target=${result.videoId} mapped=${result.positionMap != null}",
                                    )
                                    val fetchedSong =
                                        withContext(Dispatchers.IO) {
                                            runCatching { fetchTrackMetadata(result.videoId) }.getOrNull()
                                        }
                                    targetMetadata =
                                        fetchedSong?.toMediaMetadata()?.let { fetched ->
                                            if (fetched.musicVideoType.isNullOrBlank()) {
                                                fetched.copy(musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
                                            } else {
                                                fetched
                                            }
                                        } ?: sourceMetadata.copy(
                                            id = result.videoId,
                                            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                                        )
                                    positionMap = result.positionMap
                                }

                                MusicVideoResult.NotFound -> {
                                    if (!silent && isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) {
                                        _videoErrorEvent.tryEmit(context.getString(R.string.music_video_not_found))
                                    }
                                    return@launch
                                }

                                MusicVideoResult.Error -> {
                                    if (!silent && isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) {
                                        _videoErrorEvent.tryEmit(context.getString(R.string.music_video_unavailable))
                                    }
                                    return@launch
                                }
                            }
                        }

                        if (!isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) return@launch

                        val quality = dataStore[MusicVideoQualityKey].toEnum(MusicVideoQuality.AUTO)
                        val maxHeight = computeMaxVideoHeight(quality)
                        val playbackData =
                            withContext(Dispatchers.IO) {
                                fetchVideoPlaybackData(targetMetadata, maxHeight)
                            }

                        if (!isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) return@launch
                        if (playbackData == null || !playbackData.hasVideo) {
                            Timber.tag(TAG).w("No playable video stream for ${targetMetadata.id}")
                            if (!silent) {
                                _videoErrorEvent.tryEmit(context.getString(R.string.music_video_unavailable))
                            }
                            return@launch
                        }

                        val transitionPositionMap =
                            positionMap.takeUnless { wasVideoModeActive && forceReloadSameQuality }
                        val currentPosMs = getCurrentPositionMs()
                        val targetPosMs = (transitionPositionMap?.mapPosition(currentPosMs) ?: currentPosMs).coerceAtLeast(0L)
                        val resumePlayback = getPlayWhenReady()

                        videoMediaSourceFactory.register(targetMetadata.id, playbackData)

                        isCommittingTransition = true
                        val committed =
                            try {
                                commitMediaReplacement(
                                    targetMetadata,
                                    targetPosMs,
                                    resumePlayback,
                                    targetMetadata.id != sourceMetadata.id,
                                )
                            } finally {
                                isCommittingTransition = false
                            }

                        if (!committed) {
                            videoMediaSourceFactory.clear(targetMetadata.id)
                            return@launch
                        }

                        videoModeSongId = targetMetadata.id
                        videoModePositionMap =
                            if (wasVideoModeActive && forceReloadSameQuality) {
                                videoModePositionMap
                            } else {
                                positionMap
                            }
                        videoModeOriginalAudio =
                            when {
                                wasVideoModeActive && forceReloadSameQuality -> videoModeOriginalAudio
                                targetMetadata.id != sourceMetadata.id -> sourceMetadata
                                else -> null
                            }
                        _currentVideoPlaybackData.value = playbackData
                        _isVideoModeActive.value = true
                        if (!silent || _isVideoViewerVisible.value) {
                            setVideoViewerVisible(true)
                        }
                        onVideoModeCommitted(true)
                    } else {
                        if (existingOriginal != null) {
                            targetMetadata = existingOriginal
                            positionMap = videoModePositionMap?.reversed()
                        } else if (shouldResolveMusicTrackCounterpart(sourceMetadata.isVideoSong, wantVideo = false)) {
                            when (val result = withContext(Dispatchers.IO) { musicVideoResolver.resolveMusicTrack(sourceMetadata.toSongItem()) }) {
                                is MusicTrackResult.Found -> {
                                    Timber.tag(TAG).d(
                                        "Counterpart track found source=${sourceMetadata.id} target=${result.song.id}",
                                    )
                                    targetMetadata = result.song.toMediaMetadata()
                                    positionMap = result.positionMap
                                }

                                MusicTrackResult.NotFound, MusicTrackResult.Error -> {
                                    Timber.tag(TAG).d(
                                        "Counterpart audio track unavailable for ${sourceMetadata.id}; falling back to audio stream",
                                    )
                                    positionMap = null
                                }
                            }
                        } else {
                            positionMap = videoModePositionMap?.reversed()
                        }

                        if (!isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) return@launch

                        val currentPosMs = getCurrentPositionMs()
                        val mappedPosMs = positionMap?.mapPosition(currentPosMs) ?: currentPosMs
                        val clampedPosMs = clampPlaybackPositionToDuration(mappedPosMs, targetMetadata.duration)
                        val resumePlayback = getPlayWhenReady()

                        videoMediaSourceFactory.clear(sourceMetadata.id)
                        videoMediaSourceFactory.clear(targetMetadata.id)

                        isCommittingTransition = true
                        val committed =
                            try {
                                commitMediaReplacement(
                                    targetMetadata,
                                    clampedPosMs,
                                    resumePlayback,
                                    targetMetadata.id != sourceMetadata.id,
                                )
                            } finally {
                                isCommittingTransition = false
                            }

                        if (!committed) return@launch

                        videoModeSongId = null
                        videoModePositionMap = null
                        videoModeOriginalAudio = null
                        _currentVideoPlaybackData.value = null
                        _isVideoModeActive.value = false
                        setVideoViewerVisible(false)
                        onVideoModeCommitted(false)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Video mode transition failed wantVideo=$wantVideo")
                    if (!silent && isCurrentTarget(generation, expectedInstanceId, expectedSourceId)) {
                        _videoErrorEvent.tryEmit(
                            context.getString(
                                if (wantVideo) R.string.music_video_unavailable else R.string.music_track_unavailable,
                            ),
                        )
                    }
                } finally {
                    if (generation == videoModeGeneration) {
                        _isVideoModeResolving.value = false
                    }
                }
            }
    }

    private suspend fun runSabrAutoQuality() {
        var lastPositionMs = -1L
        var bufferingSinceMs: Long? = null
        var nextDecisionAtMs = 0L
        var upswitchBlockedUntilMs = 0L
        var upswitchBackoffMs = UPSWITCH_BACKOFF_MS
        var failedDownswitch: Pair<YTPlayerUtils.PlaybackData, Int>? = null

        while (true) {
            combine(
                _currentVideoPlaybackData,
                _isVideoModeActive,
                _playbackInstanceId,
            ) { data, active, _ ->
                active && data?.hasSabrVideo == true && data.videoHeight != null &&
                    data.sabrVideoBootstrap?.videoVariants?.isNotEmpty() == true
            }.first { eligible ->
                if (!eligible) {
                    bufferingSinceMs = null
                    lastPositionMs = -1L
                }
                eligible
            }

            delay(AUTO_QUALITY_POLL_MS)

            val resource = _currentVideoPlaybackData.value
            val currentMetadata = getCurrentMetadata()
            val variants = resource?.sabrVideoBootstrap?.videoVariants.orEmpty()
            val currentHeight = failedDownswitch?.takeIf { it.first === resource }?.second ?: resource?.videoHeight
            val quality = dataStore[MusicVideoQualityKey].toEnum(MusicVideoQuality.AUTO)

            if (
                resource == null ||
                currentMetadata == null ||
                !getPlayWhenReady() ||
                currentHeight == null ||
                !resource.hasSabrVideo ||
                variants.isEmpty() ||
                !_isVideoModeActive.value ||
                videoModeJob?.isActive == true ||
                quality != MusicVideoQuality.AUTO
            ) {
                bufferingSinceMs = null
                continue
            }

            val now = SystemClock.elapsedRealtime()
            val positionMs = getCurrentPositionMs()
            val buffering = getPlaybackState() == Player.STATE_BUFFERING && getPlayWhenReady()
            bufferingSinceMs =
                if (buffering && abs(positionMs - lastPositionMs) < SEEK_JUMP_MS) {
                    bufferingSinceMs ?: now
                } else {
                    null
                }
            lastPositionMs = positionMs
            if (now < nextDecisionAtMs) continue

            val stalled = bufferingSinceMs?.let { now - it >= STALL_MS } == true
            val maxHeight = computeMaxVideoHeight(MusicVideoQuality.AUTO) ?: continue
            val bandwidthBps = VideoBandwidthEstimator.recentBandwidthBps()
            val next =
                nextAutoSabrVideoVariant(
                    variants = variants,
                    currentHeight = currentHeight,
                    maxHeight = maxHeight,
                    bandwidthBps = bandwidthBps,
                    audioBitrate = resource.format.bitrate,
                    stalled = stalled,
                    allowUpswitch = now >= upswitchBlockedUntilMs && getIsPlaying(),
                ) ?: continue

            val upswitch = next.height > currentHeight
            val updatedData = resource.withSabrVideoVariant(next)
            videoMediaSourceFactory.register(currentMetadata.id, updatedData)
            isCommittingTransition = true
            val committed =
                try {
                    commitMediaReplacement(
                        currentMetadata,
                        getCurrentPositionMs(),
                        getPlayWhenReady(),
                        false,
                    )
                } finally {
                    isCommittingTransition = false
                }

            nextDecisionAtMs = SystemClock.elapsedRealtime() + AUTO_QUALITY_COOLDOWN_MS
            bufferingSinceMs = null
            if (committed) {
                _currentVideoPlaybackData.value = updatedData
            }
            if (upswitch && committed) {
                upswitchBackoffMs = UPSWITCH_BACKOFF_MS
            } else {
                upswitchBlockedUntilMs = nextDecisionAtMs + upswitchBackoffMs
                if (upswitch) {
                    upswitchBackoffMs = (upswitchBackoffMs * 2).coerceAtMost(MAX_UPSWITCH_BACKOFF_MS)
                }
            }
            failedDownswitch = if (committed || upswitch) null else resource to next.height
            Timber.tag(TAG).d(
                "Auto SABR quality ${if (committed) "switched" else "failed"} ${currentHeight}p -> ${next.height}p (stalled=$stalled, bw=$bandwidthBps)",
            )
        }
    }

    private fun computeMaxVideoHeight(quality: MusicVideoQuality): Int? {
        if (quality != MusicVideoQuality.AUTO) return quality.targetHeight
        val isCellular = isCellularConnection()
        val bandwidth = VideoBandwidthEstimator.recentBandwidthBps() ?: DEFAULT_ESTIMATED_BANDWIDTH_BPS
        val displayHeight =
            context.resources.displayMetrics.let { metrics ->
                minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(360)
            }
        val autoCap = automaticVideoMaxHeight(isCellular = isCellular, bandwidthBps = bandwidth, deviceMaxHeight = displayHeight)
        return androidVideoMaxHeight(MusicVideoQuality.AUTO, autoCap)
    }

    private fun isCellularConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return connectivityManager.isActiveNetworkMetered
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return connectivityManager.isActiveNetworkMetered
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return false
        }
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || connectivityManager.isActiveNetworkMetered
    }

    private fun isCurrentTarget(
        generation: Long,
        expectedInstanceId: Long,
        expectedSourceId: String,
    ): Boolean =
        generation == videoModeGeneration &&
            expectedInstanceId == _playbackInstanceId.value &&
            getCurrentMediaId() == expectedSourceId

    fun clearVideoMode(resetViewerPreference: Boolean = false) {
        videoModeGeneration++
        videoModeJob?.cancel()
        videoModeJob = null
        videoModeSongId = null
        videoModePositionMap = null
        videoModeOriginalAudio = null
        _currentVideoPlaybackData.value = null
        _isVideoModeActive.value = false
        _isVideoModeResolving.value = false
        onVideoModeCommitted(false)
        if (resetViewerPreference) {
            setVideoViewerVisible(false)
        }
    }

    fun release() {
        clearVideoMode(resetViewerPreference = false)
        sabrAutoQualityJob?.cancel()
        sabrAutoQualityJob = null
        videoMediaSourceFactory.releaseAll()
    }

    private fun setVideoViewerVisible(visible: Boolean) {
        _isVideoViewerVisible.value = visible
        scope.launch {
            dataStore.edit { prefs ->
                prefs[VideoViewerVisibleKey] = visible
            }
        }
    }

    companion object {
        private const val TAG = "AndroidVideoMode"
        private const val AUTO_QUALITY_POLL_MS = 2_000L
        private const val AUTO_QUALITY_COOLDOWN_MS = 15_000L
        private const val STALL_MS = 4_000L
        private const val UPSWITCH_BACKOFF_MS = 60_000L
        private const val MAX_UPSWITCH_BACKOFF_MS = 600_000L
        private const val SEEK_JUMP_MS = 5_000L
        private const val DEFAULT_ESTIMATED_BANDWIDTH_BPS = 4_000_000L
    }
}
