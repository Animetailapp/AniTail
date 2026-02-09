package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibraryPlaylistGridItem
import com.anitail.desktop.ui.component.LibraryPlaylistListItem
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.SortHeader
import com.anitail.desktop.ui.component.pluralizeSongs
import com.anitail.desktop.ui.screen.CollectionDownloadAction
import com.anitail.desktop.ui.screen.CollectionDownloadMenuState
import com.anitail.desktop.ui.screen.CollectionMenuHeader
import com.anitail.desktop.ui.screen.buildBrowsePlaylistMenuActions
import com.anitail.desktop.ui.screen.resolveCollectionDownloadMenuState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.WatchEndpoint
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    preferences: DesktopPreferences,
    playerState: PlayerState,
    filterContent: @Composable () -> Unit,
    onOpenPlaylist: (String, String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onOpenAutoPlaylist: (AutoPlaylistType) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val playlistSongMaps by database.playlistSongMaps.collectAsState(initial = emptyList())
    val songs by database.songs.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()

    val viewType by preferences.playlistViewType.collectAsState()
    val sortType by preferences.playlistSortType.collectAsState()
    val sortDescending by preferences.playlistSortDescending.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()
    val showLiked by preferences.showLikedPlaylist.collectAsState()
    val showDownloaded by preferences.showDownloadedPlaylist.collectAsState()
    val showTop by preferences.showTopPlaylist.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val visiblePlaylists = remember(playlists) { playlists.filterNot { isCachedName(it.name) } }
    val libraryPlaylists = remember(visiblePlaylists, playlistSongMaps, songsById) {
        buildLibraryPlaylists(
            playlists = visiblePlaylists,
            playlistSongMaps = playlistSongMaps,
            songsById = songsById,
        )
    }
    val playlistById = remember(libraryPlaylists) {
        libraryPlaylists.associateBy { it.playlist.id }
    }
    val songCountByPlaylist = remember(libraryPlaylists) {
        libraryPlaylists.associate { it.playlist.id to it.songCount }
    }
    val sortedPlaylists = remember(visiblePlaylists, sortType, sortDescending, songCountByPlaylist) {
        sortPlaylists(
            playlists = visiblePlaylists,
            sortType = sortType,
            descending = sortDescending,
            songCounts = songCountByPlaylist,
        )
    }
    val sortedLibraryPlaylists = remember(sortedPlaylists, playlistById) {
        sortedPlaylists.mapNotNull { playlistById[it.id] }
    }
    val playlistSongsById = remember(playlistSongMaps, songsById) {
        playlistSongMaps
            .groupBy { it.playlistId }
            .mapValues { entry ->
                entry.value
                    .sortedBy { it.position }
                    .mapNotNull { songsById[it.songId] }
            }
    }

    val likedSongs = remember(songs) { songs.filter { it.liked } }
    val downloadedSongEntities = remember(downloadedSongs, songsById) {
        downloadedSongs.map { downloaded -> songsById[downloaded.songId] ?: downloaded.toFallbackSong() }
    }
    val topSongs = remember(songs) {
        songs.sortedByDescending { it.totalPlayTime }.take(50)
    }
    val autoPlaylists = remember(likedSongs, downloadedSongEntities, topSongs) {
        listOf(
            buildAutoPlaylist("liked", "Me gusta", likedSongs),
            buildAutoPlaylist("downloaded", "Sin conexión", downloadedSongEntities),
            buildAutoPlaylist("top", "Mi Top 50", topSongs),
        )
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingAddTarget by remember { mutableStateOf<PlaylistSongTarget?>(null) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = onCreatePlaylist,
            onDismiss = { showCreateDialog = false },
        )
    }

    pendingAddTarget?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = visiblePlaylists.filterNot { it.id == target.playlist.id },
            onCreatePlaylist = { name ->
                val newPlaylist = PlaylistEntity(name = name, createdAt = LocalDateTime.now())
                scope.launch {
                    database.insertPlaylist(newPlaylist)
                    target.songs.forEach { song -> database.addSongToPlaylist(newPlaylist.id, song.id) }
                }
                pendingAddTarget = null
            },
            onSelectPlaylist = { selected ->
                scope.launch {
                    target.songs.forEach { song -> database.addSongToPlaylist(selected.id, song.id) }
                }
                pendingAddTarget = null
            },
            onDismiss = { pendingAddTarget = null },
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = { preferences.setPlaylistSortType(it) },
                onSortDescendingChange = { preferences.setPlaylistSortDescending(it) },
                sortTypeText = ::playlistSortLabel,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (sortedLibraryPlaylists.size == 1) "1 playlist" else "${sortedLibraryPlaylists.size} playlists",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = { preferences.setPlaylistViewType(viewType.toggle()) },
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

                    if (sortedLibraryPlaylists.isEmpty() && !(showLiked || showDownloaded || showTop)) {
                        item(key = "empty") { EmptyPlaylistsPlaceholder() }
                    } else {
                        items(sortedLibraryPlaylists, key = { it.playlist.id }) { playlist ->
                            val playlistSongs = playlistSongsById[playlist.playlist.id].orEmpty()
                            val menuExpanded = remember(playlist.playlist.id) { mutableStateOf(false) }
                            val menuActions = remember(
                                playlistSongs,
                                downloadStates,
                                downloadedSongs,
                            ) {
                                buildPlaylistMenuActions(
                                    playlist = playlist,
                                    playlistSongs = playlistSongs,
                                    downloadStates = downloadStates,
                                    downloadedSongs = downloadedSongs,
                                    playerState = playerState,
                                    downloadService = downloadService,
                                    onRequestAddToPlaylist = {
                                        pendingAddTarget = PlaylistSongTarget(playlist.playlist, playlistSongs)
                                    },
                                    onShare = { playlist.playlist.shareLink?.let(::copyToClipboard) },
                                    scope = scope,
                                )
                            }
                            Box {
                                LibraryPlaylistListItem(
                                    playlist = playlist,
                                    autoPlaylist = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onOpenPlaylist(playlist.playlist.id, playlist.playlist.name) },
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
                                    item = playlist.playlist.toLibraryItem(),
                                    actions = menuActions,
                                    headerContent = {
                                        CollectionMenuHeader(
                                            title = playlist.playlist.name,
                                            subtitle = pluralizeSongs(playlist.songCount),
                                            thumbnailUrl = playlist.thumbnails.firstOrNull() ?: playlist.playlist.thumbnailUrl,
                                            showLike = !playlist.playlist.isEditable,
                                            isLiked = playlist.playlist.bookmarkedAt != null,
                                            onToggleLike = {
                                                if (!playlist.playlist.isEditable) {
                                                    scope.launch { database.togglePlaylistBookmark(playlist.playlist.id) }
                                                }
                                            },
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
                    item(key = "filter", span = { GridItemSpan(maxLineSpan) }) { filterContent() }
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { headerContent() }

                    if (showLiked) {
                        val playlist = autoPlaylists[0]
                        item(key = "liked") {
                            LibraryPlaylistGridItem(
                                playlist = playlist,
                                autoPlaylist = true,
                                gridItemSize = gridItemSize,
                                modifier = Modifier.clickable { onOpenAutoPlaylist(AutoPlaylistType.LIKED) },
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
                                modifier = Modifier.clickable { onOpenAutoPlaylist(AutoPlaylistType.DOWNLOADED) },
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
                                modifier = Modifier.clickable { onOpenAutoPlaylist(AutoPlaylistType.TOP) },
                            )
                        }
                    }

                    if (sortedLibraryPlaylists.isEmpty() && !(showLiked || showDownloaded || showTop)) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyPlaylistsPlaceholder() }
                    } else {
                        items(sortedLibraryPlaylists, key = { it.playlist.id }) { playlist ->
                            val playlistSongs = playlistSongsById[playlist.playlist.id].orEmpty()
                            val menuExpanded = remember(playlist.playlist.id) { mutableStateOf(false) }
                            val menuActions = remember(
                                playlistSongs,
                                downloadStates,
                                downloadedSongs,
                            ) {
                                buildPlaylistMenuActions(
                                    playlist = playlist,
                                    playlistSongs = playlistSongs,
                                    downloadStates = downloadStates,
                                    downloadedSongs = downloadedSongs,
                                    playerState = playerState,
                                    downloadService = downloadService,
                                    onRequestAddToPlaylist = {
                                        pendingAddTarget = PlaylistSongTarget(playlist.playlist, playlistSongs)
                                    },
                                    onShare = { playlist.playlist.shareLink?.let(::copyToClipboard) },
                                    scope = scope,
                                )
                            }
                            Box {
                                LibraryPlaylistGridItem(
                                    playlist = playlist,
                                    autoPlaylist = false,
                                    gridItemSize = gridItemSize,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onOpenPlaylist(playlist.playlist.id, playlist.playlist.name) },
                                        onLongClick = { menuExpanded.value = true },
                                    ),
                                )

                                ItemContextMenu(
                                    expanded = menuExpanded.value,
                                    onDismiss = { menuExpanded.value = false },
                                    item = playlist.playlist.toLibraryItem(),
                                    actions = menuActions,
                                    headerContent = {
                                        CollectionMenuHeader(
                                            title = playlist.playlist.name,
                                            subtitle = pluralizeSongs(playlist.songCount),
                                            thumbnailUrl = playlist.thumbnails.firstOrNull() ?: playlist.playlist.thumbnailUrl,
                                            showLike = !playlist.playlist.isEditable,
                                            isLiked = playlist.playlist.bookmarkedAt != null,
                                            onToggleLike = {
                                                if (!playlist.playlist.isEditable) {
                                                    scope.launch { database.togglePlaylistBookmark(playlist.playlist.id) }
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(imageVector = IconAssets.add(), contentDescription = "Crear playlist")
        }
    }
}

private data class PlaylistSongTarget(
    val playlist: PlaylistEntity,
    val songs: List<SongEntity>,
)

private fun buildPlaylistMenuActions(
    playlist: LibraryPlaylist,
    playlistSongs: List<SongEntity>,
    downloadStates: Map<String, com.anitail.desktop.download.DownloadState>,
    downloadedSongs: List<DownloadedSong>,
    playerState: PlayerState,
    downloadService: DesktopDownloadService,
    onRequestAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): List<ContextMenuAction> {
    val downloadMenu = resolveCollectionDownloadMenuState(
        songIds = playlistSongs.map { it.id },
        downloadStates = downloadStates,
        downloadedSongs = downloadedSongs,
        showWhenEmpty = false,
    )
    return buildBrowsePlaylistMenuActions(
        canPlay = playlistSongs.isNotEmpty(),
        canShuffle = playlistSongs.isNotEmpty(),
        canRadio = playlistSongs.isNotEmpty(),
        showDownload = downloadMenu != null,
        downloadLabel = downloadMenu?.label ?: "Descargar",
        downloadEnabled = true,
        onPlay = {
            val items = playlistSongs.map { it.toLibraryItem() }
            if (items.isNotEmpty()) playerState.playQueue(items, startIndex = 0)
        },
        onShuffle = {
            val items = playlistSongs.map { it.toLibraryItem() }.shuffled()
            if (items.isNotEmpty()) playerState.playQueue(items, startIndex = 0)
        },
        onStartRadio = {
            val seed = playlistSongs.firstOrNull() ?: return@buildBrowsePlaylistMenuActions
            scope.launch {
                val result = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull() ?: return@launch
                val plan = buildRadioQueuePlan(seed.toLibraryItem(), result)
                playerState.playQueue(plan.items, plan.startIndex)
            }
        },
        onPlayNext = {
            playlistSongs
                .asReversed()
                .map { it.toLibraryItem() }
                .forEach { playerState.addToQueue(it, playNext = true) }
        },
        onAddToQueue = {
            playlistSongs
                .map { it.toLibraryItem() }
                .forEach { playerState.addToQueue(it) }
        },
        onAddToPlaylist = onRequestAddToPlaylist,
        onDownload = {
            val menu = downloadMenu ?: return@buildBrowsePlaylistMenuActions
            scope.launch {
                when (menu.action) {
                    CollectionDownloadAction.DOWNLOAD -> {
                        playlistSongs.forEach { song ->
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
                        playlistSongs.forEach { song -> downloadService.cancelDownload(song.id) }
                    }
                    CollectionDownloadAction.REMOVE -> {
                        playlistSongs.forEach { song -> downloadService.deleteDownload(song.id) }
                    }
                }
            }
        },
        onShare = onShare,
    )
}

@Composable
private fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva playlist") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = playlistName.trim()
                    if (name.isNotEmpty()) {
                        onCreate(name)
                        playlistName = ""
                        onDismiss()
                    }
                },
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun EmptyPlaylistsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No hay playlists",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun playlistSortLabel(sortType: PlaylistSortType): String {
    return when (sortType) {
        PlaylistSortType.CREATE_DATE -> "Fecha de agregado"
        PlaylistSortType.NAME -> "Nombre"
        PlaylistSortType.SONG_COUNT -> "Cantidad de canciones"
        PlaylistSortType.LAST_UPDATED -> "Última actualización"
    }
}

private fun isCachedName(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized == "en caché" || normalized == "en cache"
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
