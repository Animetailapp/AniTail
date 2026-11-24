package com.anitail.music.ui.screens

import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.EpisodeItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSectionPolicyTest {

    @Test
    fun `classifyHomeDynamicSection returns null for empty list`() {
        assertNull(classifyHomeDynamicSection(emptyList()))
    }

    @Test
    fun `classifyHomeDynamicSection classifies episode and wide video shelves as VIDEO_CAROUSEL`() {
        val videoSong = SongItem(
            id = "vid1",
            title = "Live Performance",
            artists = emptyList(),
            thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
        )
        val episode = EpisodeItem(
            id = "ep1",
            title = "Episode 1",
            author = null,
            thumbnail = "https://lh3.googleusercontent.com/test=w544-h544",
        )

        assertEquals(
            HomeDynamicShelfKind.VIDEO_CAROUSEL,
            classifyHomeDynamicSection(listOf(videoSong)),
        )
        assertEquals(
            HomeDynamicShelfKind.VIDEO_CAROUSEL,
            classifyHomeDynamicSection(listOf(episode)),
        )
    }

    @Test
    fun `classifyHomeDynamicSection classifies square music and mixed shelves as MULTI_BROWSE_CAROUSEL`() {
        val squareSong = SongItem(
            id = "song1",
            title = "Track",
            artists = emptyList(),
            thumbnail = "https://lh3.googleusercontent.com/art=w544-h544",
        )
        val album = AlbumItem(
            browseId = "MPREb_1",
            playlistId = "OLAK_1",
            title = "Album",
            artists = emptyList(),
            thumbnail = "https://lh3.googleusercontent.com/alb=w544-h544",
        )
        val artist = ArtistItem(
            id = "UC_1",
            title = "Artist",
            thumbnail = "https://lh3.googleusercontent.com/art=w544-h544",
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        val playlist = PlaylistItem(
            id = "VLPL1",
            title = "Playlist",
            author = null,
            songCountText = "20 songs",
            thumbnail = "https://lh3.googleusercontent.com/pl=w544-h544",
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        assertEquals(
            HomeDynamicShelfKind.MULTI_BROWSE_CAROUSEL,
            classifyHomeDynamicSection(listOf(squareSong, album, artist, playlist)),
        )
    }

    @Test
    fun `resolveHomeSectionRoute maps known browse IDs accurately`() {
        assertEquals("mood_and_genres", resolveHomeSectionRoute("FEmusic_moods_and_genres"))
        assertEquals("charts_screen", resolveHomeSectionRoute("FEmusic_charts"))
        assertEquals("youtube_browse/FEmusic_new_episodes", resolveHomeSectionRoute("FEmusic_new_episodes"))
        assertEquals("online_podcast/MPSP1234", resolveHomeSectionRoute("MPSP1234"))
        assertEquals("browse/VLPL_custom", resolveHomeSectionRoute("VLPL_custom"))
    }

    @Test
    fun `computeHomePagerWidthDp scales cleanly for compact and tablet widths`() {
        // compact: (392 - 32) * 0.90 = 324
        assertEquals(324, computeHomePagerWidthDp(392))
        // tablet: (800 - 32) * 0.46 = 353
        assertEquals(353, computeHomePagerWidthDp(800))
    }
}

