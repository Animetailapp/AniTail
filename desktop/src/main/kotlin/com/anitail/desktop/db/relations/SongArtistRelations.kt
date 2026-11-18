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

fun indexArtistIdsBySongId(maps: List<SongArtistMap>): Map<String, List<String>> {
    if (maps.isEmpty()) return emptyMap()
    return maps
        .groupBy { it.songId }
        .mapValues { (_, list) ->
            if (list.size == 1) {
                listOf(list[0].artistId)
            } else {
                list.sortedBy { it.position }.map { it.artistId }
            }
        }
}

fun indexPrimaryArtistIdBySongId(maps: List<SongArtistMap>): Map<String, String> {
    if (maps.isEmpty()) return emptyMap()
    val bestPositionBySong = HashMap<String, Int>(maps.size)
    val artistBySong = HashMap<String, String>(maps.size)
    for (map in maps) {
        val currentBest = bestPositionBySong[map.songId]
        if (currentBest == null || map.position < currentBest) {
            bestPositionBySong[map.songId] = map.position
            artistBySong[map.songId] = map.artistId
        }
    }
    return artistBySong
}
