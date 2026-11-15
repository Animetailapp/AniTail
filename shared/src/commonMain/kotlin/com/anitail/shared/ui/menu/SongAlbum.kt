package com.anitail.shared.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anitail.shared.model.SongItem
import com.anitail.shared.state.GlobalYouTubeRepository

@Composable
fun rememberSongWithAlbum(song: SongItem): SongItem {
    var album by remember(song.id, song.album) { mutableStateOf(song.album) }
    LaunchedEffect(song.id, song.album) {
        album = GlobalYouTubeRepository.instance.resolveSongAlbum(song)
    }
    return if (album == song.album) song else song.copy(album = album)
}
