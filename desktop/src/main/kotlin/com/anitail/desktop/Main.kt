package com.anitail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.db.entities.EventEntity
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.db.mapper.extractVideoId
import com.anitail.desktop.db.mapper.toLibraryItem
import com.anitail.desktop.db.mapper.toAlbumEntity
import com.anitail.desktop.db.mapper.toArtistEntity
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.constants.MiniPlayerBottomSpacing
import com.anitail.desktop.constants.MiniPlayerHeight
import com.anitail.desktop.constants.NavigationBarHeight
import com.anitail.desktop.auth.AccountInfo
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.auth.normalizeDataSyncId
import com.anitail.desktop.home.HomeListenAlbum
import com.anitail.desktop.home.HomeListenArtist
import com.anitail.desktop.home.HomeListenSong
import com.anitail.desktop.home.LocalItemType
import com.anitail.desktop.home.ShuffleSource
import com.anitail.desktop.home.buildAllYtItems
import com.anitail.desktop.home.buildHomeRecommendations
import com.anitail.desktop.home.resolveLocalItemType
import com.anitail.desktop.home.selectShuffleSource
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.player.rememberPlayerState
import com.anitail.desktop.sync.shouldStartSync
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.QuickPicks
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.anitail.desktop.ui.AnitailTheme
import com.anitail.desktop.ui.DefaultThemeColor
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.extractThemeColor
import com.anitail.desktop.ui.component.BottomSheet
import com.anitail.desktop.ui.component.DesktopTopBar
import com.anitail.desktop.ui.component.MiniPlayer
import com.anitail.desktop.ui.component.rememberBottomSheetState
import com.anitail.desktop.ui.screen.AlbumDetailScreen
import com.anitail.desktop.ui.screen.ArtistDetailScreen
import com.anitail.desktop.ui.screen.ChartsScreen
import com.anitail.desktop.ui.screen.ExploreScreen
import com.anitail.desktop.ui.screen.HistoryScreen
import com.anitail.desktop.ui.screen.HomeScreen
import com.anitail.desktop.ui.screen.LibraryScreenEnhanced
import com.anitail.desktop.ui.screen.MoodAndGenresScreen
import com.anitail.desktop.ui.screen.NewReleaseScreen
import com.anitail.desktop.ui.screen.PlayerScreen
import com.anitail.desktop.ui.screen.PlaylistDetailScreen
import com.anitail.desktop.ui.screen.SearchScreen
import com.anitail.desktop.ui.screen.SettingsScreen
import com.anitail.desktop.ui.screen.StatsScreen
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
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.shared.model.LibraryItem
import com.anitail.shared.model.SearchState
import com.anitail.shared.repository.InnertubeMusicRepository
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import com.anitail.desktop.sync.LibrarySyncService
import java.net.URL

