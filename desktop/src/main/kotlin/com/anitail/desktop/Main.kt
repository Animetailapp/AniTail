package com.anitail.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.anitail.desktop.storage.DesktopLibraryStore
import com.anitail.desktop.ui.DesktopTheme
import com.anitail.desktop.ui.DesktopTopBar
import com.anitail.desktop.ui.ExploreScreen
import com.anitail.desktop.ui.HomeScreen
import com.anitail.desktop.ui.LibraryFilter
import com.anitail.desktop.ui.LibraryScreen
import com.anitail.desktop.ui.PlayerScreen
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.ExplorePage
import com.anitail.innertube.pages.HomePage
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.SearchState
import com.anitail.shared.repository.InnertubeMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DesktopScreen {
    Home,
    Explore,
    Library,
    Player,
    History,
    Stats,
    Settings,
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AniTail Desktop",
    ) {
        DesktopTheme {
            AniTailDesktopApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniTailDesktopApp() {
    val repository = remember { InnertubeMusicRepository() }
    val libraryStore = remember { DesktopLibraryStore() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by remember { mutableStateOf(DesktopScreen.Home) }
    var selectedItem by remember { mutableStateOf<LibraryItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf(SearchState()) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    val libraryItems = remember { mutableStateListOf<LibraryItem>() }
    val libraryFilter = remember { mutableStateOf(LibraryFilter.TODOS) }
    var homePage by remember { mutableStateOf<HomePage?>(null) }
    var selectedChip by remember { mutableStateOf<HomePage.Chip?>(null) }
    var previousHomePage by remember { mutableStateOf<HomePage?>(null) }
    var isHomeLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var explorePage by remember { mutableStateOf<ExplorePage?>(null) }
    var chartsPage by remember { mutableStateOf<ChartsPage?>(null) }
    var isExploreLoading by remember { mutableStateOf(false) }
    var quickPicks by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var keepListening by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var forgottenFavorites by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        val storedItems = withContext(Dispatchers.IO) { libraryStore.load() }
        if (storedItems.isEmpty()) {
            libraryItems.addAll(repository.initialLibrary())
        } else {
            libraryItems.addAll(storedItems)
        }
        loadHomePage(
            onLoading = { isHomeLoading = it },
            onPage = { page -> homePage = page },
        )
        loadExplorePage(
            onLoading = { isExploreLoading = it },
            onExplore = { page -> explorePage = page },
            onCharts = { page -> chartsPage = page },
        )
    }

    LaunchedEffect(libraryItems.size) {
        val items = libraryItems.toList()
        quickPicks = items.take(12)
        keepListening = items.shuffled().take(12)
        forgottenFavorites = items.drop(12).take(12)
    }

    Scaffold(
        topBar = {
            val title = when (currentScreen) {
                DesktopScreen.Home -> "Inicio"
                DesktopScreen.Explore -> "Explorar"
                DesktopScreen.Library -> "Biblioteca"
                DesktopScreen.Player -> "Reproductor"
                DesktopScreen.History -> "Historial"
                DesktopScreen.Stats -> "Estadisticas"
                DesktopScreen.Settings -> "Ajustes"
            }
            DesktopTopBar(
                title = title,
                onSearch = {
                    currentScreen = DesktopScreen.Explore
                    requestSearchFocus = true
                },
                onHistory = {
                    currentScreen = DesktopScreen.History
                },
                onStats = {
                    currentScreen = DesktopScreen.Stats
                },
                onSettings = {
                    currentScreen = DesktopScreen.Settings
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (selectedItem != null) {
                    MiniPlayerBar(
                        title = selectedItem?.title.orEmpty(),
                        artist = selectedItem?.artist.orEmpty(),
                        onOpen = { currentScreen = DesktopScreen.Player },
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Home,
                        onClick = { currentScreen = DesktopScreen.Home },
                        label = { Text("Inicio") },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Explore,
                        onClick = { currentScreen = DesktopScreen.Explore },
                        label = { Text("Explorar") },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Library,
                        onClick = { currentScreen = DesktopScreen.Library },
                        label = { Text("Biblioteca") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            when (currentScreen) {
                DesktopScreen.Home -> {
                    HomeScreen(
                        homePage = homePage,
                        selectedChip = selectedChip,
                        isLoading = isHomeLoading,
                        isLoadingMore = isLoadingMore,
                        quickPicks = quickPicks,
                        keepListening = keepListening,
                        forgottenFavorites = forgottenFavorites,
                        onChipSelected = { chip ->
                            scope.launch {
                                handleChipSelection(
                                    chip = chip,
                                    selectedChip = selectedChip,
                                    previousHomePage = previousHomePage,
                                    currentHomePage = homePage,
                                    onSelectedChip = { selectedChip = it },
                                    onPreviousPage = { previousHomePage = it },
                                    onPage = { page -> homePage = page },
                                    onLoading = { isHomeLoading = it },
                                )
                            }
                        },
                        onLoadMore = {
                            if (isLoadingMore) return@HomeScreen
                            val continuation = homePage?.continuation ?: return@HomeScreen
                            scope.launch {
                                isLoadingMore = true
                                val nextPage = YouTube.home(continuation).getOrNull()
                                if (nextPage != null) {
                                    homePage = homePage?.copy(
                                        sections = homePage?.sections.orEmpty() + nextPage.sections,
                                        continuation = nextPage.continuation,
                                    )
                                }
                                isLoadingMore = false
                            }
                        },
                        onItemSelected = { item ->
                            itemToLibraryItem(item)?.let { libraryItem ->
                                selectedItem = libraryItem
                                currentScreen = DesktopScreen.Player
                            }
                        },
                        onAddToLibrary = { item ->
                            itemToLibraryItem(item)?.let { libraryItem ->
                                if (libraryItems.none { it.id == libraryItem.id }) {
                                    libraryItems.add(libraryItem)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            libraryStore.save(libraryItems)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                DesktopScreen.Explore -> {
                    ExploreScreen(
                        query = searchQuery,
                        searchState = searchState,
                        explorePage = explorePage,
                        chartsPage = chartsPage,
                        isLoading = isExploreLoading,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            scope.launch {
                                searchState =
                                    searchState.copy(isLoading = true, errorMessage = null)
                                val results = repository.search(searchQuery)
                                searchState = searchState.copy(
                                    isLoading = false,
                                    results = results,
                                    errorMessage = if (results.isEmpty()) "Sin resultados" else null,
                                )
                            }
                        },
                        onPlay = { item ->
                            selectedItem = item
                            currentScreen = DesktopScreen.Player
                        },
                        onAddToLibrary = { item ->
                            if (libraryItems.none { it.id == item.id }) {
                                libraryItems.add(item)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        libraryStore.save(libraryItems)
                                    }
                                }
                            }
                        },
                        requestFocus = requestSearchFocus,
                        onRequestFocusHandled = { requestSearchFocus = false },
                    )
                }

                DesktopScreen.Library -> {
                    LibraryScreen(
                        items = libraryItems,
                        filterState = libraryFilter,
                        onPlay = { item ->
                            selectedItem = item
                            currentScreen = DesktopScreen.Player
                        },
                    )
                }

                DesktopScreen.Player -> {
                    PlayerScreen(item = selectedItem)
                }

                DesktopScreen.History -> {
                    com.anitail.desktop.ui.HistoryScreen(
                        items = libraryItems,
                        onPlay = { item ->
                            selectedItem = item
                            currentScreen = DesktopScreen.Player
                        },
                    )
                }

                DesktopScreen.Stats -> {
                    com.anitail.desktop.ui.StatsScreen(
                        items = libraryItems,
                    )
                }

                DesktopScreen.Settings -> {
                    com.anitail.desktop.ui.SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artist: String,
    onOpen: () -> Unit,
) {
    androidx.compose.material3.Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onOpen) {
                    Text("Abrir")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private suspend fun loadHomePage(
    onLoading: (Boolean) -> Unit,
    onPage: (HomePage) -> Unit,
) {
    onLoading(true)
    YouTube.home().onSuccess { page ->
        onPage(page)
    }
    onLoading(false)
}

private suspend fun loadExplorePage(
    onLoading: (Boolean) -> Unit,
    onExplore: (ExplorePage) -> Unit,
    onCharts: (ChartsPage) -> Unit,
) {
    onLoading(true)
    YouTube.explore().onSuccess { page ->
        onExplore(page)
    }
    YouTube.getChartsPage().onSuccess { page ->
        onCharts(page)
    }
    onLoading(false)
}

private suspend fun handleChipSelection(
    chip: HomePage.Chip?,
    selectedChip: HomePage.Chip?,
    previousHomePage: HomePage?,
    currentHomePage: HomePage?,
    onSelectedChip: (HomePage.Chip?) -> Unit,
    onPreviousPage: (HomePage?) -> Unit,
    onPage: (HomePage?) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (chip == null || chip == selectedChip) {
        onPage(previousHomePage)
        onPreviousPage(null)
        onSelectedChip(null)
        return
    }

    if (selectedChip == null) {
        onPreviousPage(currentHomePage)
    }

    onLoading(true)
    val next = YouTube.home(params = chip.endpoint?.params).getOrNull()
    if (next != null) {
        onPage(next.copy(chips = currentHomePage?.chips))
        onSelectedChip(chip)
    }
    onLoading(false)
}

private fun itemToLibraryItem(item: YTItem): LibraryItem? {
    return when (item) {
        is SongItem -> LibraryItem(
            id = item.id,
            title = item.title,
            artist = item.artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = item.thumbnail,
            playbackUrl = "https://music.youtube.com/watch?v=${item.id}",
        )

        is AlbumItem -> LibraryItem(
            id = item.browseId,
            title = item.title,
            artist = item.artists?.joinToString { it.name }.orEmpty(),
            artworkUrl = item.thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${item.playlistId}",
        )

        is PlaylistItem -> LibraryItem(
            id = item.id,
            title = item.title,
            artist = item.author?.name.orEmpty(),
            artworkUrl = item.thumbnail,
            playbackUrl = "https://music.youtube.com/playlist?list=${item.id}",
        )

        is ArtistItem -> LibraryItem(
            id = item.id,
            title = item.title,
            artist = item.title,
            artworkUrl = item.thumbnail,
            playbackUrl = "https://music.youtube.com/channel/${item.id}",
        )

        else -> null
    }
}
