package com.anitail.music.utils

import com.anitail.music.ui.screens.artist.artistGalleryPhotos
import com.anitail.music.ui.screens.artist.artistPhotoIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LastFmArtistClientTest {

    @Test
    fun `catalogsMatch requires shared normalized catalog titles and ignores suffixes`() {
        val youTube = listOf("Blinding Lights", "Starboy (Deluxe Edition) - Single", "Save Your Tears")
        val matchingLastFm = listOf("Blinding Lights", "Save Your Tears [Explicit]", "After Hours")
        val differentArtist = listOf("Completely Unrelated Song", "Another Track", "Third Track")

        assertTrue(LastFmArtistClient.catalogsMatch(youTube, matchingLastFm))
        assertFalse(LastFmArtistClient.catalogsMatch(youTube, differentArtist))
        assertFalse(LastFmArtistClient.catalogsMatch(emptyList(), matchingLastFm))
    }

    @Test
    fun `extractGalleryPhotos rewrites to full resolution and skips placeholder hash`() {
        val html = """
            <ul>
              <li class="image-list-item">
                <a href="/music/Artist/+images/11112222333344445555666677778888">
                  <img src="https://lastfm.freetls.fastly.net/i/u/avatar170s/11112222333344445555666677778888.jpg" />
                </a>
              </li>
              <li class="image-list-item">
                <a href="/music/Artist/+images/placeholder">
                  <img src="https://lastfm.freetls.fastly.net/i/u/avatar170s/$LASTFM_PLACEHOLDER_HASH.jpg" />
                </a>
              </li>
              <li class="image-list-item">
                <a href="/music/Artist/+images/9999aaaabbbbccccddddeeeeffff0000">
                  <img src="https://lastfm.freetls.fastly.net/i/u/300x300/9999aaaabbbbccccddddeeeeffff0000.png" />
                </a>
              </li>
            </ul>
        """.trimIndent()

        val photos = LastFmArtistClient.extractGalleryPhotos(html)

        assertEquals(2, photos.size)
        assertTrue(photos.none { it.contains(LASTFM_PLACEHOLDER_HASH) })
        assertTrue(photos.all { it.contains("/ar0/") })
    }

    @Test
    fun `parsePhotoDetails extracts title, description, author profile, and upload date`() {
        val html = """
            <h3 class="gallery-image-title">Live at Tokyo &amp; Osaka</h3>
            <p class="gallery-image-description">Tour &ldquo;2025&rdquo;<br>Encore</p>
            <div class="gallery-image-uploaded-by">
                Uploaded by <a href="/user/music_fan">music_fan</a> on 14 Mar 2025
            </div>
        """.trimIndent()

        val details = parsePhotoDetails(html)
        assertNotNull(details)
        assertEquals("Live at Tokyo & Osaka", details?.title)
        assertEquals("Tour “2025” Encore", details?.description)
        assertEquals("music_fan", details?.authorName)
        assertEquals("https://www.last.fm/user/music_fan", details?.authorProfileUrl)
        assertEquals("14 Mar 2025", details?.uploadDate)
        assertEquals(LocalDate.of(2025, 3, 14), parsePhotoUploadDate(details!!.uploadDate!!))
    }

    @Test
    fun `artistGalleryPhotos falls back to thumbnail and assigns Lastfm page url`() {
        val fallback = artistGalleryPhotos("Daft Punk", "https://lh3.googleusercontent.com/abc", emptyList())
        assertEquals(1, fallback.size)
        assertEquals("https://lh3.googleusercontent.com/abc", fallback.first().url)

        val url = "https://lastfm.freetls.fastly.net/i/u/ar0/0123456789abcdef0123456789abcdef.jpg"
        val fromLastFm = artistGalleryPhotos("Daft Punk", null, listOf(ArtistPhoto(url)))
        assertEquals(
            "https://www.last.fm/music/Daft+Punk/+images/0123456789abcdef0123456789abcdef",
            fromLastFm.first().pageUrl,
        )

        assertEquals(0, artistPhotoIndex(fromLastFm, url, 3))
        assertEquals(0, artistPhotoIndex(fromLastFm, "https://missing.example/x.jpg", 3))
        assertNull(artistPhotoIndex(emptyList(), url, 0))
    }

    @Test
    fun `toLinkSegments turns urls and creative commons notices into clickable links`() {
        val bio = "Formed in Paris. See https://example.com/bio?a=1. Licensed under Creative Commons Attribution-ShareAlike License (https://creativecommons.org/licenses/by-sa/4.0/)."
        val segments = bio.toLinkSegments()

        val urlSegments = segments.filter { it.url != null }
        assertTrue(urlSegments.any { it.url == "https://example.com/bio?a=1" })
        assertTrue(urlSegments.any { it.url == "https://creativecommons.org/licenses/by-sa/4.0/" })
    }
}

