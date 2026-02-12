package com.anitail.desktop.home

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.db.entities.RelatedSongMap
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import java.time.LocalDateTime

sealed interface HomeListenItem {
    val id: String
}

data class HomeListenSong(val song: SongEntity) : HomeListenItem {
    override val id: String
        get() = song.id
}

data class HomeListenAlbum(val album: AlbumEntity) : HomeListenItem {
    override val id: String
        get() = album.id
}

data class HomeListenArtist(val artist: ArtistEntity) : HomeListenItem {
    override val id: String
        get() = artist.id
}

data class HomeRecommendations(
    val quickPicks: List<SongEntity>,
    val keepListening: List<HomeListenItem>,
    val forgottenFavorites: List<SongEntity>,
)

fun buildHomeRecommendations(
    songs: List<SongEntity>,
    albums: List<AlbumEntity>,
    artists: List<ArtistEntity>,
    songArtistMaps: List<SongArtistMap>,
    relatedSongMaps: List<RelatedSongMap>,
    events: List<EventEntity>,
    now: LocalDateTime = LocalDateTime.now(),
    hideExplicit: Boolean = false,
): HomeRecommendations {
    if (songs.isEmpty()) {
        return HomeRecommendations(emptyList(), emptyList(), emptyList())
    }

    val filteredSongs = if (hideExplicit) songs.filterNot { it.explicit } else songs
    val songsById = filteredSongs.associateBy { it.id }
    val albumsById = albums.associateBy { it.id }
    val artistsById = artists.associateBy { it.id }

    val quickPicks = buildQuickPicks(filteredSongs, songsById, relatedSongMaps, events, now)
    val keepListening = buildKeepListening(
        songsById = songsById,
        albumsById = albumsById,
        artistsById = artistsById,
        songArtistMaps = songArtistMaps,
        events = events,
        now = now,
    )
    val forgottenFavorites = buildForgottenFavorites(songsById, events, now)

    return HomeRecommendations(
        quickPicks = quickPicks,
        keepListening = keepListening,
        forgottenFavorites = forgottenFavorites,
    )
}

private fun buildQuickPicks(
    songs: List<SongEntity>,
    songsById: Map<String, SongEntity>,
    relatedSongMaps: List<RelatedSongMap>,
    events: List<EventEntity>,
    now: LocalDateTime,
): List<SongEntity> {
    if (relatedSongMaps.isEmpty() || songsById.isEmpty()) return emptyList()

    val recentIds = events.asReversed().asSequence()
        .map { it.songId }
        .distinct()
        .take(5)
        .toList()

    val weekCutoff = now.minusDays(7)
    val weekTopIds = events.takeLast(1000)
        .filter { it.timestamp.isAfter(weekCutoff) }
        .groupBy { it.songId }
        .mapValues { entry -> entry.value.sumOf { it.playTime } }
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(5)

    val totalTopIds = songs
        .sortedByDescending { it.totalPlayTime }
        .map { it.id }
        .take(10)

    val seedIds = (recentIds + weekTopIds + totalTopIds).toSet()
    if (seedIds.isEmpty()) return emptyList()

    val relatedCounts = relatedSongMaps
        .filter { it.songId in seedIds }
        .groupingBy { it.relatedSongId }
        .eachCount()

    return relatedCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .mapNotNull { songsById[it.key] }
}

private fun buildKeepListening(
    songsById: Map<String, SongEntity>,
    albumsById: Map<String, AlbumEntity>,
    artistsById: Map<String, ArtistEntity>,
    songArtistMaps: List<SongArtistMap>,
    events: List<EventEntity>,
    now: LocalDateTime,
): List<HomeListenItem> {
    val cutoff = now.minusDays(14)
    val recentEvents = events.takeLast(1000).filter { it.timestamp.isAfter(cutoff) }

    val songIds = recentEvents
        .groupBy { it.songId }
        .mapValues { entry -> entry.value.sumOf { it.playTime } }
        .entries
        .sortedByDescending { it.value }
        .drop(5)
        .take(15)
        .map { it.key }

    val songs = songIds
        .mapNotNull { songsById[it] }
        .take(10)
        .map { HomeListenSong(it) }

    val albumIds = recentEvents
        .mapNotNull { event -> songsById[event.songId]?.albumId?.let { it to event.playTime } }
        .groupBy { it.first }
        .mapValues { entry -> entry.value.sumOf { it.second } }
        .entries
        .sortedByDescending { it.value }
        .drop(2)
        .take(8)
        .map { it.key }

    val albums = albumIds
        .mapNotNull { albumsById[it] }
        .filter { it.thumbnailUrl != null }
        .take(5)
        .map { HomeListenAlbum(it) }

    val artistIds = recentEvents
        .mapNotNull { event ->
            val artistId = primaryArtistIdForSong(event.songId, songArtistMaps)
            artistId?.let { it to event.playTime }
        }
        .groupBy { it.first }
        .mapValues { entry -> entry.value.sumOf { it.second } }
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    val artists = artistIds
        .mapNotNull { artistsById[it] }
        .filter { it.isYouTubeArtist && it.thumbnailUrl != null }
        .take(5)
        .map { HomeListenArtist(it) }

    return songs + albums + artists
}

private fun buildForgottenFavorites(
    songsById: Map<String, SongEntity>,
    events: List<EventEntity>,
    now: LocalDateTime,
): List<SongEntity> {
    if (events.isEmpty()) return emptyList()

    val recentCutoff = now.minusDays(30)
    val recentPlayTime = events.takeLast(2000)
        .filter { it.timestamp.isAfter(recentCutoff) }
        .groupBy { it.songId }
        .mapValues { entry -> entry.value.sumOf { it.playTime } }

    val oldPlayTime = events
        .filter { it.timestamp.isBefore(recentCutoff) }
        .groupBy { it.songId }
        .mapValues { entry -> entry.value.sumOf { it.playTime } }

    val forgottenIds = oldPlayTime.entries
        .filter { (songId, oldTime) ->
            val recentTime = recentPlayTime[songId] ?: 0L
            0.2 * oldTime > recentTime
        }
        .sortedByDescending { it.value }
        .map { it.key }
        .take(20)

    return forgottenIds.mapNotNull { songsById[it] }
}
