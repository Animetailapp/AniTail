package com.anitail.desktop.ui.screen

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.PlaylistEntity
import com.anitail.desktop.db.entities.SongArtistMap
import com.anitail.desktop.db.entities.SongEntity
import com.anitail.desktop.db.mapper.extractVideoId
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.download.DownloadState
import com.anitail.desktop.download.DownloadStatus
import com.anitail.desktop.download.DownloadedSong
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.ui.component.ContextMenuAction
import com.anitail.desktop.ui.component.ItemContextMenu
import com.anitail.desktop.ui.component.HideOnScrollFAB
import com.anitail.desktop.ui.component.ArtistPickerDialog
import com.anitail.desktop.ui.component.MediaDetailsDialog
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.PlayingIndicatorBox
import com.anitail.desktop.ui.component.PlaylistPickerDialog
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.ui.component.ShimmerQuickPicksGrid
import com.anitail.desktop.ui.component.ShimmerSectionRow
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.home.localItemToYtItem
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.desktop.ui.utils.SnapLayoutInfoProvider
import com.anitail.desktop.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.Artist
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

// Constantes de dimensiones
private val ListItemHeight = 64.dp
private val ListThumbnailSize = 48.dp
private val GridThumbnailHeight = 128.dp
private val ThumbnailCornerRadius = 6.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homePage: HomePage?,
    selectedChip: HomePage.Chip?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    syncStatus: String?,
    isSyncing: Boolean,
    isLoadingMore: Boolean,
    quickPicks: List<LibraryItem>,
    keepListening: List<LibraryItem>,
    forgottenFavorites: List<LibraryItem>,
    accountPlaylists: List<PlaylistItem>,
    similarRecommendations: List<SimilarRecommendation>,
    playerState: PlayerState,
    accountName: String? = null,
    accountThumbnailUrl: String? = null,
    database: DesktopDatabase,
    downloadService: DesktopDownloadService,
    playlists: List<PlaylistEntity>,
    songsById: Map<String, SongEntity>,
    songArtistMaps: List<SongArtistMap>,
    onChipSelected: (HomePage.Chip?) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onLocalItemSelected: (LibraryItem) -> Unit,
    onItemSelected: (YTItem) -> Unit,
    onShuffleAll: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val downloadedSongs by downloadService.downloadedSongs.collectAsState()
    val downloadStates by downloadService.downloadStates.collectAsState()
    var pendingPlaylistItem by remember { mutableStateOf<HomeSongTarget?>(null) }
    var detailsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var pendingArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    val strings = LocalStrings.current

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }

    fun openInBrowser(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    suspend fun ensureSongInDatabase(target: HomeSongTarget) {
        if (songsById.containsKey(target.item.id)) return
        val entity = target.songItem?.toSongEntity(inLibrary = true) ?: target.item.toSongEntity()
        if (target.songItem != null) {
            database.insertSong(entity, target.songItem.toSongArtistMaps())
        } else {
            database.insertSong(entity)
        }
    }

    fun menuActionsForSong(
        libraryItem: LibraryItem,
        songItem: SongItem?,
    ): List<ContextMenuAction> {
        val songEntity = songsById[libraryItem.id]
        val artistCandidates = songItem?.artists
            ?.mapNotNull { artist -> artist.id?.let { Artist(artist.name, it) } }
            .orEmpty()
        val libraryArtistId = songEntity?.let { primaryArtistIdForSong(libraryItem.id, songArtistMaps) }
        val hasArtist = if (songItem != null) {
            artistCandidates.isNotEmpty()
        } else {
            !libraryArtistId.isNullOrBlank()
        }
        val hasAlbum = if (songItem != null) {
            !songItem.album?.id.isNullOrBlank()
        } else {
            !songEntity?.albumId.isNullOrBlank()
        }
        val canStartRadio = !(songItem?.id ?: extractVideoId(libraryItem.playbackUrl)).isNullOrBlank()
        val availability = HomeSongMenuAvailability(
            canStartRadio = canStartRadio,
            canAddToPlaylist = true,
            hasArtist = hasArtist,
            hasAlbum = hasAlbum,
        )
        val order = buildHomeSongMenuOrder(availability)
        val downloadState = resolveDownloadMenuState(
            strings = strings,
            songId = libraryItem.id,
            downloadStates = downloadStates,
            downloadedSongs = downloadedSongs,
        )
        val isInLibrary = songEntity?.inLibrary != null
        val shareUrl = songItem?.shareLink ?: libraryItem.playbackUrl

        return buildHomeSongMenuActions(
            strings = strings,
            order = order,
            downloadState = downloadState,
            isInLibrary = isInLibrary,
            onStartRadio = startRadio@{
                val videoId = songItem?.id ?: extractVideoId(libraryItem.playbackUrl) ?: return@startRadio
                coroutineScope.launch {
                    val result = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return@launch
                    val plan = buildRadioQueuePlan(libraryItem, result)
                    playerState.playQueue(plan.items, plan.startIndex)
                }
            },
            onPlayNext = { playerState.addToQueue(libraryItem, playNext = true) },
            onAddToQueue = { playerState.addToQueue(libraryItem) },
            onAddToPlaylist = { pendingPlaylistItem = HomeSongTarget(libraryItem, songItem) },
            onDownload = {
                val durationSec = ((libraryItem.durationMs ?: 0L) / 1000L).toInt()
                val albumName = songItem?.album?.name ?: songEntity?.albumName
                downloadService.downloadSong(
                    songId = libraryItem.id,
                    title = libraryItem.title,
                    artist = libraryItem.artist,
                    album = albumName,
                    thumbnailUrl = libraryItem.artworkUrl,
                    duration = durationSec,
                )
            },
            onToggleLibrary = {
                coroutineScope.launch {
                    if (songEntity == null) {
                        val entity = songItem?.toSongEntity(inLibrary = true) ?: libraryItem.toSongEntity()
                        if (songItem != null) {
                            database.insertSong(entity, songItem.toSongArtistMaps())
                        } else {
                            database.insertSong(entity)
                        }
                    } else {
                        database.toggleSongInLibrary(libraryItem.id)
                    }
                }
            },
            onOpenArtist = openArtist@{
                when {
                    artistCandidates.size == 1 -> {
                        val artist = artistCandidates.first()
                        val artistId = artist.id ?: return@openArtist
                        onOpenArtist(artistId, artist.name)
                    }
                    artistCandidates.size > 1 -> {
                        pendingArtists = artistCandidates
                    }
                    !libraryArtistId.isNullOrBlank() -> {
                        val artistId = libraryArtistId ?: return@openArtist
                        onOpenArtist(artistId, songEntity?.artistName)
                    }
                }
            },
            onOpenAlbum = openAlbum@{
                when {
                    !songItem?.album?.id.isNullOrBlank() -> {
                        val albumId = songItem.album?.id ?: return@openAlbum
                        onOpenAlbum(albumId, songItem.album?.name)
                    }
                    !songEntity?.albumId.isNullOrBlank() -> {
                        val albumId = songEntity.albumId ?: return@openAlbum
                        onOpenAlbum(albumId, songEntity.albumName)
                    }
                }
            },
            onShare = { copyToClipboard(shareUrl) },
            onDetails = { detailsItem = libraryItem },
        )
    }

    val menuActionsForSongItem: (SongItem) -> List<ContextMenuAction> = { song ->
        val libraryItem = songItemToLibraryItem(song)
        menuActionsForSong(libraryItem, song)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    onLoadMore()
                }
            }
    }


    val pullRefreshState = rememberPullToRefreshState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        val horizontalLazyGridItemWidthFactor =
            computeHorizontalLazyGridItemWidthFactor(maxWidth)
        val horizontalLazyGridItemWidth =
            computeHorizontalLazyGridItemWidth(maxWidth)
        val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState, horizontalLazyGridItemWidthFactor) {
            SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider =
            remember(forgottenFavoritesLazyGridState, horizontalLazyGridItemWidthFactor) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }
        val quickPicksFlingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider)
        val forgottenFavoritesFlingBehavior =
            rememberSnapFlingBehavior(forgottenFavoritesSnapLayoutInfoProvider)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp),
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

            // 1. Quick Picks (Grid of List Items)
            if (quickPicks.isNotEmpty()) {
                item { NavigationTitle(title = stringResource("quick_picks")) }
                item {
                    QuickPicksGrid(
                        items = quickPicks,
                        playerState = playerState,
                        onPrimary = { onLocalItemSelected(it) },
                        menuActions = { item -> menuActionsForSong(item, null) },
                        rows = 4,
                        gridState = quickPicksLazyGridState,
                        flingBehavior = quickPicksFlingBehavior,
                        itemWidth = horizontalLazyGridItemWidth,
                    )
                }
            } else if (isLoading && homePage == null) {
                 item { NavigationTitle(title = stringResource("quick_picks")) }
                 item { ShimmerQuickPicksGrid() }
            }

            // 2. Keep Listening (Grid of Cards)
            if (keepListening.isNotEmpty()) {
                item { NavigationTitle(title = stringResource("keep_listening")) }
                item {
                    KeepListeningRow(
                        items = keepListening,
                        playerState = playerState,
                        onOpen = { onLocalItemSelected(it) },
                        menuActions = { item -> menuActionsForSong(item, null) },
                    )
                }
            } else if (isLoading && homePage == null) {
                item { NavigationTitle(title = stringResource("keep_listening")) }
                item { ShimmerSectionRow() }
            }

            // 3. Account Playlists (Row of Cards)
            if (accountPlaylists.isNotEmpty()) {
                item {
                    NavigationTitle(
                        title = accountName ?: stringResource("avatar_source_youtube"),
                        label = stringResource("your_ytb_playlists"),
                        thumbnail = accountThumbnailUrl?.let { url ->
                            {
                                RemoteImage(
                                    url = url,
                                    modifier = Modifier.size(ListThumbnailSize),
                                    shape = CircleShape,
                                )
                            }
                        },
                        onClick = { onNavigate("account") },
                    )
                }
                item {
                    HomeSectionRow(
                        items = accountPlaylists,
                        onItemSelected = onItemSelected,
                        menuActionsForSong = menuActionsForSongItem,
                    )
                }
            }

            // 4. Forgotten Favorites (Grid of List Items)
            if (forgottenFavorites.isNotEmpty()) {
                item { NavigationTitle(title = stringResource("forgotten_favorites")) }
                item {
                    val rows = minOf(4, forgottenFavorites.size)
                    QuickPicksGrid(
                        items = forgottenFavorites,
                        playerState = playerState,
                        onPrimary = { onLocalItemSelected(it) },
                        menuActions = { item -> menuActionsForSong(item, null) },
                        rows = rows,
                        gridState = forgottenFavoritesLazyGridState,
                        flingBehavior = forgottenFavoritesFlingBehavior,
                        itemWidth = horizontalLazyGridItemWidth,
                    )
                }
            }

            // 5. Similar Recommendations (Row of Cards)
            similarRecommendations.forEach { recommendation ->
                item {
                    val shape = if (recommendation.isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                    NavigationTitle(
                        title = recommendation.title,
                        label = stringResource("similar_to"),
                        thumbnail = if (recommendation.thumbnailUrl != null) {
                            {
                                RemoteImage(
                                    url = recommendation.thumbnailUrl,
                                    modifier = Modifier.size(ListThumbnailSize),
                                    shape = shape,
                                )
                            }
                        } else null,
                        onClick = recommendation.sourceItem?.let { source ->
                            { onLocalItemSelected(source) }
                        },
                    )
                }
                item {
                    HomeSectionRow(
                        items = recommendation.items,
                        onItemSelected = onItemSelected,
                        menuActionsForSong = menuActionsForSongItem,
                    )
                }
            }

            // 6. Remote Sections (Rows/Grids)
            homePage?.sections?.forEach { section ->
                item {
                    val shape = if (section.items.all { it is ArtistItem }) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                    NavigationTitle(
                        title = section.title.orEmpty(),
                        label = section.label,
                        thumbnail = if (section.thumbnail != null) {
                            {
                                RemoteImage(
                                    url = section.thumbnail,
                                    modifier = Modifier.size(ListThumbnailSize),
                                    shape = shape,
                                )
                            }
                        } else null,
                        onClick = section.endpoint?.let { endpoint -> 
                            { 
                                endpoint.browseId?.let { id ->
                                    if (id == "FEmusic_moods_and_genres") onNavigate("moods_and_genres")
                                    else if (id == "FEmusic_charts") onNavigate("charts")
                                    else onNavigate("browse/$id")
                                }
                            } 
                        }
                    )
                }
                item {
                    val isGrid = section.title?.equals("Quick picks", ignoreCase = true) == true
                    if (isGrid) {
                        val libraryItems = section.items.mapNotNull { it.toLibraryItem() }
                        val songItemsById = section.items.filterIsInstance<SongItem>().associateBy { it.id }
                        val sectionGridState = rememberLazyGridState()
                        val sectionSnapLayoutInfoProvider =
                            remember(sectionGridState, horizontalLazyGridItemWidthFactor) {
                                SnapLayoutInfoProvider(
                                    lazyGridState = sectionGridState,
                                    positionInLayout = { layoutSize, itemSize ->
                                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                    }
                                )
                            }
                        val sectionFlingBehavior = rememberSnapFlingBehavior(sectionSnapLayoutInfoProvider)
                        QuickPicksGrid(
                            items = libraryItems,
                            playerState = playerState,
                            onPrimary = { onItemSelected(localItemToYtItem(it)) },
                            menuActions = menu@{ item ->
                                val songItem = songItemsById[item.id] ?: return@menu emptyList()
                                menuActionsForSong(item, songItem)
                            },
                            rows = 4,
                            gridState = sectionGridState,
                            flingBehavior = sectionFlingBehavior,
                            itemWidth = horizontalLazyGridItemWidth,
                        )
                    } else {
                        HomeSectionRow(
                            items = section.items,
                            onItemSelected = onItemSelected,
                            menuActionsForSong = menuActionsForSongItem,
                        )
                    }
                }
            }
            
            if (isLoadingMore || isLoading || (homePage?.continuation != null && homePage.sections.isNotEmpty())) {
                item { ShimmerSectionRow() }
            }
        }

        Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )

        if (syncStatus != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSyncing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .weight(0.2f)
                                .padding(end = 12.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    } else {
                        Icon(
                            imageVector = IconAssets.check(),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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

        pendingPlaylistItem?.let { target ->
            PlaylistPickerDialog(
                visible = true,
                playlists = playlists,
                onCreatePlaylist = { name ->
                    val playlist = PlaylistEntity(name = name)
                    coroutineScope.launch {
                        database.insertPlaylist(playlist)
                        ensureSongInDatabase(target)
                        database.addSongToPlaylist(playlist.id, target.item.id)
                    }
                    pendingPlaylistItem = null
                },
                onSelectPlaylist = { playlist ->
                    coroutineScope.launch {
                        ensureSongInDatabase(target)
                        database.addSongToPlaylist(playlist.id, target.item.id)
                    }
                    pendingPlaylistItem = null
                },
                onDismiss = { pendingPlaylistItem = null },
            )
        }

        ArtistPickerDialog(
            visible = pendingArtists.isNotEmpty(),
            artists = pendingArtists,
            onSelect = selectArtist@{ artist ->
                val artistId = artist.id ?: return@selectArtist
                onOpenArtist(artistId, artist.name)
                pendingArtists = emptyList()
            },
            onDismiss = { pendingArtists = emptyList() },
        )

        detailsItem?.let { item ->
            MediaDetailsDialog(
                visible = true,
                item = item,
                onCopyLink = { copyToClipboard(item.playbackUrl) },
                onOpenInBrowser = { openInBrowser(item.playbackUrl) },
                onDismiss = { detailsItem = null },
            )
        }
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
                ),
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<YTItem>,
    onItemSelected: (YTItem) -> Unit,
    menuActionsForSong: (SongItem) -> List<ContextMenuAction>,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(items) { item ->
            HomeItemCard(
                item = item,
                onPrimary = { onItemSelected(item) },
                menuActionsForSong = menuActionsForSong,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeItemCard(
    item: YTItem,
    onPrimary: () -> Unit,
    menuActionsForSong: (SongItem) -> List<ContextMenuAction>,
) {
    val isArtist = item is ArtistItem
    val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
    val (title, subtitle) = when (item) {
        is SongItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is AlbumItem -> item.title to item.artists?.joinToString { it.name }.orEmpty()
        is ArtistItem -> item.title to stringResource("artist")
        is PlaylistItem -> item.title to item.author?.name.orEmpty()
        else -> "" to ""
    }

    val libraryItem = item.toLibraryItem()
    val contextMenuItems = when (item) {
        is SongItem -> toContextMenuItems(menuActionsForSong(item))
        else -> {
            if (libraryItem != null) {
                listOf(ContextMenuItem(stringResource("open")) { onPrimary() })
            } else {
                emptyList()
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPicksGrid(
    items: List<LibraryItem>,
    playerState: PlayerState,
    onPrimary: (LibraryItem) -> Unit,
    menuActions: (LibraryItem) -> List<ContextMenuAction>,
    rows: Int = 4,
    gridState: LazyGridState,
    flingBehavior: FlingBehavior,
    itemWidth: Dp,
) {
    val itemHeight = ListItemHeight
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        state = gridState,
        flingBehavior = flingBehavior,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * rows),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(items) { item ->
            QuickPickRowItem(
                item = item,
                playerState = playerState,
                onPrimary = { onPrimary(item) },
                menuActions = menuActions(item),
                itemWidth = itemWidth,
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
    menuActions: List<ContextMenuAction>,
    itemWidth: Dp,
) {
    val isCurrentlyPlaying = playerState.currentItem?.id == item.id
    val menuExpanded = remember(item.id) { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        modifier = Modifier
            .width(itemWidth)
            .height(ListItemHeight)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(
                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable {
                if (isCurrentlyPlaying) {
                    playerState.togglePlayPause()
                } else {
                    onPrimary()
                }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
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
        Box {
            IconButton(onClick = { menuExpanded.value = true }) {
                Icon(IconAssets.moreVert(), contentDescription = stringResource("more_options"))
            }
            ItemContextMenu(
                expanded = menuExpanded.value,
                onDismiss = { menuExpanded.value = false },
                item = item,
                actions = menuActions,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeepListeningRow(
    items: List<LibraryItem>,
    playerState: PlayerState,
    onOpen: (LibraryItem) -> Unit,
    menuActions: (LibraryItem) -> List<ContextMenuAction>,
) {
    val rows = if (items.size > 6) 2 else 1
    val itemSpacing = 12.dp
    val itemHeight = GridThumbnailHeight + with(androidx.compose.ui.platform.LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
    }
    val gridHeight = itemHeight * rows + itemSpacing * (rows - 1)

    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        items(items) { item ->
            val isArtist = item.playbackUrl.contains("/channel/")
            val shape = if (isArtist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
            val contextMenuItems = toContextMenuItems(menuActions(item))

            ContextMenuArea(items = { contextMenuItems }) {
                Column(
                    modifier = Modifier
                        .width(GridThumbnailHeight)
                        .clickable {
                            if (playerState.currentItem?.id == item.id) {
                                playerState.togglePlayPause()
                            } else {
                                onOpen(item)
                            }
                        },
                ) {
                    Box {
                        RemoteImage(
                            url = item.artworkUrl,
                            modifier = Modifier
                                .size(GridThumbnailHeight)
                                .clip(shape),
                            shape = shape,
                        )
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

private fun YTItem.toLibraryItem(): LibraryItem? {
    return when (this) {
        is SongItem -> LibraryItem(
            id = id,
            title = title,
            artist = artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = thumbnail,
            playbackUrl = "https://music.youtube.com/watch?v=${id}",
            durationMs = duration?.toLong()?.times(1000L),
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

data class DownloadMenuState(
    val label: String,
    val enabled: Boolean,
)

private data class HomeSongTarget(
    val item: LibraryItem,
    val songItem: SongItem?,
)

fun resolveDownloadMenuState(
    strings: StringResolver,
    songId: String,
    downloadStates: Map<String, DownloadState>,
    downloadedSongs: List<DownloadedSong>,
): DownloadMenuState {
    val isDownloaded = downloadedSongs.any { it.songId == songId }
    val status = downloadStates[songId]?.status
    val isDownloading = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
    val label = when {
        isDownloaded -> strings.get("downloaded_to_device")
        isDownloading -> strings.get("downloading_to_device")
        else -> strings.get("download")
    }
    val enabled = !isDownloaded && !isDownloading
    return DownloadMenuState(label = label, enabled = enabled)
}

fun buildHomeSongMenuActions(
    strings: StringResolver,
    order: List<HomeSongMenuActionId>,
    downloadState: DownloadMenuState,
    isInLibrary: Boolean,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onToggleLibrary: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
): List<ContextMenuAction> {
    return order.map { actionId ->
        when (actionId) {
            HomeSongMenuActionId.START_RADIO -> ContextMenuAction(
                label = strings.get("start_radio"),
                icon = IconAssets.radio(),
                onClick = onStartRadio,
            )
            HomeSongMenuActionId.PLAY_NEXT -> ContextMenuAction(
                label = strings.get("play_next"),
                icon = IconAssets.queueMusic(),
                onClick = onPlayNext,
            )
            HomeSongMenuActionId.ADD_TO_QUEUE -> ContextMenuAction(
                label = strings.get("add_to_queue"),
                icon = IconAssets.playlistAdd(),
                onClick = onAddToQueue,
            )
            HomeSongMenuActionId.ADD_TO_PLAYLIST -> ContextMenuAction(
                label = strings.get("add_to_playlist"),
                icon = IconAssets.playlistAdd(),
                onClick = onAddToPlaylist,
            )
            HomeSongMenuActionId.DOWNLOAD -> ContextMenuAction(
                label = downloadState.label,
                icon = IconAssets.download(),
                onClick = onDownload,
                enabled = downloadState.enabled,
            )
            HomeSongMenuActionId.TOGGLE_LIBRARY -> ContextMenuAction(
                label = if (isInLibrary) strings.get("remove_from_library") else strings.get("add_to_library"),
                icon = IconAssets.add(),
                onClick = onToggleLibrary,
            )
            HomeSongMenuActionId.VIEW_ARTIST -> ContextMenuAction(
                label = strings.get("view_artist"),
                icon = IconAssets.artist(),
                onClick = onOpenArtist,
            )
            HomeSongMenuActionId.VIEW_ALBUM -> ContextMenuAction(
                label = strings.get("view_album"),
                icon = IconAssets.album(),
                onClick = onOpenAlbum,
            )
            HomeSongMenuActionId.SHARE -> ContextMenuAction(
                label = strings.get("share"),
                icon = IconAssets.share(),
                onClick = onShare,
            )
            HomeSongMenuActionId.DETAILS -> ContextMenuAction(
                label = strings.get("details"),
                icon = IconAssets.info(),
                onClick = onDetails,
            )
        }
    }
}

fun toContextMenuItems(actions: List<ContextMenuAction>): List<ContextMenuItem> {
    return actions
        .filter { it.enabled }
        .map { action -> ContextMenuItem(action.label, action.onClick) }
}

fun songItemToLibraryItem(item: SongItem): LibraryItem {
    return item.toSongEntity().toLibraryItem()
}