private enum class DesktopScreen {
    Home,
    Explore,
    Library,
    History,
    Stats,
    Settings,
    ArtistDetail,
    AlbumDetail,
    PlaylistDetail,
    Search,
    Charts,
    MoodAndGenres,
    NewRelease,
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
        AniTailDesktopApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniTailDesktopApp() {
    val repository = remember { InnertubeMusicRepository() }
    // Replace legacy store with Database and Preferences
    val database = remember { DesktopDatabase.getInstance() }
    val downloadService = remember { DesktopDownloadService() }
    val preferences = remember { DesktopPreferences.getInstance() }
    val authService = remember { DesktopAuthService() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerState = rememberPlayerState()
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlackEnabled by preferences.pureBlack.collectAsState()
    val dynamicColorEnabled by preferences.dynamicColor.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicksMode by preferences.quickPicks.collectAsState()
    val pauseListenHistory by preferences.pauseListenHistory.collectAsState()
    val historyDuration by preferences.historyDuration.collectAsState()
    val useLoginForBrowse by preferences.useLoginForBrowse.collectAsState()
    val ytmSync by preferences.ytmSync.collectAsState()
    val syncService = remember { LibrarySyncService(database) }
    val isSyncing by syncService.isSyncing.collectAsState()
    val lastSyncError by syncService.lastSyncError.collectAsState()
    var themeColor by remember { mutableStateOf(DefaultThemeColor) }
    var lastArtworkUrl by remember { mutableStateOf<String?>(null) }

    var currentScreen by remember { mutableStateOf(DesktopScreen.Home) }
    var authCredentials by remember { mutableStateOf(authService.credentials) }
    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var searchState by remember { mutableStateOf(SearchState()) }
    var searchActive by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var wasSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(authCredentials?.cookie) {
        accountInfo = authService.refreshAccountInfo()
    }

    LaunchedEffect(authCredentials?.visitorData, authCredentials?.cookie) {
        if (authCredentials?.visitorData.isNullOrBlank() && authCredentials?.cookie?.isNotBlank() == true) {
            authService.ensureVisitorData()
            authCredentials = authService.credentials
        }
    }

    LaunchedEffect(isSyncing, lastSyncError) {
        if (isSyncing) {
            wasSyncing = true
            syncStatus = "Sincronizando..."
            return@LaunchedEffect
        }

        if (wasSyncing) {
            syncStatus = if (lastSyncError != null) {
                "Error de sincronización"
            } else {
                "Sincronización completa"
            }
            delay(3000)
            syncStatus = null
            wasSyncing = false
        }
    }

    // Library state from Database
    val songs by database.songsInLibrary().collectAsState(initial = emptyList())
    val allSongs by database.songs.collectAsState(initial = emptyList())
    val songsById = remember(allSongs) { allSongs.associateBy { it.id } }
    val albums by database.albums.collectAsState(initial = emptyList())
    val artists by database.artists.collectAsState(initial = emptyList())
    val playlists by database.allPlaylists().collectAsState(initial = emptyList())
    val relatedSongMaps by database.relatedSongMaps.collectAsState(initial = emptyList())
    val events by database.events.collectAsState(initial = emptyList())
    // For backward compatibility with existing code that expects LibraryItem
    val libraryItems = remember(songs, playlists, albums, artists) {
        val list = mutableListOf<LibraryItem>()
        list.addAll(songs.map { it.toLibraryItem() })
        list.addAll(playlists.map { it.toLibraryItem() })
        list.addAll(albums.map { it.toLibraryItem() })
        list.addAll(artists.map { it.toLibraryItem() })
        list.toMutableStateList()
    }

    var homePage by remember { mutableStateOf<HomePage?>(null) }
    var selectedChip by remember { mutableStateOf<HomePage.Chip?>(null) }
    var previousHomePage by remember { mutableStateOf<HomePage?>(null) }
    var isHomeLoading by remember { mutableStateOf(false) }
    var isHomeRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var explorePage by remember { mutableStateOf<ExplorePage?>(null) }
    var chartsPage by remember { mutableStateOf<ChartsPage?>(null) }
    var isExploreLoading by remember { mutableStateOf(false) }
    var quickPicks by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var keepListening by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var forgottenFavorites by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var accountPlaylists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var similarRecommendations by remember { mutableStateOf<List<SimilarRecommendation>>(emptyList()) }
    var detailNavigation by remember { mutableStateOf(DetailNavigation()) }
    val navigationHistory = remember { mutableStateListOf<DesktopScreen>() }

    DisposableEffect(playerState, database) {
        playerState.onPlaybackEvent = { item, playTimeMs ->
            val thresholdMs = if (historyDuration <= 0f) 0L else (historyDuration * 1000f).toLong()
            if (!pauseListenHistory && playTimeMs >= thresholdMs) {
                scope.launch {
                    val existing = database.song(item.id).first()
                    val base = existing ?: item.toSongEntity(inLibrary = false)
                    database.updateSong(
                        base.copy(
                            totalPlayTime = base.totalPlayTime + playTimeMs,
                            dateModified = LocalDateTime.now(),
                        ),
                    )
                    database.insertEvent(
                        EventEntity(
                            songId = item.id,
                            timestamp = LocalDateTime.now(),
                            playTime = playTimeMs,
                        ),
                    )
                }
            }
        }
        onDispose {
            playerState.onPlaybackEvent = null
        }
    }

    LaunchedEffect(playerState.currentItem?.id) {
        val item = playerState.currentItem ?: return@LaunchedEffect
        val existing = database.song(item.id).first()
        val base = existing ?: item.toSongEntity(inLibrary = false)
        if (existing == null) {
            database.updateSong(base)
        }

        suspend fun ensureArtist(artistId: String) {
            if (artistId.isBlank()) return
            if (database.artist(artistId).first() != null) return
            YouTube.artist(artistId).onSuccess { page ->
                database.insertArtist(page.artist.toArtistEntity())
            }
        }

        suspend fun ensureAlbum(albumId: String) {
            if (albumId.isBlank()) return
            if (database.album(albumId).first() != null) return
            YouTube.album(albumId).onSuccess { page ->
                database.insertAlbum(page.album.toAlbumEntity())
            }
        }

        val nextResult = YouTube.next(WatchEndpoint(videoId = item.id)).getOrNull()
        val songDetails = nextResult?.let { result ->
            result.currentIndex?.let { index -> result.items.getOrNull(index) }
                ?: result.items.firstOrNull { it.id == item.id }
                ?: result.items.firstOrNull()
        }

        if (songDetails != null) {
            val updated = base.copy(
                title = songDetails.title.ifBlank { base.title },
                duration = if (base.duration > 0) base.duration else (songDetails.duration ?: base.duration),
                thumbnailUrl = songDetails.thumbnail.ifBlank { base.thumbnailUrl.orEmpty() }
                    .takeIf { it.isNotBlank() } ?: base.thumbnailUrl,
                albumId = songDetails.album?.id ?: base.albumId,
                albumName = songDetails.album?.name ?: base.albumName,
                artistId = songDetails.artists.firstOrNull()?.id ?: base.artistId,
                artistName = if (songDetails.artists.isNotEmpty()) {
                    songDetails.artists.joinToString(", ") { it.name }
                } else {
                    base.artistName
                },
                explicit = base.explicit || songDetails.explicit,
                dateModified = LocalDateTime.now(),
            )
            database.updateSong(updated)

            songDetails.artists.mapNotNull { it.id }.forEach { ensureArtist(it) }
            songDetails.album?.id?.let { ensureAlbum(it) }
        }

        if (!database.hasRelatedSongs(item.id)) {
            val relatedEndpoint = nextResult?.relatedEndpoint ?: return@LaunchedEffect
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return@LaunchedEffect
            val relatedSongs = relatedPage.songs
            relatedSongs.forEach { song ->
                database.insertSong(song.toSongEntity())
            }
            database.insertRelatedSongs(item.id, relatedSongs.map { it.id })
        }
    }

    LaunchedEffect(dynamicColorEnabled, playerState.currentItem?.artworkUrl) {
        if (!dynamicColorEnabled) {
            themeColor = DefaultThemeColor
            lastArtworkUrl = null
            return@LaunchedEffect
        }
        val artworkUrl = playerState.currentItem?.artworkUrl
        if (artworkUrl.isNullOrBlank()) {
            themeColor = DefaultThemeColor
            lastArtworkUrl = null
            return@LaunchedEffect
        }
        if (artworkUrl == lastArtworkUrl) return@LaunchedEffect
        lastArtworkUrl = artworkUrl
        themeColor = fetchThemeColorFromUrl(artworkUrl) ?: DefaultThemeColor
    }

    // Initialize Database and sync preferences with Innertube
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            database.initialize()
        }
    }

