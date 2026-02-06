package com.anitail.desktop.db.mapper

import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.shared.model.LibraryItem
import java.time.LocalDateTime

/**
 * Extension functions to map between innertube models and desktop database entities.
 * This ensures consistency with Android and proper reuse of the innertube module.
 */

// ============ SongItem <-> SongEntity ============

fun SongItem.toSongEntity(inLibrary: Boolean = false): SongEntity = SongEntity(
    id = id,
    title = title,
    duration = duration ?: -1,
    thumbnailUrl = thumbnail,
    albumId = album?.id,
    albumName = album?.name,
    artistName = artists.joinToString(", ") { it.name },
    explicit = explicit,
    year = null,
    date = null,
    dateModified = LocalDateTime.now(),
    liked = false,
    likedDate = null,
    totalPlayTime = 0,
    inLibrary = if (inLibrary) LocalDateTime.now() else null,
    dateDownload = null,
    isLocal = false,
)

fun SongEntity.toLibraryItem(): LibraryItem = LibraryItem(
    id = id,
    title = title,
    artist = artistName ?: "",
    artworkUrl = thumbnailUrl,
    playbackUrl = "https://music.youtube.com/watch?v=$id",
    durationMs = if (duration > 0) duration * 1000L else null,
)

// ============ ArtistItem <-> ArtistEntity ============

fun ArtistItem.toArtistEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = title,
    thumbnailUrl = thumbnail,
    channelId = channelId,
    lastUpdateTime = LocalDateTime.now(),
    bookmarkedAt = null,
)

fun ArtistEntity.toLibraryItem(): LibraryItem = LibraryItem(
    id = id,
    title = name,
    artist = "Artist",
    artworkUrl = thumbnailUrl,
    playbackUrl = "https://music.youtube.com/channel/$id",
)

// ============ AlbumItem <-> AlbumEntity ============

fun AlbumItem.toAlbumEntity(): AlbumEntity = AlbumEntity(
    id = browseId,
    playlistId = playlistId,
    title = title,
    year = year,
    thumbnailUrl = thumbnail,
    themeColor = null,
    songCount = 0,
    duration = 0,
    lastUpdateTime = LocalDateTime.now(),
    bookmarkedAt = null,
    likedDate = null,
    inLibrary = null,
)

fun AlbumEntity.toLibraryItem(): LibraryItem = LibraryItem(
    id = id,
    title = title,
    artist = "Album",
    artworkUrl = thumbnailUrl,
    playbackUrl = "https://music.youtube.com/playlist?list=$playlistId",
)

// ============ PlaylistItem <-> PlaylistEntity ============

fun PlaylistItem.toPlaylistEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = title,
    browseId = id,
    createdAt = LocalDateTime.now(),
    lastUpdateTime = LocalDateTime.now(),
    isEditable = isEditable,
    bookmarkedAt = null,
    remoteSongCount = songCountText?.filter { it.isDigit() }?.toIntOrNull(),
    playEndpointParams = playEndpoint?.params,
    thumbnailUrl = thumbnail,
    shuffleEndpointParams = shuffleEndpoint?.params,
    radioEndpointParams = radioEndpoint?.params,
    backgroundImageUrl = null,
)

fun PlaylistEntity.toLibraryItem(): LibraryItem = LibraryItem(
    id = id,
    title = name,
    artist = "Playlist",
    artworkUrl = thumbnailUrl,
    playbackUrl = "https://music.youtube.com/playlist?list=$browseId",
)

// ============ LibraryItem conversions ============

/**
 * Creates a basic SongEntity from a LibraryItem.
 * Used when adding items from innertube directly.
 */
fun LibraryItem.toSongEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    duration = (durationMs?.div(1000))?.toInt() ?: -1,
    thumbnailUrl = artworkUrl,
    albumId = null,
    albumName = null,
    artistName = artist,
    explicit = false,
    year = null,
    date = null,
    dateModified = LocalDateTime.now(),
    liked = false,
    likedDate = null,
    totalPlayTime = 0,
    inLibrary = LocalDateTime.now(),
    dateDownload = null,
    isLocal = false,
)

/**
 * Helper to extract video ID from YouTube URL
 */
fun extractVideoId(url: String): String? {
    return when {
        url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
        else -> null
    }
}

/**
 * Helper to extract playlist ID from YouTube URL
 */
fun extractPlaylistId(url: String): String? {
    return when {
        url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
        else -> null
    }
}

/**
 * Helper to extract channel ID from YouTube URL
 */
fun extractChannelId(url: String): String? {
    return when {
        url.contains("/channel/") -> url.substringAfter("/channel/").substringBefore("/")
        url.contains("/c/") -> url.substringAfter("/c/").substringBefore("/")
        else -> null
    }
}
