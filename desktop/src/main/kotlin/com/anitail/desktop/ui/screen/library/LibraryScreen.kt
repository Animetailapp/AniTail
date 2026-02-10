package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.component.ChipsRow

@Composable
fun LibraryScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    val filter by preferences.libraryFilter.collectAsState()
    var autoPlaylist by remember { mutableStateOf<AutoPlaylistType?>(null) }

    if (autoPlaylist != null) {
        LibraryAutoPlaylistScreen(
            type = autoPlaylist ?: AutoPlaylistType.LIKED,
            database = database,
            downloadService = downloadService,
            playerState = playerState,
            preferences = preferences,
            onBack = { autoPlaylist = null },
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
        )
        return
    }

    val filterContent: @Composable () -> Unit = {
        Row {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.LIBRARY to stringResource("mixes"),
                    LibraryFilter.PLAYLISTS to stringResource("playlists"),
                    LibraryFilter.SONGS to stringResource("songs"),
                    LibraryFilter.ALBUMS to stringResource("albums"),
                    LibraryFilter.ARTISTS to stringResource("artists"),
                    LibraryFilter.DOWNLOADED to stringResource("filter_downloaded"),
                ),
                currentValue = filter,
                onValueUpdate = { value ->
                    preferences.setLibraryFilter(
                        if (filter == value) LibraryFilter.LIBRARY else value,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filter) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                database = database,
                downloadService = downloadService,
                preferences = preferences,
                playerState = playerState,
                filterContent = filterContent,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onOpenPlaylist = onOpenPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onOpenAutoPlaylist = { autoPlaylist = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                database = database,
                downloadService = downloadService,
                preferences = preferences,
                playerState = playerState,
                filterContent = filterContent,
                onOpenPlaylist = onOpenPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onOpenAutoPlaylist = { autoPlaylist = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                database = database,
                downloadService = downloadService,
                preferences = preferences,
                playerState = playerState,
                onDeselect = { preferences.setLibraryFilter(LibraryFilter.LIBRARY) },
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                database = database,
                downloadService = downloadService,
                preferences = preferences,
                playerState = playerState,
                onDeselect = { preferences.setLibraryFilter(LibraryFilter.LIBRARY) },
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                artists = database.artists.collectAsState(initial = emptyList()).value,
                songs = database.songs.collectAsState(initial = emptyList()).value,
                playerState = playerState,
                preferences = preferences,
                database = database,
                onDeselect = { preferences.setLibraryFilter(LibraryFilter.LIBRARY) },
                onArtistClick = { artistId, artistName -> onOpenArtist(artistId, artistName) },
            )
            LibraryFilter.DOWNLOADED -> LibraryDownloadedScreen(
                database = database,
                downloadService = downloadService,
                playerState = playerState,
                preferences = preferences,
                onDeselect = { preferences.setLibraryFilter(LibraryFilter.LIBRARY) },
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
            )
        }
    }
}