    // Sync Preferences with InnerTube API
    val youtubeCookie by preferences.youtubeCookie.collectAsState()
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()

    LaunchedEffect(
        youtubeCookie,
        contentLanguage,
        contentCountry,
        authCredentials,
        useLoginForBrowse,
    ) {
        val credentials = authCredentials
        YouTube.cookie = credentials?.cookie ?: youtubeCookie
        YouTube.visitorData = credentials?.visitorData
        YouTube.dataSyncId = normalizeDataSyncId(credentials?.dataSyncId)
        YouTube.useLoginForBrowse = useLoginForBrowse
        YouTube.locale = com.anitail.innertube.models.YouTubeLocale(
            hl = contentLanguage,
            gl = contentCountry
        )
        println("Anitail: YouTube API initialized with Cookie: ${youtubeCookie?.take(20)}..., Locale: $contentLanguage-$contentCountry")
    }

    // Initial page loading and reload on auth changes
    LaunchedEffect(
        authCredentials,
        useLoginForBrowse,
        contentLanguage,
        contentCountry
    ) {
        // Wait for YouTube API to be updated by the other LaunchedEffect
        // or just update it here as well for safety
        val credentials = authCredentials
        YouTube.cookie = credentials?.cookie ?: youtubeCookie
        YouTube.visitorData = credentials?.visitorData
        YouTube.dataSyncId = normalizeDataSyncId(credentials?.dataSyncId)
        YouTube.useLoginForBrowse = useLoginForBrowse
        YouTube.locale = com.anitail.innertube.models.YouTubeLocale(
            hl = contentLanguage,
            gl = contentCountry
        )
        
        homePage = null
        explorePage = null
        chartsPage = null

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

    // Trigger library sync when enabled and cookie available
    LaunchedEffect(ytmSync, authCredentials?.cookie, youtubeCookie) {
        val activeCookie = authCredentials?.cookie ?: youtubeCookie
        if (!shouldStartSync(ytmSync, activeCookie)) return@LaunchedEffect

        YouTube.cookie = activeCookie
        scope.launch {
            syncService.syncAll()
        }
        scope.launch {
            YouTube.library("FEmusic_liked_playlists").onSuccess { page ->
                accountPlaylists = page.items.filterIsInstance<PlaylistItem>()
            }
        }
    }

    LaunchedEffect(
        allSongs,
        albums,
        artists,
        relatedSongMaps,
        events,
        hideExplicit,
        quickPicksMode,
    ) {
        val recommendations = buildHomeRecommendations(
            songs = allSongs,
            albums = albums,
            artists = artists,
            relatedSongMaps = relatedSongMaps,
            events = events,
            hideExplicit = hideExplicit,
        )

        val lastListenQuickPicks = if (quickPicksMode == QuickPicks.LAST_LISTEN) {
            val lastSongId = events.maxByOrNull { it.timestamp }?.songId
            val related = lastSongId?.let { database.relatedSongs(it) }.orEmpty()
            if (hideExplicit) related.filterNot { it.explicit } else related
        } else {
            emptyList()
        }

        val quickPicksSource = if (
            quickPicksMode == QuickPicks.LAST_LISTEN && lastListenQuickPicks.isNotEmpty()
        ) {
            lastListenQuickPicks
        } else {
            recommendations.quickPicks
        }

        quickPicks = quickPicksSource
            .shuffled()
            .take(20)
            .map { it.toLibraryItem() }

        keepListening = recommendations.keepListening
            .shuffled()
            .mapNotNull { item ->
                when (item) {
                    is HomeListenSong -> item.song.toLibraryItem()
                    is HomeListenAlbum -> item.album.toLibraryItem()
                    is HomeListenArtist -> item.artist.toLibraryItem()
                }
            }
            .take(20)

        forgottenFavorites = recommendations.forgottenFavorites
            .shuffled()
            .take(20)
            .map { it.toLibraryItem() }
    }

    // Recommendation logic remains similar...
    // [Truncated for brevity, keeping recommendation logic same as before but using updated libraryItems]
    LaunchedEffect(libraryItems.size) {
        if (libraryItems.size >= 3) {
            val sampledItems = libraryItems.shuffled().take(3)
            val recommendations = mutableListOf<SimilarRecommendation>()
            
            sampledItems.forEach { item ->
                val songEntity = songs.firstOrNull { it.id == item.id }
                val albumEntity = songEntity?.albumId?.let { albumId ->
                    albums.firstOrNull { it.id == albumId }
                }
                val artistEntity = songEntity?.artistId?.let { artistId ->
                    artists.firstOrNull { it.id == artistId }
                }
                val titleItem = albumEntity?.toLibraryItem()
                    ?: artistEntity?.toLibraryItem()
                    ?: item
                val videoId = extractVideoId(item.playbackUrl)
                
                if (videoId != null) {
                    val relatedPage = YouTube.related(
                        BrowseEndpoint(browseId = "MPREb_${videoId.take(8)}")
                    ).getOrNull()
                    
                    if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                        recommendations.add(
                            SimilarRecommendation(
                                title = titleItem.title,
                                thumbnailUrl = titleItem.artworkUrl,
                                isArtist = resolveLocalItemType(titleItem) == LocalItemType.ARTIST,
                                items = relatedPage.songs.take(8),
                                sourceItem = titleItem,
                            )
                        )
                    }
                }
            }
            
            if (recommendations.isEmpty() && libraryItems.size >= 6) {
                val artistGroups = libraryItems.groupBy { it.artist }
                artistGroups.entries.take(2).forEach { (artist, songs) ->
                    if (songs.size >= 2) {
                        val artistEntity = artists.firstOrNull { it.name == artist }
                        val titleItem = artistEntity?.toLibraryItem()
                        recommendations.add(
                            SimilarRecommendation(
                                title = titleItem?.title ?: artist,
                                thumbnailUrl = titleItem?.artworkUrl
                                    ?: songs.firstOrNull()?.artworkUrl,
                                isArtist = titleItem != null,
                                items = songs.take(6).map { song ->
                                    SongItem(
                                        id = song.id,
                                        title = song.title,
                                        artists = emptyList(),
                                        thumbnail = song.artworkUrl.orEmpty(),
                                        explicit = false,
                                    )
                                },
                                sourceItem = titleItem,
                            )
                        )
                    }
                }
            }
            
            similarRecommendations = recommendations.take(3)
        }
    }

