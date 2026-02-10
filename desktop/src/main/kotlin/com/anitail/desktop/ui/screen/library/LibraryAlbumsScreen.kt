package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import com.anitail.desktop.db.entities.AlbumEntity
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibraryAlbumGridItem
import com.anitail.desktop.ui.component.LibraryAlbumListItem
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.SortHeader
import com.anitail.desktop.ui.screen.CollectionDownloadAction
import com.anitail.desktop.ui.screen.CollectionDownloadMenuState
import com.anitail.desktop.ui.screen.CollectionMenuHeader
import com.anitail.desktop.ui.screen.buildBrowseAlbumMenuActions
import com.anitail.desktop.ui.screen.resolveCollectionDownloadMenuState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.WatchEndpoint
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    onDeselect: () -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val albums by database.albums.collectAsState(initial = emptyList())
    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    val filter by preferences.albumFilter.collectAsState()
    val sortType by preferences.albumSortType.collectAsState()
    val sortDescending by preferences.albumSortDescending.collectAsState()
    val viewType by preferences.albumViewType.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()
    val strings = LocalStrings.current

    val songsByAlbumId = remember(songs) {
        songs.filter { !it.albumId.isNullOrBlank() }.groupBy { it.albumId.orEmpty() }
    }
    val playTimeByAlbum = remember(songsByAlbumId) {
        songsByAlbumId.mapValues { entry -> entry.value.sumOf { it.totalPlayTime } }
    }
    val artistNamesByAlbum = remember(songsByAlbumId) {
        songsByAlbumId.mapValues { entry ->
            entry.value.firstOrNull()?.artistName.orEmpty()
        }
    }

    val filteredAlbums = remember(albums, filter) {
        when (filter) {
            AlbumFilter.LIKED -> albums.filter { it.bookmarkedAt != null }
            AlbumFilter.LIBRARY -> albums.filter { it.inLibrary != null }
        }
    }
    val sortedAlbums = remember(filteredAlbums, sortType, sortDescending, artistNamesByAlbum, playTimeByAlbum) {
        sortAlbums(
            albums = filteredAlbums,
            sortType = sortType,
            descending = sortDescending,
            artistNames = artistNamesByAlbum,
            playTimeByAlbum = playTimeByAlbum,
        )
    }

    val currentSong = remember(playerState.currentItem?.id, songs) {
        songs.firstOrNull { it.id == playerState.currentItem?.id }
    }
    val activeAlbumId = currentSong?.albumId

    var pendingAddTarget by remember { mutableStateOf<AlbumSongTarget?>(null) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    pendingAddTarget?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists.filterNot { isCachedName(it.name) },
            onCreatePlaylist = { name ->
                val playlist = PlaylistEntity(name = name, createdAt = LocalDateTime.now())
                scope.launch {
                    database.insertPlaylist(playlist)
                    target.songs.forEach { song -> database.addSongToPlaylist(playlist.id, song.id) }
                }
                pendingAddTarget = null
            },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    target.songs.forEach { song -> database.addSongToPlaylist(playlist.id, song.id) }
                }
                pendingAddTarget = null
            },
            onDismiss = { pendingAddTarget = null },
        )
    }

    when (viewType) {
        LibraryViewType.LIST -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "filter") {
                    AlbumFilterRow(
                        filter = filter,
                        onFilterChange = { preferences.setAlbumFilter(it) },
                        onDeselect = onDeselect,
                    )
                }
                item(key = "header") {
                    AlbumHeaderRow(
                        strings = strings,
                        albumCount = sortedAlbums.size,
                        sortType = sortType,
                        sortDescending = sortDescending,
                        viewType = viewType,
                        onSortTypeChange = { preferences.setAlbumSortType(it) },
                        onSortDescendingChange = { preferences.setAlbumSortDescending(it) },
                        onToggleView = { preferences.setAlbumViewType(viewType.toggle()) },
                    )
                }

                if (sortedAlbums.isEmpty()) {
                    item(key = "empty") { EmptyAlbumsPlaceholder() }
                } else {
                    items(sortedAlbums, key = { it.id }) { album ->
                        val menuExpanded = remember(album.id) { mutableStateOf(false) }
                        val albumSongs = songsByAlbumId[album.id].orEmpty()
                        val menuActions = remember(albumSongs, downloadStates, downloadedSongs, strings) {
                            buildAlbumMenuActions(
                                strings = strings,
                                album = album,
                                albumSongs = albumSongs,
                                downloadStates = downloadStates,
                                downloadedSongs = downloadedSongs,
                                playerState = playerState,
                                downloadService = downloadService,
                                onRequestAddToPlaylist = {
                                    pendingAddTarget = AlbumSongTarget(albumSongs)
                                },
                                onOpenArtist = onOpenArtist,
                                onShare = { copyToClipboard(album.toLibraryItem().playbackUrl) },
                                scope = scope,
                            )
                        }
                        Box {
                            LibraryAlbumListItem(
                                album = album,
                                isActive = album.id == activeAlbumId,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpenAlbum(album.id, album.title) },
                                        onLongClick = { menuExpanded.value = true },
                                    ),
                                trailingContent = {
                                    IconButton(onClick = { menuExpanded.value = true }) {
                                        Icon(
                                            imageVector = IconAssets.moreVert(),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )

                            ItemContextMenu(
                                expanded = menuExpanded.value,
                                onDismiss = { menuExpanded.value = false },
                                item = album.toLibraryItem(),
                                actions = menuActions,
                                headerContent = {
                                    CollectionMenuHeader(
                                        title = album.title,
                                        subtitle = joinByBullet(
                                            artistNamesByAlbum[album.id],
                                            album.year?.toString(),
                                        ).orEmpty(),
                                        thumbnailUrl = album.thumbnailUrl,
                                        showLike = true,
                                        isLiked = album.bookmarkedAt != null,
                                        onToggleLike = { scope.launch { database.toggleAlbumBookmark(album.id) } },
                                    )
                                },
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
                item(key = "filter", span = { GridItemSpan(maxLineSpan) }) {
                    AlbumFilterRow(
                        filter = filter,
                        onFilterChange = { preferences.setAlbumFilter(it) },
                        onDeselect = onDeselect,
                    )
                }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    AlbumHeaderRow(
                        strings = strings,
                        albumCount = sortedAlbums.size,
                        sortType = sortType,
                        sortDescending = sortDescending,
                        viewType = viewType,
                        onSortTypeChange = { preferences.setAlbumSortType(it) },
                        onSortDescendingChange = { preferences.setAlbumSortDescending(it) },
                        onToggleView = { preferences.setAlbumViewType(viewType.toggle()) },
                    )
                }

                if (sortedAlbums.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyAlbumsPlaceholder() }
                } else {
                    items(sortedAlbums, key = { it.id }) { album ->
                        val menuExpanded = remember(album.id) { mutableStateOf(false) }
                        val albumSongs = songsByAlbumId[album.id].orEmpty()
                        val menuActions = remember(albumSongs, downloadStates, downloadedSongs, strings) {
                            buildAlbumMenuActions(
                                strings = strings,
                                album = album,
                                albumSongs = albumSongs,
                                downloadStates = downloadStates,
                                downloadedSongs = downloadedSongs,
                                playerState = playerState,
                                downloadService = downloadService,
                                onRequestAddToPlaylist = {
                                    pendingAddTarget = AlbumSongTarget(albumSongs)
                                },
                                onOpenArtist = onOpenArtist,
                                onShare = { copyToClipboard(album.toLibraryItem().playbackUrl) },
                                scope = scope,
                            )
                        }
                        Box {
                            LibraryAlbumGridItem(
                                album = album,
                                gridItemSize = gridItemSize,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onOpenAlbum(album.id, album.title) },
                                    onLongClick = { menuExpanded.value = true },
                                ),
                            )

                            ItemContextMenu(
                                expanded = menuExpanded.value,
                                onDismiss = { menuExpanded.value = false },
                                item = album.toLibraryItem(),
                                actions = menuActions,
                                headerContent = {
                                    CollectionMenuHeader(
                                        title = album.title,
                                        subtitle = joinByBullet(
                                            artistNamesByAlbum[album.id],
                                            album.year?.toString(),
                                        ).orEmpty(),
                                        thumbnailUrl = album.thumbnailUrl,
                                        showLike = true,
                                        isLiked = album.bookmarkedAt != null,
                                        onToggleLike = { scope.launch { database.toggleAlbumBookmark(album.id) } },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AlbumSongTarget(
    val songs: List<SongEntity>,
)

private fun buildAlbumMenuActions(
    strings: StringResolver,
    album: AlbumEntity,
    albumSongs: List<SongEntity>,
    downloadStates: Map<String, com.anitail.desktop.download.DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    playerState: PlayerState,
    downloadService: DesktopDownloadService,
    onRequestAddToPlaylist: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onShare: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): List<ContextMenuAction> {
    val downloadMenu = resolveCollectionDownloadMenuState(
        strings = strings,
        songIds = albumSongs.map { it.id },
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
        showWhenEmpty = true,
    ) ?: CollectionDownloadMenuState(label = strings.get("download"), action = CollectionDownloadAction.DOWNLOAD)
    val artistId = albumSongs.firstOrNull { !it.artistId.isNullOrBlank() }?.artistId
    val artistName = albumSongs.firstOrNull { !it.artistName.isNullOrBlank() }?.artistName

    return buildBrowseAlbumMenuActions(
        strings = strings,
        hasArtists = !artistId.isNullOrBlank(),
        downloadLabel = downloadMenu.label,
        downloadEnabled = true,
        onStartRadio = {
            val seed = albumSongs.firstOrNull() ?: return@buildBrowseAlbumMenuActions
            scope.launch {
                val result = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull() ?: return@launch
                val plan = buildRadioQueuePlan(seed.toLibraryItem(), result)
                playerState.playQueue(plan.items, plan.startIndex)
            }
        },
        onPlayNext = {
            albumSongs
                .asReversed()
                .map { it.toLibraryItem() }
                .forEach { playerState.addToQueue(it, playNext = true) }
        },
        onAddToQueue = {
            albumSongs
                .map { it.toLibraryItem() }
                .forEach { playerState.addToQueue(it) }
        },
        onAddToPlaylist = onRequestAddToPlaylist,
        onDownload = {
            scope.launch {
                when (downloadMenu.action) {
                    CollectionDownloadAction.DOWNLOAD -> {
                        albumSongs.forEach { song ->
                            downloadService.downloadSong(
                                songId = song.id,
                                title = song.title,
                                artist = song.artistName.orEmpty(),
                                album = song.albumName,
                                thumbnailUrl = song.thumbnailUrl,
                                duration = song.duration,
                            )
                        }
                    }
                    CollectionDownloadAction.CANCEL -> {
                        albumSongs.forEach { song -> downloadService.cancelDownload(song.id) }
                    }
                    CollectionDownloadAction.REMOVE -> {
                        albumSongs.forEach { song -> downloadService.deleteDownload(song.id) }
                    }
                }
            }
        },
        onOpenArtist = {
            if (!artistId.isNullOrBlank()) {
                onOpenArtist(artistId, artistName)
            }
        },
        onShare = onShare,
    )
}

@Composable
private fun AlbumFilterRow(
    filter: AlbumFilter,
    onFilterChange: (AlbumFilter) -> Unit,
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
            label = { Text(stringResource("albums")) },
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
                AlbumFilter.LIKED to stringResource("liked"),
                AlbumFilter.LIBRARY to stringResource("in_library"),
            ),
            currentValue = filter,
            onValueUpdate = onFilterChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlbumHeaderRow(
    strings: StringResolver,
    albumCount: Int,
    sortType: AlbumSortType,
    sortDescending: Boolean,
    viewType: LibraryViewType,
    onSortTypeChange: (AlbumSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onToggleView: () -> Unit,
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
            sortTypeText = { albumSortLabel(strings, it) },
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = pluralStringResource("n_album", albumCount, albumCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (viewType == LibraryViewType.LIST) IconAssets.list() else IconAssets.gridView(),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun EmptyAlbumsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource("library_albums_empty_title"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun albumSortLabel(strings: StringResolver, sortType: AlbumSortType): String {
    return when (sortType) {
        AlbumSortType.CREATE_DATE -> strings.get("sort_by_create_date")
        AlbumSortType.NAME -> strings.get("sort_by_name")
        AlbumSortType.ARTIST -> strings.get("sort_by_artist")
        AlbumSortType.YEAR -> strings.get("sort_by_year")
        AlbumSortType.SONG_COUNT -> strings.get("sort_by_song_count")
        AlbumSortType.LENGTH -> strings.get("sort_by_length")
        AlbumSortType.PLAY_TIME -> strings.get("sort_by_play_time")
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

private fun isCachedName(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized == "en caché" || normalized == "en cache" || normalized == "cached"
}
