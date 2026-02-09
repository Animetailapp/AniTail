package com.anitail.desktop.ui.screen

import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreMenuActionsTest {
    @Test
    fun buildExploreAlbumMenuActionsMatchesAndroidOrderAndCallbacks() {
        val album = AlbumItem(
            browseId = "browse123",
            playlistId = "playlist123",
            title = "Nuevo Album",
            artists = listOf(Artist(name = "Artista", id = "artist123")),
            thumbnail = "https://example.com/cover.jpg",
        )
        val opened = mutableListOf<Pair<String, String?>>()
        val copied = mutableListOf<String>()

        val actions = buildExploreAlbumMenuActions(
            album = album,
            onOpenAlbum = { id, title -> opened.add(id to title) },
            copyToClipboard = { copied.add(it) },
        )

        assertEquals(
            listOf("Abrir", "Copiar enlace", "Compartir"),
            actions.map { it.label },
        )

        actions[0].onClick()
        actions[1].onClick()
        actions[2].onClick()

        assertEquals(listOf(album.browseId to album.title), opened)
        assertEquals(listOf(album.shareLink, album.shareLink), copied)
    }
}