    val allYtItems = remember(homePage, accountPlaylists, similarRecommendations) {
        buildAllYtItems(
            homePage = homePage,
            accountPlaylists = accountPlaylists,
            similarRecommendations = similarRecommendations,
        )
    }

    val refreshHome: () -> Unit = refreshHome@{
        if (isHomeRefreshing) return@refreshHome
        scope.launch {
            isHomeRefreshing = true
            loadHomePage(
                onLoading = { isHomeLoading = it },
                onPage = { page -> homePage = page },
            )
            if (authCredentials?.cookie?.isNotBlank() == true) {
                accountInfo = authService.refreshAccountInfo()
                YouTube.library("FEmusic_liked_playlists").onSuccess { page ->
                    accountPlaylists = page.items.filterIsInstance<PlaylistItem>()
                }
            }
            isHomeRefreshing = false
        }
    }

    val openArtist: (String, String?) -> Unit = { artistId, artistName ->
        navigationHistory.add(DesktopScreen.Home)
        detailNavigation = detailNavigation.copy(
            artistId = artistId,
            artistName = artistName,
        )
        currentScreen = DesktopScreen.ArtistDetail
    }

    val openAlbum: (String, String?) -> Unit = { albumId, albumName ->
        navigationHistory.add(DesktopScreen.Home)
        detailNavigation = detailNavigation.copy(
            albumId = albumId,
            albumName = albumName,
        )
        currentScreen = DesktopScreen.AlbumDetail
    }

