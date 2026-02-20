package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

fun filterSongs(
    songs: List<SongEntity>,
    filter: SongFilter,
    downloadedSongIds: Set<String>,
): List<SongEntity> {
    return when (filter) {
        SongFilter.LIKED -> songs.filter { it.liked }
        SongFilter.LIBRARY -> songs.filter { it.inLibrary != null }
        SongFilter.DOWNLOADED -> songs.filter { it.id in downloadedSongIds }
    }
}

fun sortSongs(
    songs: List<SongEntity>,
    sortType: SongSortType,
    descending: Boolean,
): List<SongEntity> {
    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        SongSortType.CREATE_DATE -> songs.sortedBy { songCreateDate(it) }
        SongSortType.NAME -> songs.sortedWith(compareBy(collator) { it.title })
        SongSortType.ARTIST -> songs.sortedWith(compareBy(collator) { it.artistName.orEmpty() })
        SongSortType.PLAY_TIME -> songs.sortedBy { it.totalPlayTime }
    }
    return if (descending) sorted.reversed() else sorted
}

private fun songCreateDate(song: SongEntity): LocalDateTime {
    return song.inLibrary ?: song.likedDate ?: song.dateModified ?: LocalDateTime.MIN
}

fun sortPlaylists(
    playlists: List<PlaylistEntity>,
    sortType: PlaylistSortType,
    descending: Boolean,
    songCounts: Map<String, Int>,
): List<PlaylistEntity> {
    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        PlaylistSortType.CREATE_DATE -> playlists.sortedBy { it.createdAt ?: LocalDateTime.MIN }
        PlaylistSortType.NAME -> playlists.sortedWith(compareBy(collator) { it.name })
        PlaylistSortType.SONG_COUNT -> playlists.sortedBy { songCounts[it.id] ?: 0 }
        PlaylistSortType.LAST_UPDATED -> playlists.sortedBy { it.lastUpdateTime ?: LocalDateTime.MIN }
    }
    return if (descending) sorted.reversed() else sorted
}

fun sortAlbums(
    albums: List<AlbumEntity>,
    sortType: AlbumSortType,
    descending: Boolean,
    artistNames: Map<String, String>,
    playTimeByAlbum: Map<String, Long>,
): List<AlbumEntity> {
    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        AlbumSortType.CREATE_DATE -> albums.sortedBy { it.inLibrary ?: it.bookmarkedAt ?: LocalDateTime.MIN }
        AlbumSortType.NAME -> albums.sortedWith(compareBy(collator) { it.title })
        AlbumSortType.ARTIST -> albums.sortedWith(compareBy(collator) { artistNames[it.id].orEmpty() })
        AlbumSortType.YEAR -> albums.sortedBy { it.year ?: 0 }
        AlbumSortType.SONG_COUNT -> albums.sortedBy { it.songCount }
        AlbumSortType.LENGTH -> albums.sortedBy { it.duration }
        AlbumSortType.PLAY_TIME -> albums.sortedBy { playTimeByAlbum[it.id] ?: 0L }
    }
    return if (descending) sorted.reversed() else sorted
}

fun sortArtists(
    artists: List<ArtistEntity>,
    sortType: ArtistSortType,
    descending: Boolean,
    songCountByArtist: Map<String, Int>,
    playTimeByArtist: Map<String, Long>,
): List<ArtistEntity> {
    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        ArtistSortType.CREATE_DATE -> artists.sortedBy { it.bookmarkedAt ?: LocalDateTime.MIN }
        ArtistSortType.NAME -> artists.sortedWith(compareBy(collator) { it.name })
        ArtistSortType.SONG_COUNT -> artists.sortedBy { songCountByArtist[it.id] ?: 0 }
        ArtistSortType.PLAY_TIME -> artists.sortedBy { playTimeByArtist[it.id] ?: 0L }
    }
    return if (descending) sorted.reversed() else sorted
}

sealed interface LibraryMixEntry {
    val id: String
    val name: String
    val createDate: LocalDateTime?
    val lastUpdated: LocalDateTime?
}

data class PlaylistMixEntry(val playlist: PlaylistEntity) : LibraryMixEntry {
    override val id: String = playlist.id
    override val name: String = playlist.name
    override val createDate: LocalDateTime? = playlist.createdAt
    override val lastUpdated: LocalDateTime? = playlist.lastUpdateTime
}

data class AlbumMixEntry(val album: AlbumEntity) : LibraryMixEntry {
    override val id: String = album.id
    override val name: String = album.title
    override val createDate: LocalDateTime? = album.inLibrary ?: album.bookmarkedAt
    override val lastUpdated: LocalDateTime? = album.lastUpdateTime
}

data class ArtistMixEntry(val artist: ArtistEntity) : LibraryMixEntry {
    override val id: String = artist.id
    override val name: String = artist.name
    override val createDate: LocalDateTime? = artist.bookmarkedAt
    override val lastUpdated: LocalDateTime? = artist.lastUpdateTime
}

fun sortMixEntries(
    entries: List<LibraryMixEntry>,
    sortType: MixSortType,
    descending: Boolean,
): List<LibraryMixEntry> {
    val collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }
    val sorted = when (sortType) {
        MixSortType.CREATE_DATE -> entries.sortedBy { it.createDate ?: LocalDateTime.MIN }
        MixSortType.LAST_UPDATED -> entries.sortedBy { it.lastUpdated ?: LocalDateTime.MIN }
        MixSortType.NAME -> entries.sortedWith(compareBy(collator) { it.name })
    }
    return if (descending) sorted.reversed() else sorted
}
