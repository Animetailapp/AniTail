package com.anitail.music.ui.screens

import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.anitail.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.anitail.innertube.pages.ChartsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreSectionPolicyTest {

    private fun song(id: String, musicVideoType: String? = MUSIC_VIDEO_TYPE_ATV) = SongItem(
        id = id,
        title = "Song $id",
        artists = listOf(Artist("Artist", "UC1")),
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        musicVideoType = musicVideoType,
    )

    private fun playlist(id: String, title: String) = PlaylistItem(
        id = id,
        title = title,
        author = Artist("YouTube Music", null),
        songCountText = "100 songs",
        thumbnail = "https://lh3.googleusercontent.com/$id",
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun artist(id: String, name: String) = ArtistItem(
        id = id,
        title = name,
        thumbnail = "https://lh3.googleusercontent.com/$id",
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    @Test
    fun `empty chart section returns null so no blank header or gap renders`() {
        val empty = ChartsPage.ChartSection(
            title = "Classifiche dei video",
            items = emptyList(),
            chartType = ChartsPage.ChartType.TOP,
        )
        assertNull(classifyChartSection(empty))
    }

    @Test
    fun `artist and playlist sections classify as multi browse carousels`() {
        val topArtists = ChartsPage.ChartSection(
            title = "Artisti più ascoltati",
            items = listOf(artist("UC1", "Alfa"), artist("UC2", "Pinguini")),
            chartType = ChartsPage.ChartType.TOP,
        )
        val videoPlaylists = ChartsPage.ChartSection(
            title = "Classifiche dei video",
            items = listOf(playlist("PL1", "Top Music Videos Italy")),
            chartType = ChartsPage.ChartType.TOP,
        )

        assertEquals(ChartShelfKind.MULTI_BROWSE_CAROUSEL, classifyChartSection(topArtists))
        assertEquals(ChartShelfKind.MULTI_BROWSE_CAROUSEL, classifyChartSection(videoPlaylists))
    }

    @Test
    fun `video song sections classify as video carousel across locales`() {
        val omvVideos = ChartsPage.ChartSection(
            title = "I video musicali del momento",
            items = listOf(song("V1", musicVideoType = MUSIC_VIDEO_TYPE_OMV)),
            chartType = ChartsPage.ChartType.TOP,
        )
        val untypedItalianVideoChart = ChartsPage.ChartSection(
            title = "Classifiche dei video",
            items = listOf(song("V2", musicVideoType = null)),
            chartType = ChartsPage.ChartType.TOP,
        )

        assertEquals(ChartShelfKind.VIDEO_CAROUSEL, classifyChartSection(omvVideos))
        assertEquals(ChartShelfKind.VIDEO_CAROUSEL, classifyChartSection(untypedItalianVideoChart))
    }

    @Test
    fun `audio tracks and album carousels classify accurately`() {
        val trendingSongs = ChartsPage.ChartSection(
            title = "Trending",
            items = listOf(song("S1"), song("S2")),
            chartType = ChartsPage.ChartType.TRENDING,
        )
        val albumCharts = ChartsPage.ChartSection(
            title = "Top albums",
            items = listOf(
                AlbumItem(
                    browseId = "MPRE1",
                    playlistId = "OLAK1",
                    title = "Album",
                    artists = listOf(Artist("Artist", "UC1")),
                    thumbnail = "https://lh3.googleusercontent.com/alb",
                ),
            ),
            chartType = ChartsPage.ChartType.TOP,
        )

        assertEquals(ChartShelfKind.SONG_PAGER, classifyChartSection(trendingSongs))
        assertEquals(ChartShelfKind.MULTI_BROWSE_CAROUSEL, classifyChartSection(albumCharts))
    }

    @Test
    fun `filterExploreVisibility shows requested section or all when null`() {
        val all = filterExploreVisibility(null)
        assertTrue(all.showCharts && all.showNewReleases && all.showMoodsAndGenres)

        val chartsOnly = filterExploreVisibility(ExploreFilterChip.CHARTS)
        assertTrue(chartsOnly.showCharts)
        assertFalse(chartsOnly.showNewReleases || chartsOnly.showMoodsAndGenres)

        val releasesOnly = filterExploreVisibility(ExploreFilterChip.NEW_RELEASES)
        assertTrue(releasesOnly.showNewReleases)
        assertFalse(releasesOnly.showCharts || releasesOnly.showMoodsAndGenres)

        val moodsOnly = filterExploreVisibility(ExploreFilterChip.MOODS_AND_GENRES)
        assertTrue(moodsOnly.showMoodsAndGenres)
        assertFalse(moodsOnly.showCharts || moodsOnly.showNewReleases)
    }
}

