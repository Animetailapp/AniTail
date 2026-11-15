package com.anitail.shared.ui.statistics

import com.anitail.shared.db.HistoryAlbumStatistic
import com.anitail.shared.db.HistoryArtistStatistic
import com.anitail.shared.model.AlbumItem
import com.anitail.shared.model.ArtistItem
import com.anitail.shared.model.SearchFilter
import com.anitail.shared.state.GlobalYouTubeRepository

fun List<AlbumItem>.matchingStatisticsAlbum(
    name: String,
    artists: String,
): AlbumItem? =
    distinctBy(AlbumItem::id).singleOrNull { candidate ->
        candidate.title.trim().equals(name.trim(), ignoreCase = true) &&
            candidate.artists
                .joinToString(", ") { it.name }
                .trim()
                .equals(artists.trim(), ignoreCase = true)
    }

suspend fun HistoryAlbumStatistic.statisticsMediaItem(): AlbumItem? {
    val repository = GlobalYouTubeRepository.instance
    id?.let { return repository.getAlbum(it)?.album }
    return repository.search("$name $artists".trim()).filterIsInstance<AlbumItem>().matchingStatisticsAlbum(name, artists)
}

suspend fun HistoryArtistStatistic.statisticsMediaItem(): ArtistItem? {
    id?.let { return ArtistItem(it, name, thumbnailUrl) }
    return GlobalYouTubeRepository.instance
        .searchFiltered(name, SearchFilter.ARTIST)
        .items
        .filterIsInstance<ArtistItem>()
        .firstOrNull { it.title.equals(name, ignoreCase = true) }
}
