package com.anitail.desktop.ui.screen

enum class HomeSongMenuActionId {
    START_RADIO,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    ADD_TO_PLAYLIST,
    DOWNLOAD,
    TOGGLE_LIBRARY,
    VIEW_ARTIST,
    VIEW_ALBUM,
    SHARE,
    DETAILS,
}

data class HomeSongMenuAvailability(
    val canStartRadio: Boolean,
    val canAddToPlaylist: Boolean,
    val hasArtist: Boolean,
    val hasAlbum: Boolean,
)

fun buildHomeSongMenuOrder(availability: HomeSongMenuAvailability): List<HomeSongMenuActionId> {
    val order = mutableListOf<HomeSongMenuActionId>()
    if (availability.canStartRadio) {
        order += HomeSongMenuActionId.START_RADIO
    }
    order += HomeSongMenuActionId.PLAY_NEXT
    order += HomeSongMenuActionId.ADD_TO_QUEUE
    if (availability.canAddToPlaylist) {
        order += HomeSongMenuActionId.ADD_TO_PLAYLIST
    }
    order += HomeSongMenuActionId.DOWNLOAD
    order += HomeSongMenuActionId.TOGGLE_LIBRARY
    if (availability.hasArtist) {
        order += HomeSongMenuActionId.VIEW_ARTIST
    }
    if (availability.hasAlbum) {
        order += HomeSongMenuActionId.VIEW_ALBUM
    }
    order += HomeSongMenuActionId.SHARE
    order += HomeSongMenuActionId.DETAILS
    return order
}
