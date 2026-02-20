package com.anitail.desktop.ui.screen.library

import androidx.compose.ui.unit.dp

enum class LibraryFilter {
    LIBRARY,
    PLAYLISTS,
    SONGS,
    ALBUMS,
    ARTISTS,
    DOWNLOADED,
}

enum class LibraryViewType {
    LIST,
    GRID;

    fun toggle(): LibraryViewType = if (this == LIST) GRID else LIST
}

enum class GridItemSize {
    SMALL,
    BIG,
}

enum class MixSortType {
    CREATE_DATE,
    LAST_UPDATED,
    NAME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class SongFilter {
    LIKED,
    LIBRARY,
    DOWNLOADED,
}

enum class AlbumFilter {
    LIKED,
    LIBRARY,
}

enum class ArtistFilter {
    LIKED,
    LIBRARY,
}

val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
