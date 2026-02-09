package com.anitail.desktop.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSongMenuOrderTest {
    @Test
    fun returnsExactOrderWhenAllOptionsAvailable() {
        val result = buildHomeSongMenuOrder(
            HomeSongMenuAvailability(
                canStartRadio = true,
                canAddToPlaylist = true,
                hasArtist = true,
                hasAlbum = true,
            )
        )

        assertEquals(
            listOf(
                HomeSongMenuActionId.START_RADIO,
                HomeSongMenuActionId.PLAY_NEXT,
                HomeSongMenuActionId.ADD_TO_QUEUE,
                HomeSongMenuActionId.ADD_TO_PLAYLIST,
                HomeSongMenuActionId.DOWNLOAD,
                HomeSongMenuActionId.TOGGLE_LIBRARY,
                HomeSongMenuActionId.VIEW_ARTIST,
                HomeSongMenuActionId.VIEW_ALBUM,
                HomeSongMenuActionId.SHARE,
                HomeSongMenuActionId.DETAILS,
            ),
            result,
        )
    }

    @Test
    fun hidesArtistAndAlbumWhenMissing() {
        val result = buildHomeSongMenuOrder(
            HomeSongMenuAvailability(
                canStartRadio = true,
                canAddToPlaylist = true,
                hasArtist = false,
                hasAlbum = false,
            )
        )

        assertEquals(
            listOf(
                HomeSongMenuActionId.START_RADIO,
                HomeSongMenuActionId.PLAY_NEXT,
                HomeSongMenuActionId.ADD_TO_QUEUE,
                HomeSongMenuActionId.ADD_TO_PLAYLIST,
                HomeSongMenuActionId.DOWNLOAD,
                HomeSongMenuActionId.TOGGLE_LIBRARY,
                HomeSongMenuActionId.SHARE,
                HomeSongMenuActionId.DETAILS,
            ),
            result,
        )
    }

    @Test
    fun hidesRadioAndPlaylistWhenUnavailable() {
        val result = buildHomeSongMenuOrder(
            HomeSongMenuAvailability(
                canStartRadio = false,
                canAddToPlaylist = false,
                hasArtist = true,
                hasAlbum = true,
            )
        )

        assertEquals(
            listOf(
                HomeSongMenuActionId.PLAY_NEXT,
                HomeSongMenuActionId.ADD_TO_QUEUE,
                HomeSongMenuActionId.DOWNLOAD,
                HomeSongMenuActionId.TOGGLE_LIBRARY,
                HomeSongMenuActionId.VIEW_ARTIST,
                HomeSongMenuActionId.VIEW_ALBUM,
                HomeSongMenuActionId.SHARE,
                HomeSongMenuActionId.DETAILS,
            ),
            result,
        )
    }
}
