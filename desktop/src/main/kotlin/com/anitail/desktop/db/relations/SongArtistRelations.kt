package com.anitail.desktop.db.relations

import com.anitail.desktop.db.entities.SongArtistMap

fun artistIdsForSong(songId: String, maps: List<SongArtistMap>): List<String> {
    return maps
        .asSequence()
        .filter { it.songId == songId }
        .sortedBy { it.position }
        .map { it.artistId }
        .toList()
}

fun primaryArtistIdForSong(songId: String, maps: List<SongArtistMap>): String? {
    return artistIdsForSong(songId, maps).firstOrNull()
}

fun songHasArtist(songId: String, artistId: String, maps: List<SongArtistMap>): Boolean {
    return maps.any { it.songId == songId && it.artistId == artistId }
}
