package com.anitail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
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
import com.anitail.desktop.db.mapper.toSongArtistMaps
import com.anitail.desktop.db.mapper.toSongEntity
import com.anitail.desktop.db.relations.primaryArtistIdForSong
import com.anitail.desktop.constants.MiniPlayerBottomSpacing
import com.anitail.desktop.constants.MiniPlayerHeight
import com.anitail.desktop.constants.NavigationBarHeight
import com.anitail.desktop.auth.AccountInfo
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.auth.DesktopLastFmService
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
import com.anitail.desktop.i18n.AndroidStringLoader
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.resolveContentCountry
import com.anitail.desktop.i18n.resolveContentLanguage
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.model.SimilarRecommendation
import com.anitail.desktop.player.buildRadioQueuePlan
import com.anitail.desktop.player.rememberPlayerState
import com.anitail.desktop.sync.shouldStartSync
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.DesktopAutoBackupService
import com.anitail.desktop.storage.AvatarSourcePreference
import com.anitail.desktop.storage.NavigationTabPreference
import com.anitail.desktop.storage.ProxyTypePreference
import com.anitail.desktop.storage.QuickPicks
import com.anitail.desktop.storage.UpdateCheckFrequency
import com.anitail.desktop.update.DesktopUpdateBackgroundScheduler
import com.anitail.desktop.update.DesktopUpdateInstaller
import com.anitail.desktop.update.DesktopUpdater
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import com.anitail.desktop.ui.AnitailTheme
import com.anitail.desktop.ui.DefaultThemeColor
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.extractThemeColor
import com.anitail.desktop.ui.loadBitmapResource
import com.anitail.desktop.ui.component.BottomSheet
import com.anitail.desktop.ui.component.DesktopTopBar
import com.anitail.desktop.ui.component.MiniPlayer
import com.anitail.desktop.ui.component.rememberBottomSheetState
import com.anitail.desktop.ui.screen.AlbumDetailScreen
import com.anitail.desktop.ui.screen.ArtistDetailScreen
import com.anitail.desktop.ui.screen.ArtistItemsScreen
import com.anitail.desktop.ui.screen.ChartsScreen
import com.anitail.desktop.ui.screen.ExploreScreen
import com.anitail.desktop.ui.screen.BrowseScreen
import com.anitail.desktop.ui.screen.HistoryScreen
import com.anitail.desktop.ui.screen.HomeScreen
import com.anitail.desktop.ui.screen.library.LibraryScreen
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
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import com.anitail.desktop.sync.LibrarySyncService
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.platform.win32.User32
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import java.util.Base64
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DesktopScreen {
    Home,
    Explore,
    Library,
    History,
    Stats,
    Settings,
    ArtistDetail,
    ArtistItems,
    AlbumDetail,
    PlaylistDetail,
    Search,
    Charts,
    MoodAndGenres,
    NewRelease,
    Browse,
}

