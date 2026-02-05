package com.anitail.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.player.rememberPlayerState
import com.anitail.desktop.storage.DesktopLibraryStore
import com.anitail.desktop.ui.DesktopTheme
import com.anitail.desktop.ui.DesktopTopBar
import com.anitail.desktop.ui.ExploreScreen
import com.anitail.desktop.ui.HomeScreen
import com.anitail.desktop.ui.LibraryFilter
import com.anitail.desktop.ui.LibraryScreen
import com.anitail.desktop.ui.PlayerScreen
import com.anitail.desktop.ui.component.MiniPlayer
import com.anitail.desktop.ui.screen.ArtistDetailScreen
import com.anitail.desktop.ui.screen.AlbumDetailScreen
import com.anitail.desktop.ui.screen.PlaylistDetailScreen
import com.anitail.desktop.ui.screen.SearchScreen
import com.anitail.desktop.ui.screen.LibraryScreenEnhanced
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.desktop.navigation.Screen
import com.anitail.desktop.navigation.rememberNavigationState
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.AlbumItem
import com.anitail.innertube.models.ArtistItem
import com.anitail.innertube.models.BrowseEndpoint
import com.anitail.innertube.models.PlaylistItem
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.YTItem
import com.anitail.innertube.pages.ChartsPage
import com.anitail.innertube.pages.ExplorePage
import com.anitail.innertube.pages.HomePage
import com.anitail.innertube.pages.RelatedPage
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.SearchState
import com.anitail.shared.repository.InnertubeMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    ArtistDetail,
    AlbumDetail,
    PlaylistDetail,
    Search,
}

/**
 * Datos de navegación para pantallas de detalle.
 */
