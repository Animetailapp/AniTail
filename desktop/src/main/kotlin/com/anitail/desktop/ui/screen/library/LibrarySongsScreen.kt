package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.SortHeader
import com.anitail.desktop.ui.screen.BrowseSongTarget
import com.anitail.desktop.ui.screen.SongMenuHeader
import com.anitail.desktop.ui.screen.buildBrowseSongMenuActions
import com.anitail.desktop.ui.screen.formatTime
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    onDeselect: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
    val strings = LocalStrings.current

    val filter by preferences.songFilter.collectAsState()
    val sortType by preferences.songSortType.collectAsState()
    val sortDescending by preferences.songSortDescending.collectAsState()
    val showCached by preferences.showCachedPlaylist.collectAsState()

    val visiblePlaylists = remember(playlists, showCached) {
        filterLibraryPlaylists(playlists, showCached)
    }

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.songId }.toSet() }
    val filteredSongs = remember(songs, filter, downloadedIds) {
        filterSongs(songs, filter, downloadedIds)
    }
    val sortedSongs = remember(filteredSongs, sortType, sortDescending) {
        sortSongs(filteredSongs, sortType, sortDescending)
    }

    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    val listState = rememberLazyListState()

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching {
            val uri = if (url.startsWith("http", ignoreCase = true)) URI(url) else File(url).toURI()
            Desktop.getDesktop().browse(uri)
        }
    }

    suspend fun ensureSongInDatabase(target: BrowseSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }

    pendingPlaylistItem?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = visiblePlaylists,
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name, createdAt = LocalDateTime.now())
                scope.launch {
                    database.insertPlaylist(playlist)
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    ensureSongInDatabase(target)
                    database.addSongToPlaylist(playlist.id, target.item.id)
                }
                pendingPlaylistItem = null
            },
            onDismiss = { pendingPlaylistItem = null },
        )
    }

    detailsItem?.let { item ->
        MediaDetailsDialog(
            visible = true,
            item = item,
            onCopyLink = { copyToClipboard(item.playbackUrl) },
            onOpenInBrowser = { openInBrowser(item.playbackUrl) },
            onDismiss = { detailsItem = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "filter") {
                SongFilterRow(
                    filter = filter,
                    onFilterChange = { preferences.setSongFilter(it) },
                    onDeselect = onDeselect,
                )
            }

            item(key = "header") {
                SongHeaderRow(
                    strings = strings,
                    songCount = sortedSongs.size,
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = { preferences.setSongSortType(it) },
                    onSortDescendingChange = { preferences.setSongSortDescending(it) },
                )
            }

            itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                val menuExpanded = remember(song.id) { mutableStateOf(false) }
                val libraryItem = song.toLibraryItem()
                val menuActions = remember(
                    song.id,
                    songArtistMaps,
                    downloadStates,
                    downloadedSongs,
                    strings,
                ) {
                    buildBrowseSongMenuActions(
                        strings = strings,
                        libraryItem = libraryItem,
                        songItem = null,
                        songsById = songsById,
                        songArtistMaps = songArtistMaps,
                        downloadStates = downloadStates,
                        downloadedSongs = downloadedSongs,
                        database = database,
                        downloadService = downloadService,
                        playerState = playerState,
                        coroutineScope = scope,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onRequestPlaylist = { pendingPlaylistItem = it },
                        onRequestArtists = {},
                        onShowDetails = { detailsItem = it },
                        copyToClipboard = ::copyToClipboard,
                    )
                }
                val subtitle = joinByBullet(
                    song.artistName,
                    song.duration.takeIf { it > 0 }?.let { formatTime(it * 1000L) },
                ).orEmpty()
                Box {
                    LibrarySongListItem(
                        song = song,
                        showInLibraryIcon = true,
                        downloaded = song.id in downloadedIds,
                        isActive = song.id == playerState.currentItem?.id,
                        isPlaying = playerState.isPlaying,
                        isSelected = false,
                        onClick = {
                            if (song.id == playerState.currentItem?.id) {
                                playerState.togglePlayPause()
                            } else {
                                val items = sortedSongs.map { it.toLibraryItem() }
                                playerState.playQueue(items, startIndex = index)
                            }
                        },
                        onMenuClick = { menuExpanded.value = true },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ItemContextMenu(
                        expanded = menuExpanded.value,
                        onDismiss = { menuExpanded.value = false },
                        item = libraryItem,
                        actions = menuActions,
                        headerContent = {
                            SongMenuHeader(
                                title = song.title,
                                subtitle = subtitle,
                                thumbnailUrl = song.thumbnailUrl,
                                isLiked = song.liked,
                                onToggleLike = { scope.launch { database.toggleSongLike(song.id) } },
                                onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
                                onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(libraryItem, null) },
                                onShare = { copyToClipboard(libraryItem.playbackUrl) },
                                onDismiss = { menuExpanded.value = false },
                            )
                        },
                    )
                }
            }
        }

        HideOnScrollFAB(
            visible = sortedSongs.isNotEmpty(),
            lazyListState = listState,
            onClick = {
                val items = sortedSongs.shuffled().map { it.toLibraryItem() }
                playerState.playQueue(items, startIndex = 0)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
    }
}

@Composable
private fun SongFilterRow(
    filter: SongFilter,
    onFilterChange: (SongFilter) -> Unit,
    onDeselect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Spacer(Modifier.width(12.dp))
        FilterChip(
            label = { Text(stringResource("songs")) },
            selected = true,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            onClick = onDeselect,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(imageVector = IconAssets.close(), contentDescription = null) },
        )
        ChipsRow(
            chips = listOf(
                SongFilter.LIKED to stringResource("liked"),
                SongFilter.LIBRARY to stringResource("in_library"),
                SongFilter.DOWNLOADED to stringResource("filter_downloaded"),
            ),
            currentValue = filter,
            onValueUpdate = onFilterChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SongHeaderRow(
    strings: StringResolver,
    songCount: Int,
    sortType: SongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (SongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { songSortLabel(strings, it) },
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = pluralStringResource("n_song", songCount, songCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun songSortLabel(strings: StringResolver, sortType: SongSortType): String {
    return when (sortType) {
        SongSortType.CREATE_DATE -> strings.get("sort_by_create_date")
        SongSortType.NAME -> strings.get("sort_by_name")
        SongSortType.ARTIST -> strings.get("sort_by_artist")
        SongSortType.PLAY_TIME -> strings.get("sort_by_play_time")
    }
}

private fun joinByBullet(left: String?, right: String?): String? {
    return when {
        left.isNullOrBlank() && right.isNullOrBlank() -> null
        left.isNullOrBlank() -> right
        right.isNullOrBlank() -> left
        else -> "$left • $right"
    }
}
