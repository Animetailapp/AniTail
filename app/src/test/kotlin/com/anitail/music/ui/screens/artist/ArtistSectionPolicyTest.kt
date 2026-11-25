package com.anitail.music.ui.screens.artist

import com.anitail.innertube.models.Album
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.anitail.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.anitail.innertube.pages.ArtistPage
import com.anitail.innertube.pages.ArtistSection
import com.anitail.innertube.pages.LATEST_RELEASE_SECTION_KEY
import com.anitail.music.utils.formatArtistSubscriberCount
import com.anitail.music.utils.monthlyAudienceText
import com.anitail.music.utils.monthlyListenersCount
import com.anitail.music.utils.withoutPlaybackCountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSectionPolicyTest {

    private val mainArtist = ArtistItem(
        id = "UC123",
        title = "Example Artist",
        thumbnail = "https://lh3.googleusercontent.com/avatar=s544",
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun album(
        id: String,
        title: String,
        year: Int? = null,
        artists: List<Artist>? = listOf(Artist(name = mainArtist.title, id = mainArtist.id)),
    ) = AlbumItem(
        browseId = id,
        playlistId = "PL$id",
        title = title,
        artists = artists,
        year = year,
        thumbnail = "https://lh3.googleusercontent.com/$id=s544",
    )

    private fun song(
        id: String,
        title: String = "Track $id",
        album: Album? = Album(name = "Album", id = "A1"),
        musicVideoType: String? = MUSIC_VIDEO_TYPE_ATV,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = mainArtist.title, id = mainArtist.id)),
        album = album,
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        musicVideoType = musicVideoType,
    )

    @Test
    fun `artistSectionsWithLatestRelease prepends max-year album and drops duplicate sole-album shelf`() {
        val newestAlbum = album("ALB_NEW", "New Single", year = 2025)
        val olderAlbum = album("ALB_OLD", "Old Album", year = 2021)
        val featuredAlbum = album(
            id = "ALB_OTHER",
            title = "Collaboration",
            year = 2026,
            artists = listOf(Artist(name = "Other Artist", id = "UC999")),
        )

        val input = listOf(
            ArtistSection("Top tracks", listOf(song("S1")), BrowseEndpoint("VLOLAK")),
            ArtistSection("Latest release", listOf(newestAlbum), null),
            ArtistSection("Albums", listOf(olderAlbum), BrowseEndpoint("MPAD1")),
            ArtistSection("Featured on", listOf(featuredAlbum), null),
        )

        val result = ArtistPage.artistSectionsWithLatestRelease(mainArtist, input)

        assertEquals(LATEST_RELEASE_SECTION_KEY, result.first().title)
        assertEquals(listOf(newestAlbum), result.first().items)
        // Single-item shelf matching newestAlbum should be deduplicated
        assertFalse(result.drop(1).any { it.title == "Latest release" })
        // Multi-item or other sections preserved in order
        assertEquals(listOf(LATEST_RELEASE_SECTION_KEY, "Top tracks", "Albums", "Featured on"), result.map { it.title })
    }

    @Test
    fun `artistSectionsWithLatestRelease preserves tie order when years match`() {
        val first2025 = album("ALB_1", "First 2025", year = 2025)
        val second2025 = album("ALB_2", "Second 2025", year = 2025)

        val result = ArtistPage.artistSectionsWithLatestRelease(
            mainArtist,
            listOf(
                ArtistSection("Singles", listOf(first2025, second2025), null),
            ),
        )

        assertEquals(first2025, result.first().items.single())
    }

    @Test
    fun `classifyArtistSections identifies library, top tracks, and video shelves`() {
        val sections = listOf(
            ArtistSection("Songs", listOf(song("S1")), BrowseEndpoint("VLOLAK123")),
            ArtistSection("Videos", listOf(song("V1", album = null, musicVideoType = MUSIC_VIDEO_TYPE_OMV)), BrowseEndpoint("VLOLV")),
            ArtistSection("From your library", listOf(album("A1", "Saved Album", 2024)), BrowseEndpoint("MPLA123")),
        )

        val classification = classifyArtistSections(sections)

        assertEquals("From your library", classification.librarySection)
        assertEquals("Songs", classification.topTracksSection)
        assertEquals(setOf("Videos"), classification.videoSections)
    }

    @Test
    fun `isVideoShelf respects untyped allowance and ATV exclusions`() {
        val audioTracks = listOf(song("S1", musicVideoType = MUSIC_VIDEO_TYPE_ATV))
        val untypedTracks = listOf(song("S2", musicVideoType = null))
        val musicVideos = listOf(song("V1", musicVideoType = MUSIC_VIDEO_TYPE_OMV))

        assertFalse(audioTracks.isVideoShelf(allowUntypedSongs = true))
        assertFalse(untypedTracks.isVideoShelf(allowUntypedSongs = false))
        assertTrue(untypedTracks.isVideoShelf(allowUntypedSongs = true))
        assertTrue(musicVideos.isVideoShelf(allowUntypedSongs = false))
    }

    @Test
    fun `audience and subscriber formatters parse localized counts cleanly`() {
        assertEquals("14.2M", monthlyListenersCount("14.2M monthly listeners"))
        assertEquals("812 mil", monthlyListenersCount("812 mil oyentes mensuales"))
        assertEquals("14.2M monthly audience", monthlyAudienceText("14.2M monthly listeners", "monthly audience"))
        assertNull(monthlyListenersCount("no digits"))

        assertEquals("2.4M subscribers", formatArtistSubscriberCount("2.4M") { "$it subscribers" })
        assertEquals("2.4M iscritti", formatArtistSubscriberCount("2.4M iscritti") { "$it subscribers" })
        assertNull(formatArtistSubscriberCount("   ") { "$it subscribers" })

        assertEquals("123M", "123M plays".withoutPlaybackCountLabel())
        assertEquals("45 mil", "45 mil visualizaciones".withoutPlaybackCountLabel())
    }
}

