package com.anitail.shared.repository

import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.MusicRepository

class InnertubeMusicRepository : MusicRepository {
    override suspend fun search(query: String): List<LibraryItem> {
        if (query.isBlank()) return emptyList()
        val result =
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrElse { return emptyList() }
        return result.items.mapNotNull { item ->
            val song = item as? SongItem ?: return@mapNotNull null
            LibraryItem(
                id = song.id,
                title = song.title,
                artist = song.artists.joinToString { it.name },
                artworkUrl = song.thumbnail,
                playbackUrl = "https://music.youtube.com/watch?v=${song.id}",
            )
        }
    }

    override fun initialLibrary(): List<LibraryItem> = emptyList()
}
