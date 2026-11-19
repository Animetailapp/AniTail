package com.anitail.music.playback.video

import com.anitail.innertube.models.SongItem
import com.anitail.music.constants.MusicVideoQuality
import com.anitail.music.models.MediaMetadata
import com.metrolist.innertubex.sabr.SabrFormatId
import com.metrolist.innertubex.sabr.SabrVideoVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackPoliciesTest {
    @Test
    fun queueVideoPolicyRespectsDisabledPlaybackLocalTracksAndMode() {
        assertFalse(shouldRequestQueueVideo(isVideoSong = true, videoPlaybackEnabled = false))
        assertFalse(shouldRequestQueueVideo(isVideoSong = false, isVideoModeActive = true, videoPlaybackEnabled = false))
        assertFalse(shouldRequestQueueVideo(isVideoSong = true, isLocal = true, videoPlaybackEnabled = true))
        assertTrue(shouldRequestQueueVideo(isVideoSong = false, isVideoModeActive = true))
        assertFalse(shouldRequestQueueVideo(isVideoSong = true, isVideoModeActive = false))
        assertTrue(shouldRequestQueueVideo(isVideoSong = true, isVideoModeActive = null))
        assertFalse(shouldRequestQueueVideo(isVideoSong = false, isPodcast = true, isVideoModeActive = null))
    }

    @Test
    fun modeCurrencyAndCounterpartPoliciesBehaveAsExpected() {
        val videoSong =
            SongItem(
                id = "video-1",
                title = "Video",
                artists = emptyList(),
                album = null,
                duration = 200,
                thumbnail = "",
                explicit = false,
                musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
            )
        assertTrue(isQueuePlaybackModeCurrent(videoSong, requestedWantVideo = true, currentVideoViewerVisible = true))
        assertFalse(isQueuePlaybackModeCurrent(videoSong, requestedWantVideo = false, currentVideoViewerVisible = true))
        assertTrue(shouldResolveMusicTrackCounterpart(sourceIsVideoSong = true, wantVideo = false))
        assertFalse(shouldResolveMusicTrackCounterpart(sourceIsVideoSong = true, wantVideo = true))
        assertTrue(shouldActivateQueueVideoMode(videoRequested = true, resolvedResourceHasVideo = true))
        assertFalse(shouldActivateQueueVideoMode(videoRequested = true, resolvedResourceHasVideo = false))
    }

    @Test
    fun qualityAndSurfaceVisibilityPoliciesMatchBounds() {
        assertEquals(720, androidVideoMaxHeight(MusicVideoQuality.AUTO, 1080))
        assertEquals(480, androidVideoMaxHeight(MusicVideoQuality.AUTO, 480))
        assertEquals(2160, androidVideoMaxHeight(MusicVideoQuality.P2160, 2160))

        assertEquals(720, automaticVideoMaxHeight(isCellular = true, bandwidthBps = 20_000_000L, deviceMaxHeight = 2160))
        assertEquals(1080, automaticVideoMaxHeight(isCellular = false, bandwidthBps = 5_000_000L, deviceMaxHeight = 2160))
        assertEquals(1440, automaticVideoMaxHeight(isCellular = false, bandwidthBps = 20_000_000L, deviceMaxHeight = 1440))

        assertTrue(shouldShowMusicVideoSurface(isVideoModeActive = true, isVideoViewerVisible = true, hasVideo = true))
        assertFalse(shouldShowMusicVideoSurface(isVideoModeActive = true, isVideoViewerVisible = true, hasVideo = true, presentationAllowed = false))
        assertFalse(shouldShowPlayerLoadingScreen(isPlaybackLoading = true, hasVisibleVideoTransitionIndicator = true))
        assertTrue(shouldShowPlayerLoadingScreen(isPlaybackLoading = true, hasVisibleVideoTransitionIndicator = false))
    }

    @Test
    fun sabrAutoQualityDropsOnStallAndUpswitchesWithHeadroom() {
        val variants =
            listOf(360 to 400_000, 480 to 800_000, 720 to 1_500_000, 1080 to 3_000_000).map { (height, bitrate) ->
                SabrVideoVariant(SabrFormatId(height), height * 16 / 9, height, bitrate, "video/mp4", null)
            }

        assertEquals(
            360,
            nextAutoSabrVideoVariant(variants, currentHeight = 720, maxHeight = 1080, bandwidthBps = 50_000_000L, audioBitrate = 128_000, stalled = true, allowUpswitch = true)?.height,
        )
        assertNull(
            nextAutoSabrVideoVariant(variants, currentHeight = 480, maxHeight = 720, bandwidthBps = 2_000_000L, audioBitrate = 128_000, stalled = false, allowUpswitch = true),
        )
        assertEquals(
            720,
            nextAutoSabrVideoVariant(variants, currentHeight = 480, maxHeight = 720, bandwidthBps = 3_000_000L, audioBitrate = 128_000, stalled = false, allowUpswitch = true)?.height,
        )
    }

    @Test
    fun originalAudioReturnedOnlyForMatchingCurrentVideo() {
        val original = MediaMetadata(id = "audio-1", title = "Song", artists = emptyList(), duration = 180)
        assertEquals(original, originalAudioForVideo("video-1", "video-1", original))
        assertNull(originalAudioForVideo("video-2", "video-1", original))
    }
}

