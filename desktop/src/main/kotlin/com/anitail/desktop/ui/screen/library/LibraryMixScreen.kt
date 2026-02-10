package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.relations.songHasArtist
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.LibraryAlbumGridItem
import com.anitail.desktop.ui.component.LibraryAlbumListItem
import com.anitail.desktop.ui.component.LibraryArtistGridItem
import com.anitail.desktop.ui.component.LibraryArtistListItem
import com.anitail.desktop.ui.component.LibraryPlaylistGridItem
import com.anitail.desktop.ui.component.LibraryPlaylistListItem
import com.anitail.desktop.ui.component.SortHeader
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    filterContent: @Composable () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onOpenAutoPlaylist: (AutoPlaylistType) -> Unit,
) {
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val playlistSongMaps by database.playlistSongMaps.collectAsState(initial = emptyList())
    val albums by database.albums.collectAsState(initial = emptyList())
    val artists by database.artists.collectAsState(initial = emptyList())
    val songs by database.songs.collectAsState(initial = emptyList())
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    val viewType by preferences.mixViewType.collectAsState()
    val sortType by preferences.mixSortType.collectAsState()
    val sortDescending by preferences.mixSortDescending.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()
    val strings = LocalStrings.current

    val showLiked by preferences.showLikedPlaylist.collectAsState()
    val showDownloaded by preferences.showDownloadedPlaylist.collectAsState()
    val showTop by preferences.showTopPlaylist.collectAsState()
    val showCached by preferences.showCachedPlaylist.collectAsState()

    val visiblePlaylists = remember(playlists, showCached) {
        filterLibraryPlaylists(playlists, showCached)
    }

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val libraryPlaylists = remember(visiblePlaylists, playlistSongMaps, songsById) {
        buildLibraryPlaylists(
            playlists = visiblePlaylists,
            playlistSongMaps = playlistSongMaps,
            songsById = songsById,
        )
    }
    val playlistsById = remember(libraryPlaylists) {
        libraryPlaylists.associateBy { it.playlist.id }
    }

    val likedSongs = remember(songs) { songs.filter { it.liked } }
    val downloadedSongEntities = remember(downloadedSongs, songsById) {
        downloadedSongs.map { downloaded -> songsById[downloaded.songId] ?: downloaded.toFallbackSong() }
    }
    val topSongs = remember(songs) {
        songs.sortedByDescending { it.totalPlayTime }.take(50)
    }

    val likedSongsTitle = strings.get("liked_songs")
    val offlineTitle = strings.get("offline")
    val myTopTitle = strings.get("my_top")
    val autoPlaylists = remember(likedSongs, downloadedSongEntities, topSongs, likedSongsTitle, offlineTitle, myTopTitle) {
        listOf(
            buildAutoPlaylist("liked", likedSongsTitle, likedSongs),
            buildAutoPlaylist("downloaded", offlineTitle, downloadedSongEntities),
            buildAutoPlaylist("top", myTopTitle, topSongs),
        )
    }

    val entries = remember(visiblePlaylists, albums, artists) {
        buildList<LibraryMixEntry> {
            addAll(visiblePlaylists.map { PlaylistMixEntry(it) })
            addAll(albums.map { AlbumMixEntry(it) })
            addAll(artists.map { ArtistMixEntry(it) })
        }
    }
    val sortedEntries = remember(entries, sortType, sortDescending) {
        sortMixEntries(entries, sortType, sortDescending)
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val currentSong = remember(playerState.currentItem?.id, songsById) {
        songsById[playerState.currentItem?.id]
    }
    val activeAlbumId = currentSong?.albumId
    val artistSongCount = remember(artists, songs, songArtistMaps) {
        artists.associate { artist ->
            val count = songs.count { song ->
                songHasArtist(song.id, artist.id, songArtistMaps) ||
                    (!song.artistName.isNullOrBlank() && song.artistName.equals(artist.name, ignoreCase = true))
            }
            artist.id to count
        }
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = { preferences.setMixSortType(it) },
                onSortDescendingChange = { preferences.setMixSortDescending(it) },
                sortTypeText = { mixSortLabel(strings, it) },
                showDescending = true,
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { preferences.setMixViewType(viewType.toggle()) },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            ) {
                Icon(
                    imageVector = if (viewType == LibraryViewType.LIST) IconAssets.list() else IconAssets.gridView(),
                    contentDescription = null,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "filter") { filterContent() }
                    item(key = "header") { headerContent() }

                    if (showLiked) {
                        val playlist = autoPlaylists[0]
                        item(key = "liked") {
                            LibraryPlaylistListItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.LIKED) },
                            )
                        }
                    }
                    if (showDownloaded) {
                        val playlist = autoPlaylists[1]
                        item(key = "downloaded") {
                            LibraryPlaylistListItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.DOWNLOADED) },
                            )
                        }
                    }
                    if (showTop) {
                        val playlist = autoPlaylists[2]
                        item(key = "top") {
                            LibraryPlaylistListItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.TOP) },
                            )
                        }
                    }

                    items(sortedEntries, key = { it.id }) { entry ->
                        when (entry) {
                            is PlaylistMixEntry -> {
                                val playlist = playlistsById[entry.playlist.id]
                                    ?: buildAutoPlaylist(entry.playlist.id, entry.playlist.name, emptyList())
                                LibraryPlaylistListItem(
                                    playlist = playlist,
                                    autoPlaylist = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onOpenPlaylist(entry.playlist.id, entry.playlist.name)
                                        },
                                )
                            }
                            is AlbumMixEntry -> {
                                LibraryAlbumListItem(
                                    album = entry.album,
                                    isActive = entry.album.id == activeAlbumId,
                                    isPlaying = playerState.isPlaying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenAlbum(entry.album.id, entry.album.title) },
                                )
                            }
                            is ArtistMixEntry -> {
                                val count = artistSongCount[entry.artist.id] ?: 0
                                LibraryArtistListItem(
                                    artist = entry.artist,
                                    songCount = count,
                                    showLikedBadge = entry.artist.bookmarkedAt != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenArtist(entry.artist.id, entry.artist.name) },
                                )
                            }
                        }
                    }
                }
            }
            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "filter", span = { GridItemSpan(maxLineSpan) }) { filterContent() }
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { headerContent() }

                    if (showLiked) {
                        val playlist = autoPlaylists[0]
                        item(key = "liked") {
                            LibraryPlaylistGridItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                gridItemSize = gridItemSize,
                                modifier = Modifier
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.LIKED) },
                            )
                        }
                    }
                    if (showDownloaded) {
                        val playlist = autoPlaylists[1]
                        item(key = "downloaded") {
                            LibraryPlaylistGridItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                gridItemSize = gridItemSize,
                                modifier = Modifier
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.DOWNLOADED) },
                            )
                        }
                    }
                    if (showTop) {
                        val playlist = autoPlaylists[2]
                        item(key = "top") {
                            LibraryPlaylistGridItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                gridItemSize = gridItemSize,
                                modifier = Modifier
                                    .clickable { onOpenAutoPlaylist(AutoPlaylistType.TOP) },
                            )
                        }
                    }

                    items(sortedEntries, key = { it.id }) { entry ->
                        when (entry) {
                            is PlaylistMixEntry -> {
                                val playlist = playlistsById[entry.playlist.id]
                                    ?: buildAutoPlaylist(entry.playlist.id, entry.playlist.name, emptyList())
                                LibraryPlaylistGridItem(
                                    playlist = playlist,
                                    autoPlaylist = false,
                                    gridItemSize = gridItemSize,
                                    modifier = Modifier.clickable {
                                        onOpenPlaylist(entry.playlist.id, entry.playlist.name)
                                    },
                                )
                            }
                            is AlbumMixEntry -> {
                                LibraryAlbumGridItem(
                                    album = entry.album,
                                    gridItemSize = gridItemSize,
                                    modifier = Modifier.clickable {
                                        onOpenAlbum(entry.album.id, entry.album.title)
                                    },
                                )
                            }
                            is ArtistMixEntry -> {
                                val count = artistSongCount[entry.artist.id] ?: 0
                                LibraryArtistGridItem(
                                    artist = entry.artist,
                                    songCount = count,
                                    showLikedBadge = entry.artist.bookmarkedAt != null,
                                    gridItemSize = gridItemSize,
                                    modifier = Modifier.clickable {
                                        onOpenArtist(entry.artist.id, entry.artist.name)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mixSortLabel(strings: StringResolver, sortType: MixSortType): String {
    return when (sortType) {
        MixSortType.CREATE_DATE -> strings.get("sort_by_create_date")
        MixSortType.LAST_UPDATED -> strings.get("sort_by_last_updated")
        MixSortType.NAME -> strings.get("sort_by_name")
    }
}

private fun DownloadedSong.toFallbackSong(): SongEntity {
    return SongEntity(
        id = songId,
        title = title,
        artistName = artist,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        dateModified = LocalDateTime.now(),
    )
}
