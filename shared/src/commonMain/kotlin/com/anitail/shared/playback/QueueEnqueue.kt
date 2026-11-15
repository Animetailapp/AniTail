package com.anitail.shared.playback

import com.anitail.shared.model.SongItem

fun enqueueOrStartQueue(
    songs: List<SongItem>,
    currentSong: SongItem?,
    preventDuplicates: Boolean,
    startQueue: (List<SongItem>, Boolean) -> Unit,
    enqueue: (List<SongItem>) -> Unit,
) {
    if (songs.isEmpty()) return
    if (currentSong == null) {
        startQueue(if (preventDuplicates) songs.distinctBy { it.id } else songs, false)
    } else {
        enqueue(songs)
    }
}
