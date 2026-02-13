package com.anitail.desktop.ui.screen.library

import com.anitail.desktop.db.entities.PlaylistEntity
import java.text.Normalizer

internal fun filterCachedPlaylists(
    playlists: List<PlaylistEntity>,
    showCached: Boolean,
): List<PlaylistEntity> {
    if (showCached) return playlists
    return playlists.filterNot { isCachedName(it.name) }
}

internal fun filterLibraryPlaylists(
    playlists: List<PlaylistEntity>,
    showCached: Boolean,
): List<PlaylistEntity> {
    val cachedFiltered = filterCachedPlaylists(playlists, showCached)
    return cachedFiltered.filterNot { isSpecialRemotePlaylist(it) }
}

internal fun isSpecialRemotePlaylist(playlist: PlaylistEntity): Boolean {
    val id = playlist.browseId ?: playlist.id
    return id == "LM" || id == "SE"
}

internal fun isCachedName(name: String): Boolean {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .trim()
        .lowercase()
    return normalized == "en cache" || normalized == "cached"
}
