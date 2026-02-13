package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.PlaylistEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedPlaylistFilterTest {
    @Test
    fun filterCachedPlaylistsWhenHidden() {
        val cached = PlaylistEntity(id = "p1", name = "En cache")
        val normal = PlaylistEntity(id = "p2", name = "Favoritas")

        val result = filterCachedPlaylists(
            playlists = listOf(cached, normal),
            showCached = false,
        )

        assertEquals(listOf("p2"), result.map { it.id })
    }

    @Test
    fun keepCachedPlaylistsWhenShown() {
        val cached = PlaylistEntity(id = "p1", name = "Cached")
        val normal = PlaylistEntity(id = "p2", name = "Mixes")

        val result = filterCachedPlaylists(
            playlists = listOf(cached, normal),
            showCached = true,
        )

        assertEquals(listOf("p1", "p2"), result.map { it.id })
    }

    @Test
    fun filterOutSpecialRemotePlaylists() {
        val likedMusic = PlaylistEntity(id = "LM", name = "Liked Music", browseId = "LM")
        val other = PlaylistEntity(id = "p2", name = "Mixes")

        val result = filterLibraryPlaylists(
            playlists = listOf(likedMusic, other),
            showCached = true,
        )

        assertEquals(listOf("p2"), result.map { it.id })
    }
}
