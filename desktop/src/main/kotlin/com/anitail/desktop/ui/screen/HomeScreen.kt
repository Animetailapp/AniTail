package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerChipsRow
import com.anitail.desktop.ui.component.ShimmerQuickPicksGrid
import com.anitail.desktop.ui.component.ShimmerSectionRow
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.HomePage
import com.anitail.desktop.db.mapper.extractChannelId
import com.anitail.desktop.db.mapper.extractPlaylistId
import com.anitail.shared.model.LibraryItem

// Constantes de dimensiones
private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val GridThumbnailHeight = 128.dp
private val ThumbnailCornerRadius = 6.dp

@Composable
fun HomeScreen(
    homePage: HomePage?,
    selectedChip: HomePage.Chip?,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    quickPicks: List<LibraryItem>,
    keepListening: List<LibraryItem>,
    forgottenFavorites: List<LibraryItem>,
    similarRecommendations: List<SimilarRecommendation>,
    playerState: PlayerState,
    onChipSelected: (HomePage.Chip?) -> Unit,
    onLoadMore: () -> Unit,
    onItemSelected: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
    onShuffleAll: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    onLoadMore()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            homePage?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
                item {
                    HomeChipsRow(
                        chips = chips,
                        selectedChip = selectedChip,
                        onSelected = onChipSelected,
                    )
                }
            }

            if (isLoading && homePage == null) {
                // Shimmer placeholders mientras carga
                item { ShimmerChipsRow() }
                item { NavigationTitle(title = "Quick picks") }
                item { ShimmerQuickPicksGrid() }
                item { NavigationTitle(title = "Keep listening") }
                item { ShimmerSectionRow() }
            }

            if (quickPicks.isNotEmpty()) {
                item { NavigationTitle(title = "Quick picks") }
                item {
                    QuickPicksGrid(
                        items = quickPicks,
                        playerState = playerState,
                        onPrimary = onItemSelected,
                        onSecondary = onAddToLibrary,
                    )
                }
            }

            if (keepListening.isNotEmpty()) {
                item { NavigationTitle(title = "Keep listening") }
                item {
                    KeepListeningRow(
                        items = keepListening,
                        playerState = playerState,
                        onOpen = onItemSelected,
                        onAddToLibrary = onAddToLibrary,
                    )
                }
            }

            if (forgottenFavorites.isNotEmpty()) {
                item { NavigationTitle(title = "Favoritos olvidados") }
                item {
                    QuickPicksGrid(
                        items = forgottenFavorites,
                        playerState = playerState,
                        onPrimary = onItemSelected,
                        onSecondary = onAddToLibrary,
                    )
                }
            }

            // Similar To sections
            similarRecommendations.forEach { recommendation ->
                item {
                    val shape = if (recommendation.isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                    NavigationTitle(
                        title = recommendation.title,
                        label = "Similar a",
                        thumbnail = if (recommendation.thumbnailUrl != null) {
                            {
                                RemoteImage(
                                    url = recommendation.thumbnailUrl,
                                    modifier = Modifier.size(ListThumbnailSize),
                                    shape = shape,
                                )
                            }
                        } else null,
                    )
                }
                item {
                    HomeSectionRow(
                        items = recommendation.items,
                        playerState = playerState,
                        onItemSelected = onItemSelected,
                        onAddToLibrary = onAddToLibrary,
                    )
                }
            }

            homePage?.sections?.forEach { section ->
                item {
                    NavigationTitle(
                        title = section.title,
                        label = section.label,
                    )
                }
                item {
                    HomeSectionRow(
                        items = section.items,
                        playerState = playerState,
                        onItemSelected = onItemSelected,
                        onAddToLibrary = onAddToLibrary,
                    )
                }
            }

            if (isLoadingMore) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Shuffle FAB
        HideOnScrollFAB(
            visible = quickPicks.isNotEmpty(),
            lazyListState = listState,
            onClick = onShuffleAll,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeChipsRow(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onSelected: (HomePage.Chip?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        chips.forEach { chip ->
            val selected = chip == selectedChip
            FilterChip(
                selected = selected,
                onClick = { onSelected(chip) },
                label = { Text(chip.title) },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<YTItem>,
    playerState: PlayerState,
    onItemSelected: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            HomeItemCard(
                item = item,
                playerState = playerState,
                onPrimary = { onItemSelected(item) },
                onSecondary = { onAddToLibrary(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeItemCard(
    item: YTItem,
    playerState: PlayerState? = null,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val isArtist = item is ArtistItem
    val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
    val (title, subtitle) = when (item) {
        is SongItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is AlbumItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is ArtistItem -> item.title to "Artista"
        is PlaylistItem -> item.title to item.author?.name.orEmpty()
        else -> "" to ""
    }

    val libraryItem = item.toLibraryItem()
    val contextMenuItems = remember(libraryItem, playerState) {
        if (libraryItem != null && playerState != null) {
            listOf(
                ContextMenuItem("Reproducir") { onPrimary() },
                ContextMenuItem("Reproducir siguiente") { playerState.addToQueue(libraryItem, playNext = true) },
                ContextMenuItem("Agregar a la cola") { playerState.addToQueue(libraryItem) },
                ContextMenuItem("Agregar a biblioteca") { onSecondary() },
            )
        } else if (libraryItem != null) {
            listOf(
                ContextMenuItem("Reproducir") { onPrimary() },
                ContextMenuItem("Agregar a biblioteca") { onSecondary() },
            )
        } else {
            emptyList()
        }
    }

    ContextMenuArea(items = { contextMenuItems }) {
        Column(
            modifier = Modifier
                .width(GridThumbnailHeight)
                .padding(12.dp)
                .clickable { onPrimary() },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(GridThumbnailHeight),
            ) {
                RemoteImage(
                    url = item.thumbnail,
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                )
                // Overlay play button for songs
                if (item is SongItem) {
                    Icon(
                        imageVector = IconAssets.play(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                shape = CircleShape,
                            )
                            .padding(6.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickPicksGrid(
    items: List<LibraryItem>,
    playerState: PlayerState,
    onPrimary: (YTItem) -> Unit,
    onSecondary: (YTItem) -> Unit,
) {
    val rows = 4
    val itemHeight = ListItemHeight
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * rows),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            QuickPickRowItem(
                item = item,
                playerState = playerState,
                onPrimary = { onPrimary(ytItemFromLibrary(item)) },
                onSecondary = { onSecondary(ytItemFromLibrary(item)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPickRowItem(
    item: LibraryItem,
    playerState: PlayerState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val isCurrentlyPlaying = playerState.currentItem?.id == item.id
    val contextMenuItems = remember(item) {
        listOf(
            ContextMenuItem("Reproducir") { onPrimary() },
            ContextMenuItem("Reproducir siguiente") { playerState.addToQueue(item, playNext = true) },
            ContextMenuItem("Agregar a la cola") { playerState.addToQueue(item) },
            ContextMenuItem("Agregar a biblioteca") { onSecondary() },
        )
    }

    ContextMenuArea(items = { contextMenuItems }) {
        Row(
            modifier = Modifier
                .width(320.dp)
                .height(ListItemHeight)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(
                    if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
                .clickable { onPrimary() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RemoteImage(
                    url = item.artworkUrl,
                    modifier = Modifier.size(ListThumbnailSize),
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                )
                PlayingIndicatorBox(
                    isActive = isCurrentlyPlaying,
                    isPlaying = playerState.isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(ListThumbnailSize)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                        ),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onSecondary) {
                Icon(IconAssets.moreVert(), contentDescription = "Menu")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeepListeningRow(
    items: List<LibraryItem>,
    playerState: PlayerState,
    onOpen: (YTItem) -> Unit,
    onAddToLibrary: (YTItem) -> Unit,
) {
    val rows = if (items.size > 6) 2 else 1
    val itemHeight = GridThumbnailHeight + 8.dp + 40.dp

    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * rows),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            val isArtist = item.playbackUrl.contains("/channel/")
            val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
            val ytItem = remember(item) { ytItemFromLibrary(item) }
            val contextMenuItems = remember(item) {
                if (ytItem is SongItem) {
                    listOf(
                        ContextMenuItem("Reproducir") { onOpen(ytItem) },
                        ContextMenuItem("Reproducir siguiente") { playerState.addToQueue(item, playNext = true) },
                        ContextMenuItem("Agregar a la cola") { playerState.addToQueue(item) },
                        ContextMenuItem("Agregar a biblioteca") { onAddToLibrary(ytItem) },
                    )
                } else {
                    listOf(
                        ContextMenuItem("Abrir") { onOpen(ytItem) },
                    )
                }
            }

            ContextMenuArea(items = { contextMenuItems }) {
                Column(
                    modifier = Modifier
                        .width(GridThumbnailHeight)
                        .clickable { onOpen(ytItem) },
                ) {
                    Box {
                        RemoteImage(
                            url = item.artworkUrl,
                            modifier = Modifier
                                .size(GridThumbnailHeight)
                                .clip(shape),
                            shape = shape,
                        )
                        if (!isArtist) {
                            Icon(
                                imageVector = IconAssets.play(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(28.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        shape = CircleShape,
                                    )
                                    .padding(4.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Funciones auxiliares
private fun ytItemFromLibrary(item: LibraryItem): YTItem {
    val playbackUrl = item.playbackUrl
    val playlistId = extractPlaylistId(playbackUrl)
    val channelId = extractChannelId(playbackUrl)

    if (!channelId.isNullOrBlank() || playbackUrl.contains("/channel/")) {
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

private fun YTItem.toLibraryItem(): LibraryItem? {
    return when (this) {
        is SongItem -> LibraryItem(
            id = id,
            title = title,
            artist = artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/watch?v=${id}",
        )
        is AlbumItem -> LibraryItem(
            id = browseId,
            title = title,
            artist = artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${playlistId}",
        )
        is PlaylistItem -> LibraryItem(
            id = id,
            title = title,
            artist = author?.name.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${id}",
        )
        is ArtistItem -> LibraryItem(
            id = id,
            title = title,
            artist = title,
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/channel/${id}",
        )
        else -> null
    }
}

private val YTItem.thumbnail: String?
    get() = when (this) {
        is SongItem -> thumbnail
        is AlbumItem -> thumbnail
        is ArtistItem -> thumbnail
        is PlaylistItem -> thumbnail
        else -> null
    }
