package com.anitail.shared.ui.menu

import com.anitail.shared.api.playlistSongs
import com.anitail.shared.model.AlbumItem
import com.anitail.shared.model.ArtistItem
import com.anitail.shared.model.EpisodeItem
import com.anitail.shared.model.PlaylistItem
import com.anitail.shared.model.PodcastItem
import com.anitail.shared.model.SongItem
import com.anitail.shared.model.YTItem
import com.anitail.shared.model.toSongItem
import com.anitail.shared.state.GlobalYouTubeRepository

suspend fun loadMediaItemSongs(item: YTItem): List<SongItem> {
    val repository = GlobalYouTubeRepository.instance
    return when (item) {
        is SongItem -> {
            listOf(item)
        }

        is EpisodeItem -> {
            listOf(item.toSongItem())
        }

        is AlbumItem -> {
            repository.getAlbum(item.id)?.songs.orEmpty()
        }

        is ArtistItem -> {
            repository.getArtistSongs(item.id)
        }

        is PlaylistItem -> {
            repository.getPlaylist(item.id).playlistSongs()
        }

        is PodcastItem -> {
            // Every page, so playback covers the whole podcast and can start from its first episode.
            buildList {
                repository.getPodcastPages(item.id).collect { page -> page.episodes.mapTo(this) { it.toSongItem(page.podcast.title) } }
            }
        }

        else -> {
            emptyList()
        }
    }
}
