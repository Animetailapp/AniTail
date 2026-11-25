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
    val baseComparator: Comparator<SongEntity> = when (sortType) {
        SongSortType.CREATE_DATE -> compareBy { songCreateDate(it) }
        SongSortType.NAME -> compareBy(collator) { it.title }
        SongSortType.ARTIST -> compareBy(collator) { it.artistName.orEmpty() }
        SongSortType.PLAY_TIME -> compareBy { it.totalPlayTime }
    }
    return songs.sortedWith(if (descending) baseComparator.reversed() else baseComparator)
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
    val baseComparator: Comparator<PlaylistEntity> = when (sortType) {
        PlaylistSortType.CREATE_DATE -> compareBy { it.createdAt ?: LocalDateTime.MIN }
        PlaylistSortType.NAME -> compareBy(collator) { it.name }
        PlaylistSortType.SONG_COUNT -> compareBy { songCounts[it.id] ?: 0 }
        PlaylistSortType.LAST_UPDATED -> compareBy { it.lastUpdateTime ?: LocalDateTime.MIN }
    }
    return playlists.sortedWith(if (descending) baseComparator.reversed() else baseComparator)
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
    val baseComparator: Comparator<AlbumEntity> = when (sortType) {
        AlbumSortType.CREATE_DATE -> compareBy { it.inLibrary ?: it.bookmarkedAt ?: LocalDateTime.MIN }
        AlbumSortType.NAME -> compareBy(collator) { it.title }
        AlbumSortType.ARTIST -> compareBy(collator) { artistNames[it.id].orEmpty() }
        AlbumSortType.YEAR -> compareBy { it.year ?: 0 }
        AlbumSortType.SONG_COUNT -> compareBy { it.songCount }
        AlbumSortType.LENGTH -> compareBy { it.duration }
        AlbumSortType.PLAY_TIME -> compareBy { playTimeByAlbum[it.id] ?: 0L }
    }
    return albums.sortedWith(if (descending) baseComparator.reversed() else baseComparator)
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
    val baseComparator: Comparator<ArtistEntity> = when (sortType) {
        ArtistSortType.CREATE_DATE -> compareBy { it.bookmarkedAt ?: LocalDateTime.MIN }
        ArtistSortType.NAME -> compareBy(collator) { it.name }
        ArtistSortType.SONG_COUNT -> compareBy { songCountByArtist[it.id] ?: 0 }
        ArtistSortType.PLAY_TIME -> compareBy { playTimeByArtist[it.id] ?: 0L }
    }
    return artists.sortedWith(if (descending) baseComparator.reversed() else baseComparator)
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
    val baseComparator: Comparator<LibraryMixEntry> = when (sortType) {
        MixSortType.CREATE_DATE -> compareBy { it.createDate ?: LocalDateTime.MIN }
        MixSortType.LAST_UPDATED -> compareBy { it.lastUpdated ?: LocalDateTime.MIN }
        MixSortType.NAME -> compareBy(collator) { it.name }
    }
    return entries.sortedWith(if (descending) baseComparator.reversed() else baseComparator)
}
