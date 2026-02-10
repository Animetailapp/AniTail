package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
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
fun LibraryAutoPlaylistScreen(
    type: AutoPlaylistType,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    preferences: DesktopPreferences,
    onBack: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val songs by database.songs.collectAsState(initial = emptyList())
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val strings = LocalStrings.current

    val sortType by preferences.songSortType.collectAsState()
    val sortDescending by preferences.songSortDescending.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.map { it.songId }.toSet() }
    val baseSongs = remember(type, songs, downloadedSongs, songsById) {
        when (type) {
            AutoPlaylistType.LIKED -> songs.filter { it.liked }
            AutoPlaylistType.DOWNLOADED -> downloadedSongs.map { downloaded ->
                songsById[downloaded.songId] ?: downloaded.toFallbackSong()
            }
            AutoPlaylistType.TOP -> songs.sortedByDescending { it.totalPlayTime }.take(50)
        }
    }
    val sortedSongs = remember(baseSongs, sortType, sortDescending) {
        sortSongs(baseSongs, sortType, sortDescending)
    }
    val libraryItemsById = remember(type, baseSongs, downloadedSongs) {
        when (type) {
            AutoPlaylistType.DOWNLOADED -> downloadedSongs.associate { downloaded ->
                val item = LibraryItem(
                    id = downloaded.songId,
                    title = downloaded.title,
                    artist = downloaded.artist,
                    artworkUrl = downloaded.thumbnailUrl,
                    playbackUrl = downloaded.filePath,
                    durationMs = downloaded.duration.toLong() * 1000L,
                )
                downloaded.songId to item
            }
            else -> baseSongs.associate { song -> song.id to song.toLibraryItem() }
        }
    }
    val entries = remember(sortedSongs, libraryItemsById) {
        sortedSongs.mapNotNull { song ->
            libraryItemsById[song.id]?.let { item -> AutoPlaylistEntry(song, item) }
        }
    }
    val totalDurationMs = remember(entries) {
        entries.sumOf { it.song.duration.toLong() } * 1000L
    }
    val headerThumbnailUrl = remember(entries) { entries.firstOrNull()?.song?.thumbnailUrl }
    val playlistDownloadState = remember(entries, downloadStates, downloadedIds) {
        if (entries.isEmpty()) {
            PlaylistDownloadState.STOPPED
        } else if (entries.all { it.song.id in downloadedIds }) {
            PlaylistDownloadState.COMPLETED
        } else if (entries.any { entry ->
                downloadStates[entry.song.id]?.status in listOf(
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.QUEUED,
                )
            }
        ) {
            PlaylistDownloadState.DOWNLOADING
        } else {
            PlaylistDownloadState.STOPPED
        }
    }

    var pendingPlaylistItem by remember { mutableStateOf<BrowseSongTarget?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.song.title.contains(searchQuery, ignoreCase = true) ||
                    (entry.song.artistName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

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

    fun queueAllSongs() {
        entries.forEach { entry ->
            playerState.addToQueue(entry.libraryItem)
        }
    }

    fun downloadAllSongs() {
        entries.forEach { entry ->
            downloadService.downloadSong(
                songId = entry.song.id,
                title = entry.song.title,
                artist = entry.song.artistName.orEmpty(),
                album = entry.song.albumName,
                thumbnailUrl = entry.song.thumbnailUrl,
                duration = entry.song.duration,
            )
        }
    }

    fun cancelAllDownloads() {
        entries.forEach { entry ->
            downloadService.cancelDownload(entry.song.id)
        }
    }

    fun deleteAllDownloads() {
        entries.forEach { entry ->
            downloadService.deleteDownload(entry.song.id)
        }
    }

    suspend fun ensureSongInDatabase(target: BrowseSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        database.insertSong(entity)
    }

    pendingPlaylistItem?.let { target ->
        PlaylistPickerDialog(
            visible = true,
            playlists = playlists.filterNot { isCachedName(it.name) },
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
            item(key = "topbar") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(IconAssets.arrowBack(), contentDescription = stringResource("back"))
                    }
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource("search")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = autoPlaylistTitle(type),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { isSearching = true }) {
                            Icon(IconAssets.search(), contentDescription = stringResource("search"))
                        }
                    }
                }
            }

            if (!isSearching && entries.isNotEmpty()) {
                item(key = "playlist_header") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .fillMaxWidth(),
                            ) {
                                if (!headerThumbnailUrl.isNullOrBlank()) {
                                    RemoteImage(
                                        url = headerThumbnailUrl,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Icon(
                                        imageVector = IconAssets.libraryMusic(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = autoPlaylistTitle(type),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 22.sp,
                                )
                                Text(
                                    text = pluralStringResource("n_song", entries.size, entries.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                                if (totalDurationMs > 0L) {
                                    Text(
                                        text = formatTime(totalDurationMs),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            when (playlistDownloadState) {
                                                PlaylistDownloadState.COMPLETED -> deleteAllDownloads()
                                                PlaylistDownloadState.DOWNLOADING -> cancelAllDownloads()
                                                PlaylistDownloadState.STOPPED -> downloadAllSongs()
                                            }
                                        },
                                    ) {
                                        when (playlistDownloadState) {
                                            PlaylistDownloadState.COMPLETED -> {
                                                Icon(
                                                    imageVector = IconAssets.offline(),
                                                    contentDescription = stringResource("downloaded_to_device"),
                                                )
                                            }
                                            PlaylistDownloadState.DOWNLOADING -> {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                            PlaylistDownloadState.STOPPED -> {
                                                Icon(
                                                    imageVector = IconAssets.download(),
                                                    contentDescription = stringResource("download"),
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = ::queueAllSongs) {
                                        Icon(
                                            imageVector = IconAssets.queueMusic(),
                                            contentDescription = stringResource("add_to_queue"),
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    val items = entries.map { it.libraryItem }
                                    if (items.isNotEmpty()) {
                                        playerState.playQueue(items, startIndex = 0)
                                    }
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = IconAssets.play(),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource("play"))
                            }

                            OutlinedButton(
                                onClick = {
                                    val items = entries.shuffled().map { it.libraryItem }
                                    if (items.isNotEmpty()) {
                                        playerState.shuffleEnabled = true
                                        playerState.playQueue(items, startIndex = 0)
                                    }
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = IconAssets.shuffle(),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource("shuffle"))
                            }
                        }
                    }
                }
            }

            if (!isSearching) {
                item(key = "sort") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        SortHeader(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = { preferences.setSongSortType(it) },
                            onSortDescendingChange = { preferences.setSongSortDescending(it) },
                            sortTypeText = { songSortLabel(strings, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            itemsIndexed(filteredEntries, key = { _, entry -> entry.song.id }) { index, entry ->
                val menuExpanded = remember(entry.song.id) { mutableStateOf(false) }
                val menuActions = remember(entry.song.id, downloadStates, downloadedSongs, strings) {
                    buildBrowseSongMenuActions(
                        strings = strings,
                        libraryItem = entry.libraryItem,
                        songItem = null,
                        songsById = songsById,
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
                    entry.song.artistName,
                    entry.song.duration.takeIf { it > 0 }?.let { formatTime(it * 1000L) },
                ).orEmpty()
                Box {
                    LibrarySongListItem(
                        song = entry.song,
                        showInLibraryIcon = true,
                        downloaded = entry.song.id in downloadedIds,
                        isActive = entry.song.id == playerState.currentItem?.id,
                        isPlaying = playerState.isPlaying,
                        isSelected = false,
                        onClick = {
                            if (entry.song.id == playerState.currentItem?.id) {
                                playerState.togglePlayPause()
                            } else {
                                val items = filteredEntries.map { it.libraryItem }
                                playerState.playQueue(items, startIndex = index)
                            }
                        },
                        onMenuClick = { menuExpanded.value = true },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ItemContextMenu(
                        expanded = menuExpanded.value,
                        onDismiss = { menuExpanded.value = false },
                        item = entry.libraryItem,
                        actions = menuActions,
                        headerContent = {
                            SongMenuHeader(
                                title = entry.song.title,
                                subtitle = subtitle,
                                thumbnailUrl = entry.song.thumbnailUrl,
                                isLiked = entry.song.liked,
                                onToggleLike = { scope.launch { database.toggleSongLike(entry.song.id) } },
                                onPlayNext = { playerState.addToQueue(entry.libraryItem, playNext = true) },
                                onAddToPlaylist = { pendingPlaylistItem = BrowseSongTarget(entry.libraryItem, null) },
                                onShare = { copyToClipboard(entry.libraryItem.playbackUrl) },
                                onDismiss = { menuExpanded.value = false },
                            )
                        },
                    )
                }
            }
        }

        HideOnScrollFAB(
            visible = entries.isNotEmpty(),
            lazyListState = listState,
            onClick = {
                val items = entries.shuffled().map { it.libraryItem }
                playerState.playQueue(items, startIndex = 0)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
    }
}

private data class AutoPlaylistEntry(
    val song: SongEntity,
    val libraryItem: LibraryItem,
)

private enum class PlaylistDownloadState {
    COMPLETED,
    DOWNLOADING,
    STOPPED,
}

@Composable
private fun autoPlaylistTitle(type: AutoPlaylistType): String {
    return when (type) {
        AutoPlaylistType.LIKED -> stringResource("liked_songs")
        AutoPlaylistType.DOWNLOADED -> stringResource("offline")
        AutoPlaylistType.TOP -> stringResource("my_top")
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

private fun isCachedName(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized == "en caché" || normalized == "en cache" || normalized == "cached"
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
