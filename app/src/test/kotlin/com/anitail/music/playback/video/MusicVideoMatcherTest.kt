package com.anitail.music.playback.video

import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicVideoMatcherTest {
    private val artist = Artist(name = "The Midnight", id = "artist-1")

    private fun track(
        id: String = "track-1",
        title: String = "Sunset",
        duration: Int = 240,
        musicVideoType: String? = "MUSIC_VIDEO_TYPE_ATV",
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(artist),
        album = null,
        duration = duration,
        thumbnail = "",
        explicit = false,
        musicVideoType = musicVideoType,
    )

    @Test
    fun prefersOfficialVideoAndRejectsCoversOrRemixes() {
        val source = track()
        val remix = track(id = "v-remix", title = "Sunset (Synth Remix)", duration = 242, musicVideoType = "MUSIC_VIDEO_TYPE_UGC")
        val cover = track(id = "v-cover", title = "Sunset (Acoustic Cover)", duration = 240, musicVideoType = "MUSIC_VIDEO_TYPE_UGC")
        val fanUgc = track(id = "v-ugc", title = "The Midnight - Sunset", duration = 241, musicVideoType = "MUSIC_VIDEO_TYPE_UGC")
        val official = track(id = "v-omv", title = "The Midnight - Sunset (Official Music Video)", duration = 248, musicVideoType = "MUSIC_VIDEO_TYPE_OMV")

        val matched = MusicVideoMatcher.findBestMatch(source, listOf(remix, cover, fanUgc, official))
        assertEquals("v-omv", matched?.id)
    }

    @Test
    fun rejectsDisqualifiedVariantsAndExcessiveDurationMismatch() {
        val source = track(duration = 180)
        val slowed = track(id = "v-slow", title = "Sunset (Slowed + Reverb)", duration = 210, musicVideoType = "MUSIC_VIDEO_TYPE_UGC")
        val tooLong = track(id = "v-long", title = "Sunset (Official Video)", duration = 900, musicVideoType = "MUSIC_VIDEO_TYPE_OMV")

        assertNull(MusicVideoMatcher.findBestMatch(source, listOf(slowed, tooLong)))
    }

    @Test
    fun reverseLookupFindsMatchingAudioTrackForVideo() {
        val video = track(id = "v-omv", title = "The Midnight - Sunset (Official 4K Video)", duration = 250, musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
        val live = track(id = "t-live", title = "Sunset", duration = 255, musicVideoType = "MUSIC_VIDEO_TYPE_LIVE")
        val audio = track(id = "t-atv", title = "Sunset", duration = 240, musicVideoType = "MUSIC_VIDEO_TYPE_ATV")

        val matched = MusicVideoMatcher.findBestTrackMatch(video, listOf(video, live, audio))
        assertEquals("t-atv", matched?.id)
    }
}

