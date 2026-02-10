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
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibrarySongListItem
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.PlaylistPickerDialog
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
fun LibraryDownloadedScreen(
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playerState: PlayerState,
    preferences: DesktopPreferences,
    onDeselect: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playlists by database.playlists.collectAsState(initial = emptyList())
    val songs by database.songs.collectAsState(initial = emptyList())
    val downloadStates by downloadService.downloadStates.collectAsState()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val strings = LocalStrings.current

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val entries = remember(downloadedSongs, songsById) {
        downloadedSongs
            .sortedByDescending { it.downloadedAt }
            .map { downloaded ->
                val songEntity = songsById[downloaded.songId] ?: downloaded.toFallbackSong()
                val libraryItem = LibraryItem(
                    id = downloaded.songId,
                    title = downloaded.title,
                    artist = downloaded.artist,
                    artworkUrl = downloaded.thumbnailUrl,
                    playbackUrl = downloaded.filePath,
                    durationMs = downloaded.duration.toLong() * 1000L,
                )
                DownloadedSongEntry(downloaded, songEntity, libraryItem)
            }
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
            item(key = "filter") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource("filter_downloaded")) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(imageVector = IconAssets.close(), contentDescription = null) },
                    )
                }
            }

            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = pluralStringResource("n_song", entries.size, entries.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource("library_downloads_empty_title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(entries, key = { _, entry -> entry.song.id }) { index, entry ->
                    val menuExpanded = remember(entry.song.id) { mutableStateOf(false) }
                    val menuActions = remember(entry.song.id, downloadStates, downloadedSongs, strings) {
                        val base = buildBrowseSongMenuActions(
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
                        base + ContextMenuAction(
                            label = strings.get("remove_download"),
                            icon = IconAssets.delete(),
                            onClick = { downloadService.deleteDownload(entry.downloaded.songId) },
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
                            downloaded = true,
                            isActive = entry.song.id == playerState.currentItem?.id,
                            isPlaying = playerState.isPlaying,
                            isSelected = false,
                            onClick = {
                                if (entry.song.id == playerState.currentItem?.id) {
                                    playerState.togglePlayPause()
                                } else {
                                    val items = entries.map { it.libraryItem }
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

private data class DownloadedSongEntry(
    val downloaded: DownloadedSong,
    val song: SongEntity,
    val libraryItem: LibraryItem,
)

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
