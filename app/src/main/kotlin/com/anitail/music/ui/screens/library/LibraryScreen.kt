@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.anitail.music.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.anitail.innertube.YouTube
import com.anitail.music.LocalDatabase
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.AlbumViewTypeKey
import com.anitail.music.constants.ChipSortTypeKey
import com.anitail.music.constants.LibraryFilter
import com.anitail.music.constants.LibraryGridColumnsKey
import com.anitail.music.constants.LibraryHiddenItemsKey
import com.anitail.music.constants.LibraryManualOrderKey
import com.anitail.music.constants.LibrarySortingMapKey
import com.anitail.music.constants.LibraryViewType
import com.anitail.music.constants.ShowCachedPlaylistKey
import com.anitail.music.constants.ShowDownloadedPlaylistKey
import com.anitail.music.constants.ShowLikedPlaylistKey
import com.anitail.music.constants.ShowLocalPlaylistKey
import com.anitail.music.constants.ShowTopPlaylistKey
import com.anitail.music.constants.ShowUploadedPlaylistKey
import com.anitail.music.constants.YtmSyncKey
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.extensions.togglePlayPause
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.ui.component.CreatePlaylistDialog
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.menu.AlbumMenu
import com.anitail.music.ui.menu.ArtistMenu
import com.anitail.music.ui.menu.PlaylistMenu
import com.anitail.music.ui.menu.SongMenu
import com.anitail.music.utils.DeviceUtils
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import com.anitail.music.viewmodels.UnifiedLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ScrollMoveMode
import sh.calvin.reorderable.rememberReorderableLazyGridState

private class LibraryReorderScrollConnection(
    private val isDragging: () -> Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (isDragging()) Offset(0f, available.y) else Offset.Zero
}

