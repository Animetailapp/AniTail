package com.anitail.shared.api

class PlaylistNotFoundException(
    val playlistId: String,
) : Exception()