private data class DetailNavigation(
    val artistId: String? = null,
    val artistName: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
)

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
    val playerState = rememberPlayerState()

    var currentScreen by remember { mutableStateOf(DesktopScreen.Home) }
    // selectedItem ahora controlado por playerState.currentItem
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
    var similarRecommendations by remember { mutableStateOf<List<SimilarRecommendation>>(emptyList()) }
    var detailNavigation by remember { mutableStateOf(DetailNavigation()) }
    val navigationHistory = remember { mutableStateListOf<DesktopScreen>() }

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

    // Cargar recomendaciones similares basadas en biblioteca
    LaunchedEffect(libraryItems.size) {
        if (libraryItems.size >= 3) {
            val sampledItems = libraryItems.shuffled().take(3)
            val recommendations = mutableListOf<SimilarRecommendation>()
            
            sampledItems.forEach { item ->
                // Intentar obtener contenido relacionado para cada item
                val videoId = item.playbackUrl
                    .substringAfter("watch?v=", "")
                    .takeIf { it.isNotEmpty() }
                
                if (videoId != null) {
                    val relatedPage = YouTube.related(
                        BrowseEndpoint(browseId = "MPREb_${videoId.take(8)}")
                    ).getOrNull()
                    
                    if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                        recommendations.add(
                            SimilarRecommendation(
                                title = item.title,
                                thumbnailUrl = item.artworkUrl,
                                isArtist = false,
                                items = relatedPage.songs.take(8),
                            )
                        )
                    }
                }
            }
            
            // Si no hay recomendaciones de API, usar items de biblioteca como fallback
            if (recommendations.isEmpty() && libraryItems.size >= 6) {
                val artistGroups = libraryItems.groupBy { it.artist }
                artistGroups.entries.take(2).forEach { (artist, songs) ->
                    if (songs.size >= 2) {
                        recommendations.add(
                            SimilarRecommendation(
                                title = artist,
                                thumbnailUrl = songs.firstOrNull()?.artworkUrl,
                                isArtist = true,
                                items = songs.take(6).map { song ->
                                    SongItem(
                                        id = song.id,
                                        title = song.title,
                                        artists = emptyList(),
                                        thumbnail = song.artworkUrl.orEmpty(),
                                        explicit = false,
                                    )
                                },
                            )
                        )
                    }
                }
            }
            
            similarRecommendations = recommendations.take(3)
        }
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
                DesktopScreen.ArtistDetail -> detailNavigation.artistName ?: "Artista"
                DesktopScreen.AlbumDetail -> detailNavigation.albumName ?: "Álbum"
                DesktopScreen.PlaylistDetail -> detailNavigation.playlistName ?: "Playlist"
                DesktopScreen.Search -> "Buscar"
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
                if (playerState.currentItem != null) {
                    MiniPlayer(
                        playerState = playerState,
                        onOpenFullPlayer = { currentScreen = DesktopScreen.Player },
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
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Search,
                        onClick = { currentScreen = DesktopScreen.Search },
                        label = { Text("Buscar") },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
                        similarRecommendations = similarRecommendations,
                        playerState = playerState,
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
                            when (item) {
                                is ArtistItem -> {
                                    navigationHistory.add(DesktopScreen.Home)
                                    detailNavigation = detailNavigation.copy(
                                        artistId = item.id,
                                        artistName = item.title,
                                    )
                                    currentScreen = DesktopScreen.ArtistDetail
                                }
                                is AlbumItem -> {
                                    navigationHistory.add(DesktopScreen.Home)
                                    detailNavigation = detailNavigation.copy(
                                        albumId = item.browseId,
                                        albumName = item.title,
                                    )
                                    currentScreen = DesktopScreen.AlbumDetail
                                }
                                is PlaylistItem -> {
                                    navigationHistory.add(DesktopScreen.Home)
                                    detailNavigation = detailNavigation.copy(
                                        playlistId = item.id,
                                        playlistName = item.title,
                                    )
                                    currentScreen = DesktopScreen.PlaylistDetail
                                }
                                else -> {
                                    itemToLibraryItem(item)?.let { libraryItem ->
                                        playerState.play(libraryItem)
                                        currentScreen = DesktopScreen.Player
                                    }
                                }
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
                        onShuffleAll = {
                            // Shuffle all quick picks and start playing
                            val shuffled = quickPicks.shuffled()
                            if (shuffled.isNotEmpty()) {
                                playerState.shuffleEnabled = true
                                playerState.play(shuffled.first())
                                shuffled.drop(1).forEach { playerState.addToQueue(it) }
                                currentScreen = DesktopScreen.Player
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
                            playerState.play(item)
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
                    LibraryScreenEnhanced(
                        items = libraryItems,
                        playerState = playerState,
                        onPlayItem = { item ->
                            playerState.play(item)
                        },
                        onArtistClick = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.Library)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onAlbumClick = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.Library)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onPlaylistClick = { playlistId, playlistName ->
                            navigationHistory.add(DesktopScreen.Library)
                            detailNavigation = detailNavigation.copy(
                                playlistId = playlistId,
                                playlistName = playlistName,
                            )
                            currentScreen = DesktopScreen.PlaylistDetail
                        },
                    )
                }

                DesktopScreen.Player -> {
                    PlayerScreen(item = playerState.currentItem)
                }

                DesktopScreen.History -> {
                    com.anitail.desktop.ui.HistoryScreen(
                        items = libraryItems,
                        onPlay = { item ->
                            playerState.play(item)
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

                DesktopScreen.ArtistDetail -> {
                    ArtistDetailScreen(
                        artistId = detailNavigation.artistId.orEmpty(),
                        artistName = detailNavigation.artistName.orEmpty(),
                        playerState = playerState,
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                        onAlbumClick = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.ArtistDetail)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onArtistClick = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.ArtistDetail)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onPlaylistClick = { playlistId, playlistName ->
                            navigationHistory.add(DesktopScreen.ArtistDetail)
                            detailNavigation = detailNavigation.copy(
                                playlistId = playlistId,
                                playlistName = playlistName,
                            )
                            currentScreen = DesktopScreen.PlaylistDetail
                        },
                        onSongClick = { item ->
                            playerState.play(item)
                        },
                    )
                }

                DesktopScreen.AlbumDetail -> {
                    AlbumDetailScreen(
                        albumId = detailNavigation.albumId.orEmpty(),
                        albumName = detailNavigation.albumName.orEmpty(),
                        playerState = playerState,
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                        onArtistClick = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.AlbumDetail)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                    )
                }

                DesktopScreen.PlaylistDetail -> {
                    PlaylistDetailScreen(
                        playlistId = detailNavigation.playlistId.orEmpty(),
                        playlistName = detailNavigation.playlistName.orEmpty(),
                        playerState = playerState,
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                        onArtistClick = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.PlaylistDetail)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                    )
                }

                DesktopScreen.Search -> {
                    SearchScreen(
                        playerState = playerState,
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                        onArtistClick = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.Search)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onAlbumClick = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.Search)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onPlaylistClick = { playlistId, playlistName ->
                            navigationHistory.add(DesktopScreen.Search)
                            detailNavigation = detailNavigation.copy(
                                playlistId = playlistId,
                                playlistName = playlistName,
                            )
                            currentScreen = DesktopScreen.PlaylistDetail
                        },
                        onSongClick = { item ->
                            playerState.play(item)
                        },
                    )
                }
            }
        }
    }

    // Simular progreso de reproducción (cada 100ms avanza 100ms)
    LaunchedEffect(playerState.isPlaying, playerState.currentItem) {
        while (playerState.isPlaying && playerState.currentItem != null) {
            delay(100)
            playerState.updatePosition(playerState.position + 100)
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