@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: UnifiedLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by (playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    val mediaMetadata by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })

    val uiState by viewModel.uiState.collectAsState()

    var prefFilter by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val selectedFilter = prefFilter.toExpressiveFilter()

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val isGridView = viewType == LibraryViewType.GRID

    var gridColumns by rememberPreference(LibraryGridColumnsKey, DEFAULT_LIBRARY_GRID_COLUMNS)
    var storedManualOrder by rememberPreference(LibraryManualOrderKey, "")
    var storedHiddenItems by rememberPreference(LibraryHiddenItemsKey, "")
    var storedSortingMap by rememberPreference(LibrarySortingMapKey, "")

    val showLiked by rememberPreference(ShowLikedPlaylistKey, true)
    val showDownloaded by rememberPreference(ShowDownloadedPlaylistKey, true)
    val showTop by rememberPreference(ShowTopPlaylistKey, true)
    val showCached by rememberPreference(ShowCachedPlaylistKey, true)
    val showUploaded by rememberPreference(ShowUploadedPlaylistKey, true)
    val showLocal by rememberPreference(ShowLocalPlaylistKey, true)
    val ytmSync by rememberPreference(YtmSyncKey, true)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        searchQuery = ""
    }
    BackHandler(enabled = !isSearchOpen && selectedFilter != null) {
        prefFilter = LibraryFilter.LIBRARY
    }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            viewModel.refresh()
        }
    }

    val sortingMap = remember(storedSortingMap) { decodeLibrarySortMap(storedSortingMap) }
    val sortConfig = remember(sortingMap, selectedFilter) {
        resolveLibrarySortConfig(sortingMap, selectedFilter)
    }
    val hiddenKeys = remember(storedHiddenItems) { decodeHiddenLibraryItems(storedHiddenItems) }

    var localManualOrder by remember(storedManualOrder) { mutableStateOf(storedManualOrder) }
    val reorderEnabled = sortConfig.sort == LibraryExpressiveSortType.MANUAL && searchQuery.isBlank()

    val autoPlaylists = remember(
        showLiked,
        showDownloaded,
        showTop,
        showCached,
        showUploaded,
        showLocal,
        uiState.hasLocalSongs,
        uiState.topSize,
        context,
    ) {
        buildList {
            if (showLiked) {
                add(
                    LibraryAutoPlaylistItem(
                        type = "liked",
                        title = context.getString(R.string.liked),
                        subtitle = context.getString(R.string.library_favorite_songs),
                        coverRes = libraryCoverDrawable("liked"),
                        fallbackIcon = fallbackIconForAutoPlaylist("liked"),
                        route = "auto_playlist/liked",
                    ),
                )
            }
            if (showDownloaded) {
                add(
                    LibraryAutoPlaylistItem(
                        type = "downloaded",
                        title = context.getString(R.string.filter_downloaded),
                        subtitle = context.getString(R.string.library_offline_songs),
                        coverRes = libraryCoverDrawable("downloaded"),
                        fallbackIcon = fallbackIconForAutoPlaylist("downloaded"),
                        route = "auto_playlist/downloaded",
                    ),
                )
            }
            if (showTop) {
                val topSize = uiState.topSize
                val topTitle = if (topSize == "100") {
                    context.getString(R.string.my_top_100)
                } else {
                    "${context.getString(R.string.my_top)} $topSize"
                }
                add(
                    LibraryAutoPlaylistItem(
                        type = "top100",
                        title = topTitle,
                        subtitle = context.getString(R.string.library_top_songs),
                        coverRes = libraryCoverDrawable("top100"),
                        fallbackIcon = fallbackIconForAutoPlaylist("top100"),
                        route = "top_playlist/$topSize",
                    ),
                )
            }
            if (showCached) {
                add(
                    LibraryAutoPlaylistItem(
                        type = "cached",
                        title = context.getString(R.string.cached_playlist),
                        subtitle = context.getString(R.string.library_cached_songs),
                        coverRes = libraryCoverDrawable("cached"),
                        fallbackIcon = fallbackIconForAutoPlaylist("cached"),
                        route = "cache_playlist/cached",
                    ),
                )
            }
            if (showUploaded) {
                add(
                    LibraryAutoPlaylistItem(
                        type = "uploaded",
                        title = context.getString(R.string.uploaded_playlist_name),
                        subtitle = context.getString(R.string.library_uploaded_songs),
                        coverRes = libraryCoverDrawable("uploaded"),
                        fallbackIcon = fallbackIconForAutoPlaylist("uploaded"),
                        route = "auto_playlist/uploaded",
                    ),
                )
            }
            if (showLocal && uiState.hasLocalSongs) {
                add(
                    LibraryAutoPlaylistItem(
                        type = "local",
                        title = context.getString(R.string.filter_local),
                        subtitle = context.getString(R.string.library_local_songs),
                        coverRes = null,
                        fallbackIcon = Icons.Outlined.Folder,
                        route = "local_music",
                    ),
                )
            }
        }
    }

    val podcastSpecialCards = remember(selectedFilter, context, searchQuery, hiddenKeys) {
        if (selectedFilter != LibraryExpressiveFilter.PODCASTS) {
            emptyList()
        } else {
            val base = listOf(
                LibraryAutoPlaylistItem(
                    type = "podcasts",
                    title = context.getString(R.string.new_episodes),
                    subtitle = "",
                    coverRes = libraryCoverDrawable("podcasts"),
                    fallbackIcon = fallbackIconForAutoPlaylist("podcasts"),
                    route = "online_playlist/RDPN",
                ),
                LibraryAutoPlaylistItem(
                    type = "episodes",
                    title = context.getString(R.string.episodes_for_later),
                    subtitle = "",
                    coverRes = libraryCoverDrawable("episodes"),
                    fallbackIcon = fallbackIconForAutoPlaylist("episodes"),
                    route = "online_playlist/SE",
                ),
            ).filterNot { HiddenLibraryItemKey(HiddenLibraryItemType.AUTO, it.type) in hiddenKeys }
            val term = searchQuery.trim()
            if (term.isEmpty()) base else base.filter { it.title.contains(term, ignoreCase = true) }
        }
    }

    val visibleAutoPlaylists = remember(
        autoPlaylists,
        podcastSpecialCards,
        selectedFilter,
        hiddenKeys,
        searchQuery,
    ) {
        podcastSpecialCards + filterAutoPlaylists(
            autoPlaylists = autoPlaylists,
            filter = selectedFilter,
            hiddenKeys = hiddenKeys,
            searchQuery = searchQuery,
        )
    }

    val displayedEntries = remember(
        uiState.entries,
        selectedFilter,
        hiddenKeys,
        sortConfig,
        localManualOrder,
        searchQuery,
    ) {
        filterAndSortLibraryEntries(
            entries = uiState.entries,
            filter = selectedFilter,
            hiddenKeys = hiddenKeys,
            sortType = sortConfig.sort,
            ascending = sortConfig.ascending,
            manualOrder = localManualOrder,
            searchQuery = searchQuery,
        )
    }

    val filterLabels = remember(context) {
        mapOf(
            LibraryExpressiveFilter.TRACKS to context.getString(R.string.tracks),
            LibraryExpressiveFilter.PLAYLISTS to context.getString(R.string.playlists),
            LibraryExpressiveFilter.ARTISTS to context.getString(R.string.artists),
            LibraryExpressiveFilter.ALBUMS to context.getString(R.string.albums),
            LibraryExpressiveFilter.PODCASTS to context.getString(R.string.podcasts),
        )
    }

    val sortLabels = remember(context) {
        mapOf(
            LibraryExpressiveSortType.RECENTLY_ADDED to context.getString(R.string.sort_by_create_date),
            LibraryExpressiveSortType.NAME to context.getString(R.string.sort_by_name),
            LibraryExpressiveSortType.LAST_UPDATED to context.getString(R.string.library_sort_updated),
            LibraryExpressiveSortType.ARTIST to context.getString(R.string.artist),
            LibraryExpressiveSortType.YEAR to context.getString(R.string.sort_by_year),
            LibraryExpressiveSortType.SONG_COUNT to context.getString(R.string.songs),
            LibraryExpressiveSortType.LENGTH to context.getString(R.string.duration),
            LibraryExpressiveSortType.PLAYTIME to context.getString(R.string.history_sort_listening_time),
            LibraryExpressiveSortType.MANUAL to context.getString(R.string.manual),
        )
    }

    val unfilteredGridState = rememberLazyGridState()
    val filteredGridState = rememberLazyGridState()
    val activeGridState = if (searchQuery.isBlank()) unfilteredGridState else filteredGridState

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            activeGridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var userRefreshing by remember { mutableStateOf(false) }
    val isRefreshingNow by rememberUpdatedState(uiState.isRefreshing)
    LaunchedEffect(userRefreshing) {
        if (!userRefreshing) return@LaunchedEffect
        withTimeoutOrNull(1_500L) { snapshotFlow { isRefreshingNow }.first { it } }
        snapshotFlow { isRefreshingNow }.first { !it }
        userRefreshing = false
    }

    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val topPadding = insets.calculateTopPadding() + 6.dp
    val bottomPadding = insets.calculateBottomPadding()
    val isTelevision = remember(context) { DeviceUtils.isTelevisionDevice(context) }

    val hideLibraryItem: (HiddenLibraryItemKey) -> Unit = { key ->
        storedHiddenItems = encodeHiddenLibraryItems(hiddenKeys + key)
    }

    val moveEntry: (LibraryEntry, LibraryEntry) -> Unit = { from, to ->
        val updated = moveLibraryEntries(
            allEntries = uiState.entries,
            visibleEntries = displayedEntries,
            storedOrder = localManualOrder,
            fromKey = from.hiddenKey,
            toKey = to.hiddenKey,
        )
        if (updated != localManualOrder) {
            localManualOrder = updated
            storedManualOrder = updated
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val queueAllSongsTitle = stringResource(R.string.queue_all_songs)

    Box(Modifier.fillMaxSize()) {
        if (uiState.isInitialLoading && uiState.entries.isEmpty()) {
            ContainedLoadingIndicator(Modifier.align(Alignment.Center))
        } else {
            PullToRefreshBox(
                isRefreshing = userRefreshing,
                onRefresh = {
                    userRefreshing = true
                    if (!isRefreshingNow) viewModel.refresh()
                },
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = userRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = insets.calculateTopPadding()),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val gridMode = isGridView
                val gridState = activeGridState
                val safeColumns = gridColumns.coerceIn(LibraryGridColumnsRange)
                val gap = if (gridMode) (14 - safeColumns * 2).dp else 8.dp
                val reorderState = rememberReorderableLazyGridState(
                    lazyGridState = gridState,
                    scrollThresholdPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding + 96.dp,
                    ),
                    scrollMoveMode = ScrollMoveMode.INSERT,
                ) { from, to ->
                    if (reorderEnabled) {
                        val fromItem = displayedEntries.firstOrNull { it.reorderKey == from.key }
                        val toItem = displayedEntries.firstOrNull { it.reorderKey == to.key }
                        if (fromItem != null && toItem != null) {
                            moveEntry(fromItem, toItem)
                        }
                    }
                }
                val reorderScrollConnection = remember(reorderState) {
                    LibraryReorderScrollConnection { reorderState.isAnyItemDragging }
                }

                LazyVerticalGrid(
                            columns = if (gridMode) GridCells.Fixed(safeColumns) else GridCells.Fixed(1),
                            state = gridState,
                            contentPadding = PaddingValues(
                                top = topPadding,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = bottomPadding + 96.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(reorderScrollConnection)
                                .libraryGridPinch(
                                    enabled = gridMode && !isTelevision,
                                    currentColumns = safeColumns,
                                    onColumnsChange = { gridColumns = it },
                                ),
                        ) {
                            item(
                                key = "library_header",
                                contentType = "header",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LibraryChipShelf(
                                        options = LibraryExpressiveFilter.entries,
                                        selectedOption = selectedFilter,
                                        onSelectionChange = { next ->
                                            prefFilter = next.toPreferenceFilter()
                                        },
                                        label = { filterLabels.getValue(it) },
                                    )
                                    LibrarySortAndControlsRow(
                                        selectedFilter = selectedFilter,
                                        sortConfig = sortConfig,
                                        sortLabel = { sortLabels.getValue(it) },
                                        onSortSelect = { option ->
                                            val nextConfig = LibrarySortConfig(
                                                sort = option,
                                                ascending = option.defaultAscending,
                                            )
                                            storedSortingMap = encodeLibrarySortMap(
                                                updateLibrarySortMap(sortingMap, selectedFilter, nextConfig),
                                            )
                                        },
                                        onDirectionToggle = {
                                            val nextConfig = sortConfig.copy(ascending = !sortConfig.ascending)
                                            storedSortingMap = encodeLibrarySortMap(
                                                updateLibrarySortMap(sortingMap, selectedFilter, nextConfig),
                                            )
                                        },
                                        isSearchOpen = isSearchOpen,
                                        onSearchToggle = {
                                            if (isSearchOpen) searchQuery = ""
                                            isSearchOpen = !isSearchOpen
                                        },
                                        isGridView = gridMode,
                                        onGridViewChange = { grid ->
                                            viewType = if (grid) LibraryViewType.GRID else LibraryViewType.LIST
                                        },
                                    )
                                }
                            }

                            item(
                                key = "library_search",
                                contentType = "search",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ExpandableLibrarySearchBar(
                                    visible = isSearchOpen,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = {
                                        isSearchOpen = false
                                        searchQuery = ""
                                    },
                                    placeholder = stringResource(R.string.search_library),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            if (!uiState.isRefreshing && uiState.syncError && uiState.entries.isEmpty()) {
                                item(
                                    key = "library_error",
                                    contentType = "status",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                        Text(
                                            text = stringResource(R.string.library_load_failed),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        TextButton(
                                            onClick = viewModel::refresh,
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(stringResource(R.string.try_again))
                                        }
                                    }
                                }
                            }

                            items(
                                count = visibleAutoPlaylists.size,
                                key = { index -> "auto_${visibleAutoPlaylists[index].type}" },
                                contentType = { "auto_playlist" },
                                span = { GridItemSpan(if (gridMode) 1 else maxLineSpan) },
                            ) { index ->
                                val item = visibleAutoPlaylists[index]
                                var autoMenuExpanded by remember { mutableStateOf(false) }
                                Box(Modifier.animateItem()) {
                                    LibraryItemContainer(
                                        isGridView = gridMode,
                                        onMenuClick = { autoMenuExpanded = true },
                                        modifier = Modifier.combinedClickable(
                                            onClick = { navController.navigate(item.route) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                autoMenuExpanded = true
                                            },
                                        ),
                                    ) {
                                        if (gridMode) {
                                            LibraryGridCard(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                thumbnailUrl = null,
                                                fallbackIcon = item.fallbackIcon,
                                                coverRes = item.coverRes,
                                                badge = item.badge,
                                            )
                                        } else {
                                            LibraryListCard(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                thumbnailUrl = null,
                                                fallbackIcon = item.fallbackIcon,
                                                coverRes = item.coverRes,
                                                badge = item.badge,
                                            )
                                        }
                                    }
                                    DropdownMenuPopup(
                                        expanded = autoMenuExpanded,
                                        onDismissRequest = { autoMenuExpanded = false },
                                    ) {
                                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.hide_from_library_local)) },
                                                onClick = {
                                                    hideLibraryItem(
                                                        HiddenLibraryItemKey(HiddenLibraryItemType.AUTO, item.type),
                                                    )
                                                    autoMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (displayedEntries.isEmpty() && visibleAutoPlaylists.isEmpty() && !uiState.syncError) {
                                item(
                                    key = "library_empty",
                                    contentType = "status",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (searchQuery.isNotBlank()) {
                                                stringResource(R.string.search_no_results)
                                            } else {
                                                stringResource(R.string.library_empty)
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics {
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                        )
                                    }
                                }
                            } else {
                                items(
                                    count = displayedEntries.size,
                                    key = { index -> displayedEntries[index].reorderKey },
                                    contentType = { index -> displayedEntries[index]::class.simpleName ?: "entry" },
                                    span = { GridItemSpan(if (gridMode) 1 else maxLineSpan) },
                                ) { index ->
                                    val entry = displayedEntries[index]
                                    var podcastMenuExpanded by remember { mutableStateOf(false) }

                                    fun moveTo(targetIndex: Int): (() -> Unit)? =
                                        displayedEntries
                                            .getOrNull(targetIndex)
                                            ?.takeIf { targetIndex != index }
                                            ?.let { target -> { moveEntry(entry, target) } }

                                    val onEntryClick: () -> Unit = {
                                        when (entry) {
                                            is LibraryEntry.SongEntry -> {
                                                val conn = playerConnection
                                                if (conn != null) {
                                                    if (entry.song.id == mediaMetadata?.id) {
                                                        conn.player.togglePlayPause()
                                                    } else {
                                                        val queueSongs = displayedEntries
                                                            .filterIsInstance<LibraryEntry.SongEntry>()
                                                            .map { it.song }
                                                        val songIndex = queueSongs.indexOfFirst { it.id == entry.song.id }
                                                            .coerceAtLeast(0)
                                                        conn.playQueue(
                                                            ListQueue(
                                                                title = queueAllSongsTitle,
                                                                items = queueSongs.map { it.toMediaItem() },
                                                                startIndex = songIndex,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                            is LibraryEntry.AlbumEntry ->
                                                navController.navigate("album/${entry.album.id}")
                                            is LibraryEntry.ArtistEntry ->
                                                navController.navigate("artist/${entry.artist.id}")
                                            is LibraryEntry.PlaylistEntry ->
                                                navController.navigate("local_playlist/${entry.playlist.id}")
                                            is LibraryEntry.PodcastEntry ->
                                                navController.navigate("online_podcast/${entry.podcast.id}")
                                            is LibraryEntry.PodcastChannelEntry ->
                                                navController.navigate("artist/${entry.channelId}")
                                        }
                                    }

                                    val onEntryMenu: () -> Unit = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when (entry) {
                                            is LibraryEntry.SongEntry -> menuState.show {
                                                SongMenu(
                                                    originalSong = entry.song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                            is LibraryEntry.AlbumEntry -> menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = entry.album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                            is LibraryEntry.ArtistEntry -> menuState.show {
                                                ArtistMenu(
                                                    originalArtist = entry.artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                            is LibraryEntry.PlaylistEntry -> menuState.show {
                                                PlaylistMenu(
                                                    playlist = entry.playlist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                            is LibraryEntry.PodcastEntry -> {
                                                podcastMenuExpanded = true
                                            }
                                            is LibraryEntry.PodcastChannelEntry -> menuState.show {
                                                ArtistMenu(
                                                    originalArtist = entry.artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    }

                                    ReorderableItem(
                                        state = reorderState,
                                        key = entry.reorderKey,
                                        enabled = reorderEnabled,
                                    ) {
                                        val dragHandleModifier = Modifier.draggableHandle(
                                            enabled = reorderEnabled,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            dragGestureDetector = if (isTelevision) {
                                                DragGestureDetector.Press
                                            } else {
                                                DragGestureDetector.LongPress
                                            },
                                        )
                                        val isCurrent = entry is LibraryEntry.SongEntry &&
                                            entry.song.id == mediaMetadata?.id

                                        val subtitle = entrySubtitle(entry)
                                        val thumbnailUrl = entryThumbnail(entry)
                                        val artistId = when (entry) {
                                            is LibraryEntry.ArtistEntry -> entry.artist.id
                                            is LibraryEntry.PodcastChannelEntry -> entry.channelId
                                            else -> null
                                        }
                                        val localThumbnails = (entry as? LibraryEntry.PlaylistEntry)
                                            ?.playlist
                                            ?.thumbnails

                                        Box {
                                            LibraryItemContainer(
                                                isGridView = gridMode,
                                                onMenuClick = onEntryMenu,
                                                modifier = Modifier.combinedClickable(
                                                    onClick = onEntryClick,
                                                    onLongClick = onEntryMenu,
                                                ),
                                            ) {
                                                if (gridMode) {
                                                    LibraryGridCard(
                                                        title = entry.title,
                                                        subtitle = subtitle,
                                                        thumbnailUrl = thumbnailUrl,
                                                        fallbackIcon = fallbackIconForEntry(entry),
                                                        artistId = artistId,
                                                        localThumbnails = localThumbnails,
                                                        isCurrent = isCurrent,
                                                        isPlaying = isPlaying,
                                                        isDownloaded = entry.isDownloaded,
                                                        reorderHandle = if (reorderEnabled && displayedEntries.size > 1) {
                                                            {
                                                                LibraryReorderMenu(
                                                                    onMoveUp = moveTo(index - 1),
                                                                    onMoveDown = moveTo(index + 1),
                                                                    onMoveToTop = moveTo(0),
                                                                    onMoveToBottom = moveTo(displayedEntries.lastIndex),
                                                                    dragHandleModifier = dragHandleModifier,
                                                                    onArtwork = true,
                                                                    modifier = Modifier.align(Alignment.TopStart),
                                                                )
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    )
                                                } else {
                                                    LibraryListCard(
                                                        title = entry.title,
                                                        subtitle = subtitle,
                                                        thumbnailUrl = thumbnailUrl,
                                                        fallbackIcon = fallbackIconForEntry(entry),
                                                        artistId = artistId,
                                                        localThumbnails = localThumbnails,
                                                        isCurrent = isCurrent,
                                                        isPlaying = isPlaying,
                                                        isDownloaded = entry.isDownloaded,
                                                        reorderHandle = if (reorderEnabled && displayedEntries.size > 1) {
                                                            {
                                                                LibraryReorderMenu(
                                                                    onMoveUp = moveTo(index - 1),
                                                                    onMoveDown = moveTo(index + 1),
                                                                    onMoveToTop = moveTo(0),
                                                                    onMoveToBottom = moveTo(displayedEntries.lastIndex),
                                                                    dragHandleModifier = dragHandleModifier,
                                                                    onArtwork = false,
                                                                )
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    )
                                                }
                                            }

                                            if (entry is LibraryEntry.PodcastEntry) {
                                                DropdownMenuPopup(
                                                    expanded = podcastMenuExpanded,
                                                    onDismissRequest = { podcastMenuExpanded = false },
                                                ) {
                                                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.remove_from_library)) },
                                                            onClick = {
                                                                podcastMenuExpanded = false
                                                                coroutineScope.launch(Dispatchers.IO) {
                                                                    database.update(entry.podcast.toggleBookmark())
                                                                    YouTube.likePlaylist(entry.podcast.id.removePrefix("MPSP"), false)
                                                                }
                                                            },
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.hide_from_library_local)) },
                                                            onClick = {
                                                                podcastMenuExpanded = false
                                                                hideLibraryItem(entry.hiddenKey)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (hiddenKeys.isNotEmpty() && selectedFilter == null) {
                                item(
                                    key = "restore_hidden_library_items",
                                    contentType = "footer",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextButton(
                                            onClick = { storedHiddenItems = "" },
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(stringResource(R.string.restore_hidden_library_items))
                                        }
                                    }
                                }
                            }
                }
            }
        }

        ScrollingLibraryAddButton(
            label = stringResource(R.string.new_playlist),
            onClick = { showCreatePlaylistDialog = true },
            state = rememberScrollingFabState(activeGridState),
            visible = selectedFilter == null || selectedFilter == LibraryExpressiveFilter.PLAYLISTS,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
                .padding(16.dp),
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = null,
            allowSyncing = true,
        )
    }
}

@Composable
private fun entrySubtitle(entry: LibraryEntry): String =
    when (entry) {
        is LibraryEntry.SongEntry -> entry.artistsDisplay
        is LibraryEntry.AlbumEntry -> entry.artistsDisplay
        is LibraryEntry.ArtistEntry -> stringResource(R.string.artist)
        is LibraryEntry.PlaylistEntry -> pluralStringResource(
            R.plurals.n_song,
            entry.playlist.songCount,
            entry.playlist.songCount,
        )
        is LibraryEntry.PodcastEntry -> entry.podcast.author.orEmpty()
        is LibraryEntry.PodcastChannelEntry -> stringResource(R.string.artist)
    }

private fun entryThumbnail(entry: LibraryEntry): String? =
    when (entry) {
        is LibraryEntry.SongEntry -> entry.song.thumbnailUrl
        is LibraryEntry.AlbumEntry -> entry.album.thumbnailUrl
        is LibraryEntry.ArtistEntry -> entry.artist.thumbnailUrl
        is LibraryEntry.PlaylistEntry -> entry.playlist.playlist.thumbnailUrl
        is LibraryEntry.PodcastEntry -> entry.podcast.thumbnailUrl
        is LibraryEntry.PodcastChannelEntry -> entry.artist.thumbnailUrl
    }