private enum class WindowSnapMode {
    FLOATING,
    MAXIMIZED,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

private const val SNAP_THRESHOLD_PX = 16

private fun isWindowAtWorkArea(bounds: Rectangle, workArea: Rectangle): Boolean {
    val workX = workArea.x
    val workY = workArea.y
    val workWidth = workArea.width
    val workHeight = workArea.height
    if (workWidth <= 0 || workHeight <= 0) return false
    val tolerance = 2
    return abs(bounds.x - workX) <= tolerance &&
        abs(bounds.y - workY) <= tolerance &&
        abs(bounds.width - workWidth) <= tolerance &&
        abs(bounds.height - workHeight) <= tolerance
}

private fun isWindowAtWorkArea(window: java.awt.Window): Boolean {
    val workArea = getWorkAreaForWindow(window)
    return isWindowAtWorkArea(getVisualBoundsForSnap(window), workArea)
}

private fun isValidBounds(bounds: Rectangle): Boolean {
    return bounds.width >= 200 && bounds.height >= 200
}

private fun getWorkAreaForWindow(window: java.awt.Window): Rectangle {
    val gc = window.graphicsConfiguration ?: return Rectangle(0, 0, 0, 0)
    val bounds = gc.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    val x = bounds.x + insets.left
    val y = bounds.y + insets.top
    val width = bounds.width - insets.left - insets.right
    val height = bounds.height - insets.top - insets.bottom
    return Rectangle(x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
}

private fun determineSnapTargetByBounds(bounds: Rectangle, workArea: Rectangle): WindowSnapMode? {
    if (workArea.width <= 0 || workArea.height <= 0) return null
    val left = workArea.x
    val right = workArea.x + workArea.width
    val top = workArea.y
    val bottom = workArea.y + workArea.height

    val nearLeft = abs(bounds.x - left) <= SNAP_THRESHOLD_PX
    val nearRight = abs(bounds.x + bounds.width - right) <= SNAP_THRESHOLD_PX
    val nearTop = abs(bounds.y - top) <= SNAP_THRESHOLD_PX
    val nearBottom = abs(bounds.y + bounds.height - bottom) <= SNAP_THRESHOLD_PX

    val spansFullWidth = nearLeft && nearRight
    return when {
        nearTop && spansFullWidth -> WindowSnapMode.MAXIMIZED
        nearTop && nearLeft -> WindowSnapMode.TOP_LEFT
        nearTop && nearRight -> WindowSnapMode.TOP_RIGHT
        nearBottom && nearLeft -> WindowSnapMode.BOTTOM_LEFT
        nearBottom && nearRight -> WindowSnapMode.BOTTOM_RIGHT
        nearTop -> WindowSnapMode.MAXIMIZED
        nearLeft -> WindowSnapMode.LEFT
        nearRight -> WindowSnapMode.RIGHT
        else -> null
    }
}

private fun determineSnapTargetByPointer(point: java.awt.Point, workArea: Rectangle): WindowSnapMode? {
    if (workArea.width <= 0 || workArea.height <= 0) return null
    val left = workArea.x
    val right = workArea.x + workArea.width
    val top = workArea.y
    val bottom = workArea.y + workArea.height

    val nearLeft = kotlin.math.abs(point.x - left) <= SNAP_THRESHOLD_PX
    val nearRight = kotlin.math.abs(point.x - right) <= SNAP_THRESHOLD_PX
    val nearTop = kotlin.math.abs(point.y - top) <= SNAP_THRESHOLD_PX
    val nearBottom = kotlin.math.abs(point.y - bottom) <= SNAP_THRESHOLD_PX

    return when {
        nearTop && nearLeft -> WindowSnapMode.TOP_LEFT
        nearTop && nearRight -> WindowSnapMode.TOP_RIGHT
        nearBottom && nearLeft -> WindowSnapMode.BOTTOM_LEFT
        nearBottom && nearRight -> WindowSnapMode.BOTTOM_RIGHT
        nearTop -> WindowSnapMode.MAXIMIZED
        nearLeft -> WindowSnapMode.LEFT
        nearRight -> WindowSnapMode.RIGHT
        else -> null
    }
}

private fun computeSnapBounds(workArea: Rectangle, snapMode: WindowSnapMode): Rectangle? {
    if (workArea.width <= 0 || workArea.height <= 0) return null
    val halfWidth = workArea.width / 2
    val halfHeight = workArea.height / 2
    val rightX = workArea.x + workArea.width - halfWidth
    val bottomY = workArea.y + workArea.height - halfHeight

    return when (snapMode) {
        WindowSnapMode.MAXIMIZED ->
            Rectangle(workArea.x, workArea.y, workArea.width, workArea.height)
        WindowSnapMode.LEFT ->
            Rectangle(workArea.x, workArea.y, halfWidth, workArea.height)
        WindowSnapMode.RIGHT ->
            Rectangle(rightX, workArea.y, workArea.width - (rightX - workArea.x), workArea.height)
        WindowSnapMode.TOP_LEFT ->
            Rectangle(workArea.x, workArea.y, halfWidth, halfHeight)
        WindowSnapMode.TOP_RIGHT ->
            Rectangle(rightX, workArea.y, workArea.width - (rightX - workArea.x), halfHeight)
        WindowSnapMode.BOTTOM_LEFT ->
            Rectangle(workArea.x, bottomY, halfWidth, workArea.height - (bottomY - workArea.y))
        WindowSnapMode.BOTTOM_RIGHT ->
            Rectangle(rightX, bottomY, workArea.width - (rightX - workArea.x), workArea.height - (bottomY - workArea.y))
        WindowSnapMode.FLOATING -> null
    }
}

private fun boundsMatch(a: Rectangle, b: Rectangle, tolerance: Int = 2): Boolean {
    return abs(a.x - b.x) <= tolerance &&
        abs(a.y - b.y) <= tolerance &&
        abs(a.width - b.width) <= tolerance &&
        abs(a.height - b.height) <= tolerance
}

private fun resolveSnapModeFromBounds(bounds: Rectangle, workArea: Rectangle): WindowSnapMode {
    val target = determineSnapTargetByBounds(bounds, workArea) ?: return WindowSnapMode.FLOATING
    val expected = computeSnapBounds(workArea, target) ?: return WindowSnapMode.FLOATING
    return if (boundsMatch(bounds, expected)) target else WindowSnapMode.FLOATING
}

private fun applySnapBounds(window: java.awt.Window, workArea: Rectangle, snapMode: WindowSnapMode) {
    val target = computeSnapBounds(workArea, snapMode) ?: return
    val adjusted = if (isWindows()) adjustBoundsForResizeBorder(window, target) else target
    if (window.x == adjusted.x && window.y == adjusted.y &&
        window.width == adjusted.width && window.height == adjusted.height
    ) {
        return
    }
    window.setBounds(adjusted)
}

private fun adjustBoundsForResizeBorder(window: java.awt.Window, bounds: Rectangle): Rectangle {
    val border = getResizeBorderThickness(window)
    if (border.x <= 0 && border.y <= 0) return bounds
    return Rectangle(
        bounds.x - border.x,
        bounds.y - border.y,
        bounds.width + border.x * 2,
        bounds.height + border.y * 2,
    )
}

private fun getVisualBoundsForSnap(window: java.awt.Window, bounds: Rectangle = window.bounds): Rectangle {
    val border = getResizeBorderThickness(window)
    if (border.x <= 0 && border.y <= 0) return Rectangle(bounds)
    val width = (bounds.width - border.x * 2).coerceAtLeast(1)
    val height = (bounds.height - border.y * 2).coerceAtLeast(1)
    return Rectangle(
        bounds.x + border.x,
        bounds.y + border.y,
        width,
        height,
    )
}

private fun getResizeBorderThickness(window: java.awt.Window): Rectangle {
    if (!isWindows()) return Rectangle(0, 0, 0, 0)
    val scaleX = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
    val scaleY = window.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0
    val padded = User32.INSTANCE.GetSystemMetrics(SM_CXPADDEDBORDER)
    val rawX = User32.INSTANCE.GetSystemMetrics(SM_CXSIZEFRAME) + padded
    val rawY = User32.INSTANCE.GetSystemMetrics(SM_CYSIZEFRAME) + padded
    val x = (rawX / scaleX).roundToInt()
    val y = (rawY / scaleY).roundToInt()
    return Rectangle(x, y, 0, 0)
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true
}

private const val SM_CXSIZEFRAME = 32
private const val SM_CYSIZEFRAME = 33
private const val SM_CXPADDEDBORDER = 92

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
    val browseId: String? = null,
    val browseParams: String? = null,
)

fun main(args: Array<String>) {
    if (args.any { it == "--check-updates-once" }) {
        runBlocking {
            val preferences = DesktopPreferences.getInstance()
            val result = DesktopUpdater.checkForUpdates(preferences = preferences, force = true)
            if (result.isSuccess) {
                val report = result.getOrThrow()
                if (report.isUpdateAvailable) {
                    DesktopUpdateInstaller.downloadAndInstall(report.releaseInfo.downloadUrl)
                }
            }
        }
        return
    }

    application {
        val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)
        val windowIcon = remember { loadBitmapResource("drawable/ic_anitail.png") }
        Window(
            onCloseRequest = ::exitApplication,
            title = "AniTail Desktop",
            state = windowState,
            icon = windowIcon?.let { BitmapPainter(it) },
            undecorated = true,
        ) {
            AniTailDesktopApp(
                windowState = windowState,
                onCloseRequest = ::exitApplication,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameWindowScope.AniTailDesktopApp(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    // Replace legacy store with Database and Preferences
    val database = remember { DesktopDatabase.getInstance() }
    val downloadService = remember { DesktopDownloadService() }
    val preferences = remember { DesktopPreferences.getInstance() }
    val authService = remember { DesktopAuthService() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerState = rememberPlayerState()
    val appLanguage by preferences.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AndroidStringLoader.load(appLanguage) }
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlackEnabled by preferences.pureBlack.collectAsState()
    val dynamicColorEnabled by preferences.dynamicColor.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicksMode by preferences.quickPicks.collectAsState()
    val defaultLibChip by preferences.defaultLibChip.collectAsState()
    val pauseListenHistory by preferences.pauseListenHistory.collectAsState()
    val historyDuration by preferences.historyDuration.collectAsState()
    val useLoginForBrowse by preferences.useLoginForBrowse.collectAsState()
    val ytmSync by preferences.ytmSync.collectAsState()
    val proxyEnabled by preferences.proxyEnabled.collectAsState()
    val proxyType by preferences.proxyType.collectAsState()
    val proxyUrl by preferences.proxyUrl.collectAsState()
    val proxyUsername by preferences.proxyUsername.collectAsState()
    val proxyPassword by preferences.proxyPassword.collectAsState()
    val discordUsername by preferences.discordUsername.collectAsState()
    val discordAvatarUrl by preferences.discordAvatarUrl.collectAsState()
    val preferredAvatarSource by preferences.preferredAvatarSource.collectAsState()
    val defaultOpenTab by preferences.defaultOpenTab.collectAsState()
    val densityScale by preferences.densityScale.collectAsState()
    val slimNavBar by preferences.slimNavBar.collectAsState()
    val autoLoadMore by preferences.autoLoadMore.collectAsState()
    val similarContentEnabled by preferences.similarContentEnabled.collectAsState()
    val autoSkipNextOnError by preferences.autoSkipNextOnError.collectAsState()
    val autoStartRadio by preferences.autoStartRadio.collectAsState()
    val autoBackupEnabled by preferences.autoBackupEnabled.collectAsState()
    val autoBackupFrequencyHours by preferences.autoBackupFrequencyHours.collectAsState()
    val autoBackupKeepCount by preferences.autoBackupKeepCount.collectAsState()
    val autoBackupUseCustomLocation by preferences.autoBackupUseCustomLocation.collectAsState()
    val autoBackupCustomLocation by preferences.autoBackupCustomLocation.collectAsState()
    val autoUpdateEnabled by preferences.autoUpdateEnabled.collectAsState()
    val autoUpdateFrequency by preferences.autoUpdateCheckFrequency.collectAsState()
    val latestVersionName by preferences.latestVersionName.collectAsState()
    val syncService = remember { LibrarySyncService(database) }
    val isSyncing by syncService.isSyncing.collectAsState()
    val lastSyncError by syncService.lastSyncError.collectAsState()
    var themeColor by remember { mutableStateOf(DefaultThemeColor) }
    var lastArtworkUrl by remember { mutableStateOf<String?>(null) }
    var queueContinuationInFlight by remember { mutableStateOf(false) }
    var trackedLastFmSongId by remember { mutableStateOf<String?>(null) }
    var lastFmSongStartTimeMs by remember { mutableStateOf(0L) }
    var hasScrobbledCurrentSong by remember { mutableStateOf(false) }
    var notifiedUpdateVersion by remember { mutableStateOf<String?>(null) }
    val currentVersionName = remember { DesktopUpdater.currentVersionName() }
    val showUpdateBadge = remember(latestVersionName, currentVersionName) {
        latestVersionName.isNotBlank() &&
            DesktopUpdater.isVersionNewer(latestVersionName, currentVersionName)
    }

    var currentScreen by remember {
        mutableStateOf(
            when (defaultOpenTab) {
                NavigationTabPreference.HOME -> DesktopScreen.Home
                NavigationTabPreference.EXPLORE -> DesktopScreen.Explore
                NavigationTabPreference.LIBRARY -> DesktopScreen.Library
            },
        )
    }
    var authCredentials by remember { mutableStateOf(authService.credentials) }
    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var wasSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(strings.locale) {
        Locale.setDefault(strings.locale)
    }

    LaunchedEffect(defaultLibChip) {
        preferences.setLibraryFilter(defaultLibChip)
    }

    LaunchedEffect(authCredentials?.cookie) {
        accountInfo = authService.refreshAccountInfo()
    }

    LaunchedEffect(
        autoBackupEnabled,
        autoBackupFrequencyHours,
        autoBackupKeepCount,
        autoBackupUseCustomLocation,
        autoBackupCustomLocation,
    ) {
        DesktopAutoBackupService.startOrUpdate(
            preferences = preferences,
            authService = authService,
        )
    }

    LaunchedEffect(autoUpdateEnabled, autoUpdateFrequency) {
        DesktopUpdateBackgroundScheduler.startOrUpdate(preferences)
    }

    LaunchedEffect(autoUpdateEnabled, autoUpdateFrequency, strings.locale) {
        if (!autoUpdateEnabled || autoUpdateFrequency == UpdateCheckFrequency.NEVER) {
            return@LaunchedEffect
        }

        while (true) {
            val checkResult = DesktopUpdater.maybeCheckForUpdates(preferences).getOrNull()
            if (checkResult != null &&
                checkResult.isUpdateAvailable &&
                checkResult.releaseInfo.versionName != notifiedUpdateVersion
            ) {
                notifiedUpdateVersion = checkResult.releaseInfo.versionName
                val snackbarResult = snackbarHostState.showSnackbar(
                    message = strings.get("new_version_available_toast", checkResult.releaseInfo.versionName),
                    actionLabel = strings.get("download_update"),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    val installResult = DesktopUpdateInstaller.downloadAndInstall(checkResult.releaseInfo.downloadUrl)
                    if (installResult.isFailure) {
                        snackbarHostState.showSnackbar(
                            message = strings.get("update_check_failed"),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
            delay(60_000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            DesktopAutoBackupService.stop()
        }
    }

    LaunchedEffect(authCredentials?.visitorData, authCredentials?.cookie) {
        if (authCredentials?.visitorData.isNullOrBlank() && authCredentials?.cookie?.isNotBlank() == true) {
            authService.ensureVisitorData()
            authCredentials = authService.credentials
        }
    }

    LaunchedEffect(playerState.currentItem?.id) {
        val currentItem = playerState.currentItem
        trackedLastFmSongId = currentItem?.id
        lastFmSongStartTimeMs = System.currentTimeMillis()
        hasScrobbledCurrentSong = false
        if (currentItem != null) {
            DesktopLastFmService.updateNowPlaying(currentItem)
            DesktopLastFmService.retryPendingScrobbles()
        }
    }

    LaunchedEffect(playerState.isPlaying, playerState.currentItem?.id) {
        val trackedId = playerState.currentItem?.id ?: return@LaunchedEffect
        if (playerState.isPlaying) {
            playerState.currentItem?.let { DesktopLastFmService.updateNowPlaying(it) }
        }
        while (
            playerState.isPlaying &&
            !hasScrobbledCurrentSong &&
            playerState.currentItem?.id == trackedId &&
            trackedLastFmSongId == trackedId
        ) {
            val realPlayedMs = System.currentTimeMillis() - lastFmSongStartTimeMs
            if (realPlayedMs >= 30_000L && playerState.position >= 30_000L) {
                val item = playerState.currentItem
                if (item != null && item.id == trackedId) {
                    hasScrobbledCurrentSong = true
                    val timestampSec = (lastFmSongStartTimeMs / 1000L).coerceAtLeast(1L)
                    DesktopLastFmService.scrobble(item, timestampSec)
                }
                break
            }
            delay(1_000L)
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
    val songArtistMaps by database.songArtistMaps.collectAsState(initial = emptyList())
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

    DisposableEffect(
        playerState,
        autoLoadMore,
        similarContentEnabled,
        autoSkipNextOnError,
        autoStartRadio,
    ) {
        val shouldLoadMore = autoLoadMore || similarContentEnabled

        fun continueQueue(seedItem: LibraryItem, triggeredByError: Boolean) {
            if (queueContinuationInFlight) return
            if (playerState.currentItem?.id != seedItem.id) return
            scope.launch {
                queueContinuationInFlight = true
                try {
                    if (triggeredByError && autoSkipNextOnError && playerState.canSkipNext) {
                        playerState.skipToNext()
                        return@launch
                    }
                    if (!shouldLoadMore && !autoStartRadio) return@launch

                    val videoId = seedItem.id.ifBlank { extractVideoId(seedItem.playbackUrl).orEmpty() }
                    if (videoId.isBlank()) return@launch

                    val result = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return@launch
                    val plan = buildRadioQueuePlan(seedItem, result)
                    if (plan.items.isEmpty()) return@launch

                    if (shouldLoadMore) {
                        val existingIds = playerState.queue.mapTo(mutableSetOf()) { it.id }
                        val additions = plan.items.filter { candidate ->
                            candidate.id != seedItem.id && existingIds.add(candidate.id)
                        }
                        additions.forEach { playerState.addToQueue(it) }
                    }

                    if (playerState.currentItem?.id != seedItem.id) return@launch

                    if (playerState.canSkipNext) {
                        playerState.skipToNext()
                        return@launch
                    }

                    if (autoStartRadio) {
                        val startIndex = plan.items.indexOfFirst { it.id != seedItem.id }
                            .takeIf { it >= 0 } ?: plan.startIndex
                        playerState.playQueue(plan.items, startIndex = startIndex)
                    }
                } finally {
                    queueContinuationInFlight = false
                }
            }
        }

        playerState.onQueueEnded = { endedItem ->
            continueQueue(endedItem, triggeredByError = false)
        }
        playerState.onPlaybackErrorEvent = { failedItem, _ ->
            continueQueue(failedItem, triggeredByError = true)
        }

        onDispose {
            playerState.onQueueEnded = null
            playerState.onPlaybackErrorEvent = null
        }
    }

    LaunchedEffect(playerState.currentItem?.id) {
        val item = playerState.currentItem ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
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
                    artistName = if (songDetails.artists.isNotEmpty()) {
                        songDetails.artists.joinToString(", ") { it.name }
                    } else {
                        base.artistName
                    },
                    explicit = base.explicit || songDetails.explicit,
                    dateModified = LocalDateTime.now(),
                )
                database.updateSong(updated)
                database.insertSongArtistMaps(songDetails.toSongArtistMaps())

                songDetails.artists.mapNotNull { it.id }.forEach { ensureArtist(it) }
                songDetails.album?.id?.let { ensureAlbum(it) }
            }

            if (!database.hasRelatedSongs(item.id)) {
                val relatedEndpoint = nextResult?.relatedEndpoint ?: return@withContext
                val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return@withContext
                val relatedSongs = relatedPage.songs
                relatedSongs.forEach { song ->
                    database.insertSong(song.toSongEntity(), song.toSongArtistMaps())
                }
                database.insertRelatedSongs(item.id, relatedSongs.map { it.id })
            }
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
        proxyEnabled,
        proxyType,
        proxyUrl,
        proxyUsername,
        proxyPassword,
    ) {
        val credentials = authCredentials
        val proxyConfig = buildProxyConfig(
            enabled = proxyEnabled,
            type = proxyType,
            url = proxyUrl,
            username = proxyUsername,
            password = proxyPassword,
        )
        YouTube.cookie = credentials?.cookie ?: youtubeCookie
        YouTube.visitorData = credentials?.visitorData
        YouTube.dataSyncId = normalizeDataSyncId(credentials?.dataSyncId)
        YouTube.proxy = proxyConfig.proxy
        YouTube.proxyAuth = proxyConfig.proxyAuth
        YouTube.useLoginForBrowse = useLoginForBrowse
        YouTube.locale = com.anitail.innertube.models.YouTubeLocale(
            hl = resolveContentLanguage(contentLanguage),
            gl = resolveContentCountry(contentCountry)
        )
        println("Anitail: YouTube API initialized with Cookie: ${youtubeCookie?.take(20)}..., Locale: $contentLanguage-$contentCountry")
    }

    // Initial page loading and reload on auth changes
    LaunchedEffect(
        authCredentials,
        useLoginForBrowse,
        contentLanguage,
        contentCountry,
        proxyEnabled,
        proxyType,
        proxyUrl,
        proxyUsername,
        proxyPassword,
    ) {
        // Wait for YouTube API to be updated by the other LaunchedEffect
        // or just update it here as well for safety
        val credentials = authCredentials
        val proxyConfig = buildProxyConfig(
            enabled = proxyEnabled,
            type = proxyType,
            url = proxyUrl,
            username = proxyUsername,
            password = proxyPassword,
        )
        YouTube.cookie = credentials?.cookie ?: youtubeCookie
        YouTube.visitorData = credentials?.visitorData
        YouTube.dataSyncId = normalizeDataSyncId(credentials?.dataSyncId)
        YouTube.proxy = proxyConfig.proxy
        YouTube.proxyAuth = proxyConfig.proxyAuth
        YouTube.useLoginForBrowse = useLoginForBrowse
        YouTube.locale = com.anitail.innertube.models.YouTubeLocale(
            hl = resolveContentLanguage(contentLanguage),
            gl = resolveContentCountry(contentCountry)
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
        songArtistMaps,
        events,
        hideExplicit,
        quickPicksMode,
    ) {
        val recommendations = buildHomeRecommendations(
            songs = allSongs,
            albums = albums,
            artists = artists,
            songArtistMaps = songArtistMaps,
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
                val artistId = songEntity?.let { primaryArtistIdForSong(it.id, songArtistMaps) }
                val artistEntity = artistId?.let { id ->
                    artists.firstOrNull { it.id == id }
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

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, densityScale) {
        Density(
            density = (baseDensity.density * densityScale).coerceAtLeast(0.1f),
            fontScale = (baseDensity.fontScale * densityScale).coerceAtLeast(0.1f),
        )
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalDensity provides scaledDensity,
    ) {
        AnitailTheme(
            darkMode = darkMode,
            pureBlack = pureBlackEnabled,
            themeColor = themeColor,
        ) {
            val windowCornerRadius = 12.dp
            var snapMode by remember { mutableStateOf(WindowSnapMode.FLOATING) }
            var windowBounds by remember { mutableStateOf(window.bounds) }
            val isMaximizedByBounds = isWindowAtWorkArea(
                getVisualBoundsForSnap(window, windowBounds),
                getWorkAreaForWindow(window),
            )
            val isSnapped = snapMode != WindowSnapMode.FLOATING || isMaximizedByBounds
            val showMaximizedState = isSnapped
            val lastFloatingBounds = remember { mutableStateOf(window.bounds) }
            val youtubeAccountName = accountInfo?.name
                ?.takeUnless { it.isBlank() }
                ?: authCredentials?.accountName?.takeUnless { it.isBlank() }
            val youtubeAccountAvatar = accountInfo?.thumbnailUrl?.takeUnless { it.isBlank() }
                ?: authCredentials?.accountImageUrl?.takeUnless { it.isBlank() }
            val discordAccountName = discordUsername.takeUnless { it.isBlank() }
            val discordAccountAvatar = discordAvatarUrl.takeUnless { it.isBlank() }
            val topBarAccountName = when (preferredAvatarSource) {
                AvatarSourcePreference.YOUTUBE -> youtubeAccountName ?: discordAccountName
                AvatarSourcePreference.DISCORD -> discordAccountName ?: youtubeAccountName
            }
            val topBarAccountAvatar = when (preferredAvatarSource) {
                AvatarSourcePreference.YOUTUBE -> youtubeAccountAvatar ?: discordAccountAvatar
                AvatarSourcePreference.DISCORD -> discordAccountAvatar ?: youtubeAccountAvatar
            }
            val windowShape: Shape = if (isSnapped) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(windowCornerRadius)
            }
            val density = LocalDensity.current
            val topBarHeight = 48.dp
            val borderColor = if (pureBlackEnabled) {
                Color(0xFF2A2A2A)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

        LaunchedEffect(Unit) {
            windowState.isMinimized = false
        }

        LaunchedEffect(windowState.placement) {
            if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                if (snapMode == WindowSnapMode.FLOATING && isValidBounds(window.bounds)) {
                    lastFloatingBounds.value = Rectangle(window.bounds)
                }
                snapMode = WindowSnapMode.MAXIMIZED
                windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
                applySnapBounds(window, getWorkAreaForWindow(window), snapMode)
            }
        }

            DisposableEffect(window, windowState.placement, density) {
            fun updateWindowBounds() {
                val bounds = window.bounds
                if (bounds != windowBounds) {
                    windowBounds = Rectangle(bounds)
                }
            }

            fun updateSnapModeFromBounds() {
                if (windowState.isMinimized) return
                val workArea = getWorkAreaForWindow(window)
                val resolved = resolveSnapModeFromBounds(getVisualBoundsForSnap(window), workArea)
                if (resolved != snapMode) {
                    snapMode = resolved
                }
            }

            fun updateWindowShape() {
                if (snapMode != WindowSnapMode.FLOATING || isWindowAtWorkArea(window)) {
                    val bounds = window.bounds
                    val width = bounds.width.coerceAtLeast(1)
                    val height = bounds.height.coerceAtLeast(1)
                    window.shape = Rectangle2D.Double(
                        0.0,
                        0.0,
                        width.toDouble(),
                        height.toDouble(),
                    )
                    return
                }
                val radiusPx = with(density) { windowCornerRadius.toPx() }
                val bounds = window.bounds
                val width = bounds.width.coerceAtLeast(1)
                val height = bounds.height.coerceAtLeast(1)
                window.shape = RoundRectangle2D.Double(
                    0.0,
                    0.0,
                    width.toDouble(),
                    height.toDouble(),
                    (radiusPx * 2).toDouble(),
                    (radiusPx * 2).toDouble(),
                )
            }

            fun updateLastFloatingBounds() {
                if (snapMode != WindowSnapMode.FLOATING) return
                if (isWindowAtWorkArea(window)) return
                val bounds = window.bounds
                if (isValidBounds(bounds)) {
                    lastFloatingBounds.value = Rectangle(bounds)
                }
            }

            fun applyMaximizedBounds() {
                if (snapMode == WindowSnapMode.FLOATING) return
                if (windowState.isMinimized) return
                applySnapBounds(window, getWorkAreaForWindow(window), snapMode)
            }

            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    updateWindowBounds()
                    updateSnapModeFromBounds()
                    updateWindowShape()
                    applyMaximizedBounds()
                    updateLastFloatingBounds()
                }

                override fun componentMoved(e: ComponentEvent) {
                    updateWindowBounds()
                    updateLastFloatingBounds()
                }
            }
            updateWindowBounds()
            updateSnapModeFromBounds()
            updateWindowShape()
            applyMaximizedBounds()
            updateLastFloatingBounds()
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
            }
        }

        val baseModifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
        val windowModifier = if (isSnapped) {
            baseModifier
        } else {
            baseModifier
                .clip(windowShape)
                .border(1.dp, borderColor, windowShape)
        }
        BoxWithConstraints(
            modifier = windowModifier
        ) {
            val navigationBarHeight = if (slimNavBar) 64.dp else NavigationBarHeight
            val contentHeight = maxHeight - topBarHeight
            val playerBottomSheetState =
                rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = navigationBarHeight + MiniPlayerBottomSpacing + MiniPlayerHeight,
                    expandedBound = contentHeight,
                )
            val playerBottomPadding = if (!playerBottomSheetState.isDismissed) {
                MiniPlayerHeight + MiniPlayerBottomSpacing
            } else {
                0.dp
            }
            val contentBottomPadding = navigationBarHeight + playerBottomPadding
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
                                navigationHistory.add(currentScreen)
                                currentScreen = DesktopScreen.Search
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
                            accountDisplayName = topBarAccountName,
                            accountAvatarUrl = topBarAccountAvatar,
                            pureBlack = pureBlackEnabled,
                            isMaximized = showMaximizedState,
                            window = window,
                            windowState = windowState,
                            onToggleMaximize = {
                                windowState.isMinimized = false
                                if (isSnapped) {
                                    snapMode = WindowSnapMode.FLOATING
                                    val bounds = lastFloatingBounds.value
                                    if (isValidBounds(bounds)) {
                                        window.setBounds(bounds)
                                    }
                                } else {
                                    if (isValidBounds(window.bounds)) {
                                        lastFloatingBounds.value = Rectangle(window.bounds)
                                    }
                                    snapMode = WindowSnapMode.MAXIMIZED
                                    applySnapBounds(window, getWorkAreaForWindow(window), snapMode)
                                }
                            },
                            onRestoreFromSnap = {
                                if (isSnapped) {
                                    windowState.isMinimized = false
                                    snapMode = WindowSnapMode.FLOATING
                                    val bounds = lastFloatingBounds.value
                                    if (isValidBounds(bounds)) {
                                        window.setBounds(bounds)
                                    }
                                }
                            },
                            onSnapFromDragEnd = { point ->
                                val workArea = getWorkAreaForWindow(window)
                                val target = determineSnapTargetByPointer(point, workArea)
                                    ?: determineSnapTargetByBounds(getVisualBoundsForSnap(window), workArea)
                                    ?: return@DesktopTopBar
                                if (snapMode == WindowSnapMode.FLOATING && isValidBounds(window.bounds)) {
                                    lastFloatingBounds.value = Rectangle(window.bounds)
                                }
                                snapMode = target
                                applySnapBounds(window, workArea, snapMode)
                            },
                            onWindowClose = onCloseRequest,
                            onRefreshHome = if (currentScreen == DesktopScreen.Home) refreshHome else null,
                            showUpdateBadge = showUpdateBadge,
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
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
                            accountName = when (preferredAvatarSource) {
                                AvatarSourcePreference.YOUTUBE -> accountInfo?.name ?: authCredentials?.accountName
                                AvatarSourcePreference.DISCORD -> discordUsername
                                    .takeUnless { it.isBlank() }
                                    ?: accountInfo?.name
                                    ?: authCredentials?.accountName
                            },
                            accountThumbnailUrl = when (preferredAvatarSource) {
                                AvatarSourcePreference.YOUTUBE ->
                                    accountInfo?.thumbnailUrl ?: authCredentials?.accountImageUrl
                                AvatarSourcePreference.DISCORD -> discordAvatarUrl
                                    .takeUnless { it.isBlank() }
                                    ?: accountInfo?.thumbnailUrl
                                    ?: authCredentials?.accountImageUrl
                            },
                            database = database,
                            downloadService = downloadService,
                            playlists = playlists,
                            songsById = songsById,
                            songArtistMaps = songArtistMaps,
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
                                            val id = route.removePrefix("browse/")
                                            if (id.isNotBlank()) {
                                                navigationHistory.add(DesktopScreen.Home)
                                                detailNavigation = detailNavigation.copy(
                                                    browseId = id,
                                                    browseParams = null,
                                                )
                                                currentScreen = DesktopScreen.Browse
                                            }
                                        }
                                    }
                                }
                            },
                            onLocalItemSelected = { item ->
                                when (resolveLocalItemType(item)) {
                                    LocalItemType.SONG -> {
                                        playerState.play(item)
                                        playerBottomSheetState.collapseSoft()
                                        scope.launch {
                                            val videoId = extractVideoId(item.playbackUrl) ?: return@launch
                                            val result = YouTube.next(
                                                WatchEndpoint(videoId = videoId)
                                            ).getOrNull() ?: return@launch
                                            val plan = buildRadioQueuePlan(item, result)
                                            if (playerState.currentItem?.id == item.id) {
                                                playerState.playQueue(
                                                    plan.items,
                                                    plan.startIndex,
                                                    preserveCurrentPlayback = true,
                                                )
                                            }
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
                            explorePage = explorePage,
                            chartsPage = chartsPage,
                            isLoading = isExploreLoading,
                            playerState = playerState,
                            database = database,
                            downloadService = downloadService,
                            playlists = playlists,
                            songsById = songsById,
                            onPlay = { item ->
                                playerState.play(item)
                                playerBottomSheetState.collapseSoft()
                            },
                            onOpenArtist = { artistId, artistName ->
                                navigationHistory.add(DesktopScreen.Explore)
                                detailNavigation = detailNavigation.copy(
                                    artistId = artistId,
                                    artistName = artistName,
                                )
                                currentScreen = DesktopScreen.ArtistDetail
                            },
                            onOpenAlbum = { albumId, albumName ->
                                navigationHistory.add(DesktopScreen.Explore)
                                detailNavigation = detailNavigation.copy(
                                    albumId = albumId,
                                    albumName = albumName,
                                )
                                currentScreen = DesktopScreen.AlbumDetail
                            },
                            onBrowse = { browseId, params ->
                                navigationHistory.add(DesktopScreen.Explore)
                                detailNavigation = detailNavigation.copy(
                                    browseId = browseId,
                                    browseParams = params,
                                )
                                currentScreen = DesktopScreen.Browse
                            },
                            onMoodGreClick = { currentScreen = DesktopScreen.MoodAndGenres },
                            onNewReleaseClick = { currentScreen = DesktopScreen.NewRelease },
                        )
                    }

                DesktopScreen.Library -> {
                    LibraryScreen(
                        database = database,
                        downloadService = downloadService,
                        preferences = preferences,
                        playerState = playerState,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.Library)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.Library)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onOpenPlaylist = { playlistId, playlistName ->
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
                        },
                    )
                }

                DesktopScreen.History -> {
                    HistoryScreen(
                        database = database,
                        downloadService = downloadService,
                        playerState = playerState,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.History)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.History)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                    )
                }

                DesktopScreen.Stats -> {
                    StatsScreen(
                        database = database,
                        downloadService = downloadService,
                        playerState = playerState,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.Stats)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.Stats)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                    )
                }

                DesktopScreen.Settings -> {
                    SettingsScreen(
                        preferences = preferences,
                        downloadService = downloadService,
                        authService = authService,
                        authCredentials = authCredentials,
                        accountInfo = accountInfo,
                        playerState = playerState,
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
                        onCategoryClick = { browseId, params, _ ->
                            if (browseId.isNotBlank()) {
                                navigationHistory.add(DesktopScreen.MoodAndGenres)
                                detailNavigation = detailNavigation.copy(
                                    browseId = browseId,
                                    browseParams = params,
                                )
                                currentScreen = DesktopScreen.Browse
                            }
                        },
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

                DesktopScreen.Browse -> {
                    BrowseScreen(
                        browseId = detailNavigation.browseId,
                        browseParams = detailNavigation.browseParams,
                        hideExplicit = hideExplicit,
                        playerState = playerState,
                        database = database,
                        downloadService = downloadService,
                        playlists = playlists,
                        songsById = songsById,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.Browse)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.Browse)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onOpenPlaylist = { playlistId, playlistName ->
                            navigationHistory.add(DesktopScreen.Browse)
                            detailNavigation = detailNavigation.copy(
                                playlistId = playlistId,
                                playlistName = playlistName,
                            )
                            currentScreen = DesktopScreen.PlaylistDetail
                        },
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                    )
                }

                DesktopScreen.ArtistDetail -> {
                    ArtistDetailScreen(
                        artistId = detailNavigation.artistId.orEmpty(),
                        artistName = detailNavigation.artistName.orEmpty(),
                        playerState = playerState,
                        database = database,
                        downloadService = downloadService,
                        playlists = playlists,
                        songsById = songsById,
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
                        },
                        onBrowse = { browseId, params ->
                            navigationHistory.add(DesktopScreen.ArtistDetail)
                            detailNavigation = detailNavigation.copy(
                                browseId = browseId,
                                browseParams = params,
                            )
                            currentScreen = DesktopScreen.ArtistItems
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

                DesktopScreen.ArtistItems -> {
                    ArtistItemsScreen(
                        browseId = detailNavigation.browseId.orEmpty(),
                        browseParams = detailNavigation.browseParams,
                        hideExplicit = hideExplicit,
                        playerState = playerState,
                        database = database,
                        downloadService = downloadService,
                        playlists = playlists,
                        songsById = songsById,
                        onOpenArtist = { artistId, artistName ->
                            navigationHistory.add(DesktopScreen.ArtistItems)
                            detailNavigation = detailNavigation.copy(
                                artistId = artistId,
                                artistName = artistName,
                            )
                            currentScreen = DesktopScreen.ArtistDetail
                        },
                        onOpenAlbum = { albumId, albumName ->
                            navigationHistory.add(DesktopScreen.ArtistItems)
                            detailNavigation = detailNavigation.copy(
                                albumId = albumId,
                                albumName = albumName,
                            )
                            currentScreen = DesktopScreen.AlbumDetail
                        },
                        onOpenPlaylist = { playlistId, playlistName ->
                            navigationHistory.add(DesktopScreen.ArtistItems)
                            detailNavigation = detailNavigation.copy(
                                playlistId = playlistId,
                                playlistName = playlistName,
                            )
                            currentScreen = DesktopScreen.PlaylistDetail
                        },
                        onBack = {
                            currentScreen = navigationHistory.removeLastOrNull() ?: DesktopScreen.Home
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
                        database = database,
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
                    }
                }

                NavigationBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(navigationBarHeight)
                        .offset(y = navigationBarHeight * playerBottomSheetState.progress.coerceIn(0f, 1f)),
                    containerColor = if (pureBlackEnabled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (pureBlackEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    NavigationBarItem(
                        selected = currentScreen == DesktopScreen.Home,
                        onClick = { currentScreen = DesktopScreen.Home },
                        label = if (slimNavBar) null else {
                            { Text(stringResource("home")) }
                        },
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
                        label = if (slimNavBar) null else {
                            { Text(stringResource("explore")) }
                        },
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
                        label = if (slimNavBar) null else {
                            { Text(stringResource("filter_library")) }
                        },
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
    }

    // El reproductor nativo ahora actualiza la posición automáticamente
}

private suspend fun fetchThemeColorFromUrl(url: String): Color? = withContext(Dispatchers.IO) {
    val bytes = runCatching { URL(url).readBytes() }.getOrNull() ?: return@withContext null
    val bitmap = runCatching { Image.makeFromEncoded(bytes).asImageBitmap() }.getOrNull()
        ?: return@withContext null
    runCatching { bitmap.extractThemeColor() }.getOrNull()
}

private data class DesktopProxyConfig(
    val proxy: Proxy?,
    val proxyAuth: String?,
)

private fun buildProxyConfig(
    enabled: Boolean,
    type: ProxyTypePreference,
    url: String,
    username: String,
    password: String,
): DesktopProxyConfig {
    if (!enabled) return DesktopProxyConfig(proxy = null, proxyAuth = null)

    val normalized = url.trim()
    if (normalized.isBlank()) return DesktopProxyConfig(proxy = null, proxyAuth = null)

    val parsed = runCatching {
        val uri = if (normalized.contains("://")) {
            java.net.URI(normalized)
        } else {
            java.net.URI("http://$normalized")
        }
        val host = uri.host?.trim().orEmpty()
        val port = if (uri.port > 0) uri.port else 8080
        if (host.isBlank()) return@runCatching null
        host to port
    }.getOrNull() ?: return DesktopProxyConfig(proxy = null, proxyAuth = null)

    val proxyType = when (type) {
        ProxyTypePreference.SOCKS -> Proxy.Type.SOCKS
        ProxyTypePreference.HTTP -> Proxy.Type.HTTP
    }
    val proxy = Proxy(proxyType, InetSocketAddress(parsed.first, parsed.second))

    val hasAuth = username.isNotBlank() || password.isNotBlank()
    val proxyAuth = if (hasAuth) {
        val raw = "${username.trim()}:${password.trim()}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        "Basic $encoded"
    } else {
        null
    }

    return DesktopProxyConfig(proxy = proxy, proxyAuth = proxyAuth)
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
