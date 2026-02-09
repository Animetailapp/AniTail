package com.anitail.desktop.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.ArtistEntity
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ChipsRow
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.LibraryArtistGridItem
import com.anitail.desktop.ui.component.LibraryArtistListItem
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.SortHeader
import com.anitail.desktop.ui.component.pluralizeSongs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistsScreen(
    artists: List<ArtistEntity>,
    songs: List<SongEntity>,
    playerState: PlayerState,
    preferences: DesktopPreferences,
    database: DesktopDatabase,
    onDeselect: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val filter by preferences.artistFilter.collectAsState()
    val sortType by preferences.artistSortType.collectAsState()
    val sortDescending by preferences.artistSortDescending.collectAsState()
    val viewType by preferences.artistViewType.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()

    val stats = remember(songs) { buildArtistSongStats(songs) }
    val songCountByArtist = remember(artists, stats) {
        artists.associate { artist -> artist.id to artistSongCount(artist, stats) }
    }
    val playTimeByArtist = remember(artists, stats) {
        artists.associate { artist -> artist.id to artistPlayTime(artist, stats) }
    }

    val filteredArtists = remember(artists, filter) {
        when (filter) {
            ArtistFilter.LIKED -> artists.filter { it.bookmarkedAt != null }
            ArtistFilter.LIBRARY -> artists
        }
    }
    val sortedArtists = remember(filteredArtists, sortType, sortDescending, songCountByArtist, playTimeByArtist) {
        sortArtists(
            artists = filteredArtists,
            sortType = sortType,
            descending = sortDescending,
            songCountByArtist = songCountByArtist,
            playTimeByArtist = playTimeByArtist,
        )
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    when (viewType) {
        LibraryViewType.LIST -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "filter") {
                    ArtistFilterRow(
                        filter = filter,
                        onFilterChange = { preferences.setArtistFilter(it) },
                        onDeselect = onDeselect,
                    )
                }

                item(key = "header") {
                    ArtistHeaderRow(
                        artistCount = sortedArtists.size,
                        sortType = sortType,
                        sortDescending = sortDescending,
                        viewType = viewType,
                        onSortTypeChange = { preferences.setArtistSortType(it) },
                        onSortDescendingChange = { preferences.setArtistSortDescending(it) },
                        onToggleView = { preferences.setArtistViewType(viewType.toggle()) },
                    )
                }

                if (sortedArtists.isEmpty()) {
                    item(key = "empty") {
                        EmptyArtistsPlaceholder()
                    }
                } else {
                    items(
                        items = sortedArtists,
                        key = { it.id },
                    ) { artist ->
                        val menuExpanded = remember(artist.id) { mutableStateOf(false) }
                        val songCount = songCountByArtist[artist.id] ?: 0
                        val menuActions = buildArtistMenuActions(
                            artist = artist,
                            database = database,
                            scope = scope,
                        )
                        Box {
                            LibraryArtistListItem(
                                artist = artist,
                                songCount = songCount,
                                showLikedBadge = artist.bookmarkedAt != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onArtistClick(artist.id, artist.name) },
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
                                item = artist.toLibraryItem(),
                                actions = menuActions,
                                headerContent = {
                                    LibraryArtistMenuHeader(
                                        artist = artist,
                                        songCount = songCount,
                                        onPlay = {
                                            val queue = buildArtistQueue(artist, songs)
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = false
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShuffle = {
                                            val queue = buildArtistQueue(artist, songs).shuffled()
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = true
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShare = {
                                            val url = "https://music.youtube.com/channel/${artist.id}"
                                            clipboard.setText(AnnotatedString(url))
                                        },
                                        onDismiss = { menuExpanded.value = false },
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
                item(
                    key = "filter",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    ArtistFilterRow(
                        filter = filter,
                        onFilterChange = { preferences.setArtistFilter(it) },
                        onDeselect = onDeselect,
                    )
                }

                item(
                    key = "header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    ArtistHeaderRow(
                        artistCount = sortedArtists.size,
                        sortType = sortType,
                        sortDescending = sortDescending,
                        viewType = viewType,
                        onSortTypeChange = { preferences.setArtistSortType(it) },
                        onSortDescendingChange = { preferences.setArtistSortDescending(it) },
                        onToggleView = { preferences.setArtistViewType(viewType.toggle()) },
                    )
                }

                if (sortedArtists.isEmpty()) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        EmptyArtistsPlaceholder()
                    }
                } else {
                    items(
                        items = sortedArtists,
                        key = { it.id },
                    ) { artist ->
                        val menuExpanded = remember(artist.id) { mutableStateOf(false) }
                        val songCount = songCountByArtist[artist.id] ?: 0
                        val menuActions = buildArtistMenuActions(
                            artist = artist,
                            database = database,
                            scope = scope,
                        )
                        Box {
                            LibraryArtistGridItem(
                                artist = artist,
                                songCount = songCount,
                                showLikedBadge = artist.bookmarkedAt != null,
                                gridItemSize = gridItemSize,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onArtistClick(artist.id, artist.name) },
                                    onLongClick = { menuExpanded.value = true },
                                ),
                            )

                            ItemContextMenu(
                                expanded = menuExpanded.value,
                                onDismiss = { menuExpanded.value = false },
                                item = artist.toLibraryItem(),
                                actions = menuActions,
                                headerContent = {
                                    LibraryArtistMenuHeader(
                                        artist = artist,
                                        songCount = songCount,
                                        onPlay = {
                                            val queue = buildArtistQueue(artist, songs)
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = false
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShuffle = {
                                            val queue = buildArtistQueue(artist, songs).shuffled()
                                            if (queue.isNotEmpty()) {
                                                playerState.shuffleEnabled = true
                                                playerState.playQueue(queue, 0)
                                            }
                                        },
                                        onShare = {
                                            val url = "https://music.youtube.com/channel/${artist.id}"
                                            clipboard.setText(AnnotatedString(url))
                                        },
                                        onDismiss = { menuExpanded.value = false },
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

@Composable
private fun ArtistFilterRow(
    filter: ArtistFilter,
    onFilterChange: (ArtistFilter) -> Unit,
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
            label = { Text("Artistas") },
            selected = true,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            onClick = onDeselect,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(imageVector = IconAssets.close(), contentDescription = null)
            },
        )
        ChipsRow(
            chips = listOf(
                ArtistFilter.LIKED to "Me gusta",
                ArtistFilter.LIBRARY to "Biblioteca",
            ),
            currentValue = filter,
            onValueUpdate = onFilterChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ArtistHeaderRow(
    artistCount: Int,
    sortType: ArtistSortType,
    sortDescending: Boolean,
    viewType: LibraryViewType,
    onSortTypeChange: (ArtistSortType) -> Unit,
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
            sortTypeText = ::artistSortLabel,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (artistCount == 1) "1 artista" else "$artistCount artistas",
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
private fun LibraryArtistMenuHeader(
    artist: ArtistEntity,
    songCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            RemoteImage(
                url = artist.thumbnailUrl,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp, bottom = 4.dp),
        ) {
            if (artist.bookmarkedAt != null) {
                Icon(
                    imageVector = IconAssets.favorite(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp),
                )
            }
            Text(
                text = pluralizeSongs(songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val canPlay = songCount > 0
        val canShare = artist.isYouTubeArtist
        if (canPlay || canShare) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canPlay) {
                    MenuQuickActionButton(
                        label = "Reproducir",
                        icon = IconAssets.play(),
                        onClick = {
                            onPlay()
                            onDismiss()
                        },
                    )
                    MenuQuickActionButton(
                        label = "Aleatorio",
                        icon = IconAssets.shuffle(),
                        onClick = {
                            onShuffle()
                            onDismiss()
                        },
                    )
                }
                if (canShare) {
                    MenuQuickActionButton(
                        label = "Compartir",
                        icon = IconAssets.share(),
                        onClick = {
                            onShare()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MenuQuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EmptyArtistsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = IconAssets.person(),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "No hay artistas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Los artistas que sigas aparecerán aquí",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private data class ArtistSongStats(
    val countById: Map<String, Int>,
    val countByName: Map<String, Int>,
    val playTimeById: Map<String, Long>,
    val playTimeByName: Map<String, Long>,
)

private fun buildArtistSongStats(songs: List<SongEntity>): ArtistSongStats {
    val countById = mutableMapOf<String, Int>()
    val countByName = mutableMapOf<String, Int>()
    val playTimeById = mutableMapOf<String, Long>()
    val playTimeByName = mutableMapOf<String, Long>()

    songs.forEach { song ->
        val playTime = song.totalPlayTime
        val artistId = song.artistId
        val artistName = song.artistName
        if (!artistId.isNullOrBlank()) {
            countById[artistId] = (countById[artistId] ?: 0) + 1
            playTimeById[artistId] = (playTimeById[artistId] ?: 0L) + playTime
        } else if (!artistName.isNullOrBlank()) {
            val key = artistName.lowercase()
            countByName[key] = (countByName[key] ?: 0) + 1
            playTimeByName[key] = (playTimeByName[key] ?: 0L) + playTime
        }
    }

    return ArtistSongStats(
        countById = countById,
        countByName = countByName,
        playTimeById = playTimeById,
        playTimeByName = playTimeByName,
    )
}

private fun artistSongCount(artist: ArtistEntity, stats: ArtistSongStats): Int {
    return stats.countById[artist.id]
        ?: stats.countByName[artist.name.lowercase()]
        ?: 0
}

private fun artistPlayTime(artist: ArtistEntity, stats: ArtistSongStats): Long {
    return stats.playTimeById[artist.id]
        ?: stats.playTimeByName[artist.name.lowercase()]
        ?: 0L
}

private fun buildArtistQueue(
    artist: ArtistEntity,
    songs: List<SongEntity>,
): List<com.anitail.shared.model.LibraryItem> {
    val artistSongs = songs.filter { song ->
        when {
            !song.artistId.isNullOrBlank() -> song.artistId == artist.id
            !song.artistName.isNullOrBlank() -> song.artistName.equals(artist.name, ignoreCase = true)
            else -> false
        }
    }
    val sorted = sortSongs(artistSongs, SongSortType.CREATE_DATE, descending = true)
    return sorted.map { it.toLibraryItem() }
}

private fun buildArtistMenuActions(
    artist: ArtistEntity,
    database: DesktopDatabase,
    scope: kotlinx.coroutines.CoroutineScope,
): List<ContextMenuAction> {
    val isSubscribed = artist.bookmarkedAt != null
    return listOf(
        ContextMenuAction(
            label = if (isSubscribed) "Suscrito" else "Suscribirse",
            icon = if (isSubscribed) IconAssets.subscribed() else IconAssets.subscribe(),
            onClick = {
                scope.launch {
                    database.toggleArtistBookmark(artist.id)
                }
            },
        ),
    )
}

private fun artistSortLabel(sortType: ArtistSortType): String {
    return when (sortType) {
        ArtistSortType.CREATE_DATE -> "Fecha de agregado"
        ArtistSortType.NAME -> "Nombre"
        ArtistSortType.SONG_COUNT -> "Cantidad de canciones"
        ArtistSortType.PLAY_TIME -> "Tiempo de reproducción"
    }
}
