package com.anitail.music.playback.video

import com.anitail.innertube.models.SongItem
import com.anitail.music.constants.MusicVideoQuality
import com.anitail.music.models.MediaMetadata
import com.metrolist.innertubex.sabr.SabrVideoVariant

internal const val ANDROID_AUTO_VIDEO_MAX_HEIGHT = 720
private const val SABR_UPSWITCH_HEADROOM = 1.5

internal fun shouldRequestQueueVideo(
    isVideoSong: Boolean,
    isVideoModeActive: Boolean? = null,
    isLocal: Boolean = false,
    isPodcast: Boolean = false,
    videoPlaybackEnabled: Boolean = true,
): Boolean {
    if (!videoPlaybackEnabled || isLocal) return false
    return isVideoModeActive ?: (isVideoSong && !isPodcast)
}

internal fun isQueuePlaybackModeCurrent(
    song: SongItem,
    requestedWantVideo: Boolean,
    currentVideoViewerVisible: Boolean,
    videoPlaybackEnabled: Boolean = true,
): Boolean =
    requestedWantVideo ==
        shouldRequestQueueVideo(
            isVideoSong = song.isVideoSong,
            isVideoModeActive = currentVideoViewerVisible,
            isLocal = song.id.startsWith("local_"),
            isPodcast = isPodcastType(song.musicVideoType),
            videoPlaybackEnabled = videoPlaybackEnabled,
        )

internal fun shouldResolveMusicTrackCounterpart(
    sourceIsVideoSong: Boolean,
    wantVideo: Boolean,
): Boolean = sourceIsVideoSong && !wantVideo

internal fun shouldActivateQueueVideoMode(
    videoRequested: Boolean,
    resolvedResourceHasVideo: Boolean,
): Boolean = videoRequested && resolvedResourceHasVideo

internal fun androidVideoMaxHeight(
    quality: MusicVideoQuality,
    resolvedMaxHeight: Int?,
): Int? =
    if (quality == MusicVideoQuality.AUTO) {
        resolvedMaxHeight?.coerceAtMost(ANDROID_AUTO_VIDEO_MAX_HEIGHT)
    } else {
        resolvedMaxHeight
    }

internal fun automaticVideoMaxHeight(
    isCellular: Boolean,
    bandwidthBps: Long,
    deviceMaxHeight: Int = 2160,
): Int {
    val bandwidthCap =
        when {
            bandwidthBps >= 15_000_000L -> 2160
            bandwidthBps >= 8_000_000L -> 1440
            bandwidthBps >= 4_000_000L -> 1080
            bandwidthBps >= 2_000_000L -> 720
            else -> 480
        }
    val effectiveCap = if (isCellular) minOf(ANDROID_AUTO_VIDEO_MAX_HEIGHT, bandwidthCap) else bandwidthCap
    return minOf(effectiveCap, deviceMaxHeight.coerceAtLeast(360))
}

internal fun shouldShowMusicVideoSurface(
    isVideoModeActive: Boolean,
    isVideoViewerVisible: Boolean,
    hasVideo: Boolean,
    presentationAllowed: Boolean = true,
): Boolean = presentationAllowed && isVideoModeActive && isVideoViewerVisible && hasVideo

internal fun shouldShowPlayerLoadingScreen(
    isPlaybackLoading: Boolean,
    hasVisibleVideoTransitionIndicator: Boolean,
): Boolean = isPlaybackLoading && !hasVisibleVideoTransitionIndicator

/** SABR pacing understates capacity; only stalls step down, to at most half the current bitrate. */
internal fun nextAutoSabrVideoVariant(
    variants: List<SabrVideoVariant>,
    currentHeight: Int,
    maxHeight: Int,
    bandwidthBps: Long?,
    audioBitrate: Int,
    stalled: Boolean,
    allowUpswitch: Boolean,
): SabrVideoVariant? {
    val usable = variants.filter { it.height <= maxHeight }
    val index = usable.indexOfFirst { it.height == currentHeight }
    if (index < 0) return null
    val current = usable[index]
    if (stalled) {
        val lower = usable.take(index)
        return lower.lastOrNull { it.bitrate <= current.bitrate / 2 } ?: lower.firstOrNull()
    }
    if (!allowUpswitch || bandwidthBps == null) return null
    return usable.getOrNull(index + 1)?.takeIf { (it.bitrate + audioBitrate) * SABR_UPSWITCH_HEADROOM <= bandwidthBps }
}

internal fun originalAudioForVideo(
    videoId: String,
    currentVideoId: String?,
    originalAudio: MediaMetadata?,
): MediaMetadata? = originalAudio?.takeIf { videoId == currentVideoId }

internal fun clampPlaybackPositionToDuration(
    positionMs: Long,
    durationSeconds: Int?,
): Long {
    val durationMs = durationSeconds?.toLong()?.times(1000L)?.takeIf { it > 0L } ?: return positionMs.coerceAtLeast(0L)
    return positionMs.coerceIn(0L, durationMs - 1L)
}

