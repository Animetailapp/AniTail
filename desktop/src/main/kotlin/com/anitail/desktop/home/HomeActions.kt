package com.anitail.desktop.home

import com.anitail.desktop.db.mapper.extractChannelId
import com.anitail.desktop.db.mapper.extractPlaylistId
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem

enum class ShuffleSource {
    LOCAL,
    YT,
    NONE,
}

enum class LocalItemType {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
    UNKNOWN,
}

fun selectShuffleSource(localCount: Int, ytCount: Int, randomValue: Float): ShuffleSource {
    if (localCount <= 0 && ytCount <= 0) return ShuffleSource.NONE
    if (localCount > 0 && ytCount <= 0) return ShuffleSource.LOCAL
    if (ytCount > 0 && localCount <= 0) return ShuffleSource.YT
    return if (randomValue < 0.5f) ShuffleSource.LOCAL else ShuffleSource.YT
}

fun resolveLocalItemType(item: LibraryItem): LocalItemType {
    val playlistId = extractPlaylistId(item.playbackUrl)
    val channelId = extractChannelId(item.playbackUrl)
    if (!channelId.isNullOrBlank() || item.playbackUrl.contains("/channel/")) {
        return LocalItemType.ARTIST
    }
    if (!playlistId.isNullOrBlank() && (item.id.startsWith("MPREb_") ||
                item.id.startsWith("FEmusic_library_privately_owned_release_detail"))
    ) {
        return LocalItemType.ALBUM
    }
    if (!playlistId.isNullOrBlank()) {
        return LocalItemType.PLAYLIST
    }
    if (item.playbackUrl.contains("watch?v=") || item.playbackUrl.contains("youtu.be/")) {
        return LocalItemType.SONG
    }
    return LocalItemType.UNKNOWN
}

fun localItemToYtItem(item: LibraryItem): YTItem {
    val playlistId = extractPlaylistId(item.playbackUrl)
    val channelId = extractChannelId(item.playbackUrl)

    if (!channelId.isNullOrBlank() || item.playbackUrl.contains("/channel/")) {
        return ArtistItem(
            id = channelId ?: item.id,
            title = item.title,
            thumbnail = item.artworkUrl,
            channelId = channelId,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }

    if (!playlistId.isNullOrBlank() && (item.id.startsWith("MPREb_") ||
                item.id.startsWith("FEmusic_library_privately_owned_release_detail"))
    ) {
        return AlbumItem(
            browseId = item.id,
            playlistId = playlistId,
            title = item.title,
            artists = null,
            year = null,
            thumbnail = item.artworkUrl.orEmpty(),
            explicit = false,
        )
    }

    if (!playlistId.isNullOrBlank()) {
        return PlaylistItem(
            id = item.id,
            title = item.title,
            author = null,
            songCountText = null,
            thumbnail = item.artworkUrl,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isEditable = false,
        )
    }

    return SongItem(
        id = item.id,
        title = item.title,
        artists = emptyList(),
        thumbnail = item.artworkUrl.orEmpty(),
        explicit = false,
    )
}

fun buildAllYtItems(
    homePage: HomePage?,
    accountPlaylists: List<PlaylistItem>,
    similarRecommendations: List<SimilarRecommendation>,
): List<YTItem> {
    val homeItems = homePage?.sections?.flatMap { it.items }.orEmpty()
    val similarItems = similarRecommendations.flatMap { it.items }
    return (homeItems + accountPlaylists + similarItems)
        .distinctBy { it.id }
}
