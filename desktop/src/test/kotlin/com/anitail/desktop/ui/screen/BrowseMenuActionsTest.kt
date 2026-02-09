package com.anitail.desktop.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseMenuActionsTest {
    @Test
    fun buildBrowseAlbumMenuActionsMatchesAndroidOrder() {
        val calls = mutableListOf<String>()

        val actions = buildBrowseAlbumMenuActions(
            hasArtists = true,
            downloadLabel = "Descargar",
            downloadEnabled = true,
            onStartRadio = { calls.add("radio") },
            onPlayNext = { calls.add("next") },
            onAddToQueue = { calls.add("queue") },
            onAddToPlaylist = { calls.add("playlist") },
            onDownload = { calls.add("download") },
            onOpenArtist = { calls.add("artist") },
            onShare = { calls.add("share") },
        )

        assertEquals(
            listOf(
                "Iniciar radio",
                "Reproducir siguiente",
                "Agregar a la cola",
                "Agregar a playlist",
                "Descargar",
                "Ir al artista",
                "Compartir",
            ),
            actions.map { it.label },
        )

        actions.forEach { it.onClick() }
        assertEquals(
            listOf("radio", "next", "queue", "playlist", "download", "artist", "share"),
            calls,
        )
    }

    @Test
    fun buildBrowseAlbumMenuActionsOmitsArtistWhenMissing() {
        val actions = buildBrowseAlbumMenuActions(
            hasArtists = false,
            downloadLabel = "Descargar",
            downloadEnabled = true,
            onStartRadio = {},
            onPlayNext = {},
            onAddToQueue = {},
            onAddToPlaylist = {},
            onDownload = {},
            onOpenArtist = {},
            onShare = {},
        )

        assertEquals(
            listOf(
                "Iniciar radio",
                "Reproducir siguiente",
                "Agregar a la cola",
                "Agregar a playlist",
                "Descargar",
                "Compartir",
            ),
            actions.map { it.label },
        )
    }

    @Test
    fun buildBrowsePlaylistMenuActionsMatchesAndroidOrder() {
        val calls = mutableListOf<String>()

        val actions = buildBrowsePlaylistMenuActions(
            canPlay = true,
            canShuffle = true,
            canRadio = true,
            showDownload = true,
            downloadLabel = "Descargar",
            downloadEnabled = true,
            onPlay = { calls.add("play") },
            onShuffle = { calls.add("shuffle") },
            onStartRadio = { calls.add("radio") },
            onPlayNext = { calls.add("next") },
            onAddToQueue = { calls.add("queue") },
            onAddToPlaylist = { calls.add("playlist") },
            onDownload = { calls.add("download") },
            onShare = { calls.add("share") },
        )

        assertEquals(
            listOf(
                "Reproducir",
                "Aleatorio",
                "Iniciar radio",
                "Reproducir siguiente",
                "Agregar a la cola",
                "Agregar a playlist",
                "Descargar",
                "Compartir",
            ),
            actions.map { it.label },
        )

        actions.forEach { it.onClick() }
        assertEquals(
            listOf("play", "shuffle", "radio", "next", "queue", "playlist", "download", "share"),
            calls,
        )
    }

    @Test
    fun buildBrowsePlaylistMenuActionsOmitsDownloadWhenDisabled() {
        val actions = buildBrowsePlaylistMenuActions(
            canPlay = true,
            canShuffle = true,
            canRadio = true,
            showDownload = false,
            downloadLabel = "Descargar",
            downloadEnabled = false,
            onPlay = {},
            onShuffle = {},
            onStartRadio = {},
            onPlayNext = {},
            onAddToQueue = {},
            onAddToPlaylist = {},
            onDownload = {},
            onShare = {},
        )

        assertEquals(
            listOf(
                "Reproducir",
                "Aleatorio",
                "Iniciar radio",
                "Reproducir siguiente",
                "Agregar a la cola",
                "Agregar a playlist",
                "Compartir",
            ),
            actions.map { it.label },
        )
    }

    @Test
    fun buildBrowseArtistMenuActionsMatchesAndroidOrder() {
        val calls = mutableListOf<String>()

        val actions = buildBrowseArtistMenuActions(
            isSubscribed = false,
            onToggleSubscribe = { calls.add("subscribe") },
        )

        assertEquals(
            listOf("Suscribirse"),
            actions.map { it.label },
        )

        actions.forEach { it.onClick() }
        assertEquals(listOf("subscribe"), calls)
    }
}
