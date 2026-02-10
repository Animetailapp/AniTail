package com.anitail.desktop.ui.screen

import com.anitail.desktop.i18n.testStringResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreMenuActionsTest {
    @Test
    fun buildExploreAlbumMenuActionsMatchesAndroidOrderAndCallbacks() {
        val calls = mutableListOf<String>()

        val actions = buildExploreAlbumMenuActions(
            strings = testStringResolver(),
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
}