    AnitailTheme(
        darkMode = darkMode,
        pureBlack = pureBlackEnabled,
        themeColor = themeColor,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val playerBottomSheetState =
                rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = NavigationBarHeight + MiniPlayerBottomSpacing + MiniPlayerHeight,
                    expandedBound = maxHeight,
                )
            val playerBottomPadding = if (!playerBottomSheetState.isDismissed) {
                MiniPlayerHeight + MiniPlayerBottomSpacing
            } else {
                0.dp
            }
            val contentBottomPadding = NavigationBarHeight + playerBottomPadding
            val bottomSheetBackgroundColor =
                if (pureBlackEnabled) Color.Black else MaterialTheme.colorScheme.surfaceContainer

            LaunchedEffect(playerState.currentItem) {
                if (playerState.currentItem == null) {
                    playerBottomSheetState.dismiss()
                } else if (playerBottomSheetState.isDismissed) {
                    playerBottomSheetState.collapseSoft()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        DesktopTopBar(
                            onSearch = {
                                currentScreen = DesktopScreen.Explore
                                searchActive = true
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
                            pureBlack = pureBlackEnabled,
                            onRefreshHome = if (currentScreen == DesktopScreen.Home) refreshHome else null,
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(bottom = contentBottomPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        when (currentScreen) {
                        DesktopScreen.Home -> {
                        HomeScreen(
                            homePage = homePage,
                            selectedChip = selectedChip,
                            isLoading = isHomeLoading,
                            isRefreshing = isHomeRefreshing,
                            syncStatus = syncStatus,
                            isSyncing = isSyncing,
                            isLoadingMore = isLoadingMore,
                            quickPicks = quickPicks,
                            keepListening = keepListening,
                            forgottenFavorites = forgottenFavorites,
                            accountPlaylists = accountPlaylists,
                            similarRecommendations = similarRecommendations,
                            playerState = playerState,
                            accountName = accountInfo?.name ?: authCredentials?.accountName,
                            accountThumbnailUrl = accountInfo?.thumbnailUrl,
                            database = database,
                            downloadService = downloadService,
                            playlists = playlists,
                            songsById = songsById,
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
                            onRefresh = refreshHome,
                            onOpenArtist = openArtist,
                            onOpenAlbum = openAlbum,
                            onNavigate = { route ->
                                when (route) {
                                    "account" -> currentScreen = DesktopScreen.Library
                                    "moods_and_genres" -> currentScreen = DesktopScreen.MoodAndGenres
                                    "charts" -> currentScreen = DesktopScreen.Charts
                                    else -> {
                                        if (route.startsWith("browse/")) {
                                            // Handle generic browse if possible, or specific types
                                            val id = route.removePrefix("browse/")
                                            // TODO: specific browse
                                        }
                                    }
                                }
                            },
                            onLocalItemSelected = { item ->
                                when (resolveLocalItemType(item)) {
                                    LocalItemType.SONG -> {
                                        scope.launch {
                                            val videoId = extractVideoId(item.playbackUrl) ?: return@launch
                                            val result = YouTube.next(
                                                WatchEndpoint(videoId = videoId)
                                            ).getOrNull() ?: return@launch
                                            val plan = buildRadioQueuePlan(item, result)
                                            playerState.playQueue(plan.items, plan.startIndex)
                                            playerBottomSheetState.collapseSoft()
                                        }
                                    }
                                    LocalItemType.ALBUM -> {
                                        navigationHistory.add(DesktopScreen.Home)
                                        detailNavigation = detailNavigation.copy(
                                            albumId = item.id,
                                            albumName = item.title,
                                        )
                                        currentScreen = DesktopScreen.AlbumDetail
                                    }
                                    LocalItemType.ARTIST -> {
                                        navigationHistory.add(DesktopScreen.Home)
                                        detailNavigation = detailNavigation.copy(
                                            artistId = item.id,
                                            artistName = item.title,
                                        )
                                        currentScreen = DesktopScreen.ArtistDetail
                                    }
                                    LocalItemType.PLAYLIST -> {
                                        navigationHistory.add(DesktopScreen.Home)
                                        detailNavigation = detailNavigation.copy(
                                            playlistId = item.id,
                                            playlistName = item.title,
                                        )
                                        currentScreen = DesktopScreen.PlaylistDetail
                                    }
                                    LocalItemType.UNKNOWN -> {
                                        playerState.play(item)
                                        playerBottomSheetState.collapseSoft()
                                    }
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
                                            playerBottomSheetState.collapseSoft()
                                        }
                                    }
                                }
                            },
                            onShuffleAll = {
                                val source = selectShuffleSource(
                                    localCount = libraryItems.size,
                                    ytCount = allYtItems.size,
                                    randomValue = kotlin.random.Random.nextFloat(),
                                )
                                if (source == ShuffleSource.NONE) return@HomeScreen

                                scope.launch {
                                    when (source) {
                                        ShuffleSource.LOCAL -> {
                                            val lucky = libraryItems.randomOrNull() ?: return@launch
                                            when (resolveLocalItemType(lucky)) {
                                                LocalItemType.SONG -> {
                                                    val videoId = extractVideoId(lucky.playbackUrl) ?: return@launch
                                                    val result = YouTube.next(
                                                        WatchEndpoint(videoId = videoId)
                                                    ).getOrNull() ?: return@launch
                                                    val plan = buildRadioQueuePlan(lucky, result)
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                                LocalItemType.ALBUM -> {
                                                    val songsForAlbum = songs
                                                        .filter { it.albumId == lucky.id }
                                                        .map { it.toLibraryItem() }
                                                    if (songsForAlbum.isNotEmpty()) {
                                                        playerState.playQueue(songsForAlbum, startIndex = 0)
                                                    }
                                                }
                                                LocalItemType.ARTIST -> {
                                                    // Android no reproduce artistas desde shuffle local
                                                }
                                                LocalItemType.PLAYLIST -> {
                                                    // Android no reproduce playlists desde shuffle local
                                                }
                                                LocalItemType.UNKNOWN -> {
                                                    playerState.play(lucky)
                                                }
                                            }
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        ShuffleSource.YT -> {
                                            val lucky = allYtItems.randomOrNull() ?: return@launch
                                            when (lucky) {
                                                is SongItem -> {
                                                    val libraryItem = itemToLibraryItem(lucky) ?: return@launch
                                                    val result = YouTube.next(
                                                        WatchEndpoint(videoId = lucky.id)
                                                    ).getOrNull() ?: return@launch
                                                    val plan = buildRadioQueuePlan(libraryItem, result)
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                                is AlbumItem -> {
                                                    val page = YouTube.album(lucky.browseId).getOrNull() ?: return@launch
                                                    val queue = page.songs.map { song ->
                                                        song.toSongEntity().toLibraryItem()
                                                    }
                                                    if (queue.isNotEmpty()) {
                                                        playerState.playQueue(queue, startIndex = 0)
                                                    }
                                                }
                                                is PlaylistItem -> {
                                                    val page = YouTube.playlist(lucky.id).getOrNull() ?: return@launch
                                                    val queue = page.songs.map { song ->
                                                        song.toSongEntity().toLibraryItem()
                                                    }
                                                    if (queue.isNotEmpty()) {
                                                        playerState.playQueue(queue, startIndex = 0)
                                                    }
                                                }
                                                is ArtistItem -> {
                                                    val endpoint = lucky.radioEndpoint ?: return@launch
                                                    val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                                                    val seed = LibraryItem(
                                                        id = lucky.id,
                                                        title = lucky.title,
                                                        artist = lucky.title,
                                                        artworkUrl = lucky.thumbnail,
                                                        playbackUrl = lucky.shareLink,
                                                    )
                                                    val plan = buildRadioQueuePlan(seed, result)
                                                    playerState.playQueue(plan.items, plan.startIndex)
                                                }
                                                else -> {}
                                            }
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        ShuffleSource.NONE -> Unit
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
                                    val results = repository.search(searchQuery.text)
                                    searchState = searchState.copy(
                                        isLoading = false,
                                        results = results,
                                        errorMessage = if (results.isEmpty()) "Sin resultados" else null,
                                    )
                                }
                            },
                            onPlay = { item ->
                                playerState.play(item)
                                playerBottomSheetState.collapseSoft()
                            },
                            onAddToLibrary = { item ->
                                val songEntity = item.toSongEntity()
                                scope.launch {
                                    database.insertSong(songEntity)
                                }
                            },
                            searchActive = searchActive,
                            onSearchActiveChange = { searchActive = it },
                            pureBlack = pureBlackEnabled,
                            onChartsClick = { currentScreen = DesktopScreen.Charts },
                            onMoodGreClick = { currentScreen = DesktopScreen.MoodAndGenres },
                            onNewReleaseClick = { currentScreen = DesktopScreen.NewRelease },
                        )
                    }

                DesktopScreen.Library -> {
                    LibraryScreenEnhanced(
                        items = libraryItems,
                        playerState = playerState,
                        onPlayItem = { item ->
                            playerState.play(item)
                            playerBottomSheetState.collapseSoft()
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
                        onCreatePlaylist = { name ->
                            scope.launch {
                                database.insertPlaylist(
                                    com.anitail.desktop.db.entities.PlaylistEntity(
                                        name = name,
                                        createdAt = java.time.LocalDateTime.now()
                                    )
                                )
                            }
                        }
                    )
                }

                DesktopScreen.History -> {
                    HistoryScreen(
                        items = libraryItems,
                        onPlay = { item ->
                            playerState.play(item)
                            playerBottomSheetState.collapseSoft()
                        },
                    )
                }

                DesktopScreen.Stats -> {
                    StatsScreen(
                        items = libraryItems,
                    )
                }

                DesktopScreen.Settings -> {
                    SettingsScreen(
                        preferences = preferences,
                        authService = authService,
                        authCredentials = authCredentials,
                        onOpenLogin = {},
                        onAuthChanged = { authCredentials = it },
                    )
                }

                DesktopScreen.Charts -> {
                    ChartsScreen(
                        playerState = playerState,
                        onBack = { currentScreen = DesktopScreen.Explore },
                        onPlayTrack = { item ->
                            playerState.play(item)
                            playerBottomSheetState.collapseSoft()
                        },
                        onArtistClick = { artistId, _ ->
                            navigationHistory.add(DesktopScreen.Charts)
                            detailNavigation = detailNavigation.copy(artistId = artistId)
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onPlaylistClick = { playlistId, _ ->
                            navigationHistory.add(DesktopScreen.Charts)
                            detailNavigation = detailNavigation.copy(playlistId = playlistId)
                            currentScreen = DesktopScreen.PlaylistDetail
                        }
                    )
                }

                            DesktopScreen.MoodAndGenres -> {
                                MoodAndGenresScreen(
                                    onBack = { currentScreen = DesktopScreen.Explore },
                                    onCategoryClick = { _, _, _ -> }
                                )
                            }

                DesktopScreen.NewRelease -> {
                    NewReleaseScreen(
                        playerState = playerState,
                        onBack = { currentScreen = DesktopScreen.Explore },
                        onAlbumClick = { albumId, _ ->
                            navigationHistory.add(DesktopScreen.NewRelease)
                            detailNavigation = detailNavigation.copy(albumId = albumId)
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onArtistClick = { artistId, _ ->
                            navigationHistory.add(DesktopScreen.NewRelease)
                            detailNavigation = detailNavigation.copy(artistId = artistId)
                            currentScreen = DesktopScreen.ArtistDetail
                        }
                    )
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
                            playerBottomSheetState.collapseSoft()
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
                            playerBottomSheetState.collapseSoft()
                        },
                    )
                }
                        }
                    }
                }

                BottomSheet(
                    state = playerBottomSheetState,
                    background = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bottomSheetBackgroundColor)
                        )
                    },
                    onDismiss = { playerState.stop() },
                    collapsedContent = {
                        if (playerState.currentItem != null) {
                            MiniPlayer(
                                playerState = playerState,
                                onOpenFullPlayer = { playerBottomSheetState.expandSoft() },
                            )
                        }
                    },
                ) {
                    PlayerScreen(
                        item = playerState.currentItem,
                        playerState = playerState,
                        database = database,
                        downloadService = downloadService,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(currentScreen)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(currentScreen)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onCollapsePlayer = { playerBottomSheetState.collapseSoft() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                NavigationBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(NavigationBarHeight)
                        .offset(y = NavigationBarHeight * playerBottomSheetState.progress.coerceIn(0f, 1f)),
                    containerColor = if (pureBlackEnabled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (pureBlackEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Home,
                        onClick = { currentScreen = DesktopScreen.Home },
                        label = { Text("Inicio") },
                        icon = {
                            val selected = currentScreen == DesktopScreen.Home
                            Icon(
                                if (selected) IconAssets.homeFilled() else IconAssets.homeOutlined(),
                                contentDescription = null,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Explore,
                        onClick = { currentScreen = DesktopScreen.Explore },
                        label = { Text("Explorar") },
                        icon = {
                            val selected = currentScreen == DesktopScreen.Explore
                            Icon(
                                if (selected) IconAssets.exploreFilled() else IconAssets.exploreOutlined(),
                                contentDescription = null,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Library,
                        onClick = { currentScreen = DesktopScreen.Library },
                        label = { Text("Biblioteca") },
                        icon = {
                            val selected = currentScreen == DesktopScreen.Library
                            Icon(
                                if (selected) IconAssets.libraryFilled() else IconAssets.libraryOutlined(),
                                contentDescription = null,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
        }
    }

    // El reproductor nativo ahora actualiza la posición automáticamente
}

private suspend fun fetchThemeColorFromUrl(url: String): Color? = withContext(Dispatchers.IO) {
    val bytes = runCatching { URL(url).readBytes() }.getOrNull() ?: return@withContext null
    val bitmap = runCatching { Image.makeFromEncoded(bytes).asImageBitmap() }.getOrNull()
        ?: return@withContext null
    runCatching { bitmap.extractThemeColor() }.getOrNull()
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
