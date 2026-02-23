package com.anitail.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.music.constants.AppBarHeight
import com.anitail.music.constants.DarkModeKey
import com.anitail.music.constants.CustomThemeSeedColorKey
import com.anitail.music.constants.DefaultOpenTabKey
import com.anitail.music.constants.DisableScreenshotKey
import com.anitail.music.constants.DynamicIconKey
import com.anitail.music.constants.DynamicThemeKey
import com.anitail.music.constants.HighRefreshRateKey
import com.anitail.music.constants.MiniPlayerBottomSpacing
import com.anitail.music.constants.MiniPlayerHeight
import com.anitail.music.constants.NavigationBarAnimationSpec
import com.anitail.music.constants.NavigationBarHeight
import com.anitail.music.constants.PauseSearchHistoryKey
import com.anitail.music.constants.PureBlackKey
import com.anitail.music.constants.SearchSource
import com.anitail.music.constants.SearchSourceKey
import com.anitail.music.constants.SlimNavBarHeight
import com.anitail.music.constants.SlimNavBarKey
import com.anitail.music.constants.ShowLyricsKey
import com.anitail.music.constants.StopMusicOnTaskClearKey
import com.anitail.music.constants.ThemePalette
import com.anitail.music.constants.ThemePaletteKey
import com.anitail.music.constants.UseNewMiniPlayerDesignKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.SearchHistory
import com.anitail.music.downloads.DownloadLibraryRepository
import com.anitail.music.extensions.toEnum
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.DownloadUtil
import com.anitail.music.playback.MusicService
import com.anitail.music.playback.MusicService.MusicBinder
import com.anitail.music.playback.PlayerConnection
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.services.AutoBackupWorker
import com.anitail.music.ui.component.BottomSheetMenu
import com.anitail.music.ui.component.BottomSheetPage
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.LocalBottomSheetPageState
import com.anitail.music.ui.component.LocalMenuState
import com.anitail.music.ui.component.TopSearch
import com.anitail.music.ui.component.rememberBottomSheetState
import com.anitail.music.ui.component.shimmer.ShimmerTheme
import com.anitail.music.ui.menu.YouTubeSongMenu
import com.anitail.music.ui.player.BottomSheetPlayer
import com.anitail.music.ui.screens.Screens
import com.anitail.music.ui.screens.navigationBuilder
import com.anitail.music.ui.screens.search.LocalSearchScreen
import com.anitail.music.ui.screens.search.OnlineSearchScreen
import com.anitail.music.ui.screens.settings.DarkMode
import com.anitail.music.ui.screens.settings.NavigationTab
import com.anitail.music.ui.theme.AnitailTheme
import com.anitail.music.ui.theme.ColorSaver
import com.anitail.music.ui.theme.DefaultThemeColor
import com.anitail.music.ui.theme.ThemePreviewState
import com.anitail.music.ui.theme.extractThemeColor
import com.anitail.music.ui.utils.LocalIsTelevision
import com.anitail.music.ui.theme.seedColor
import com.anitail.music.ui.utils.appBarScrollBehavior
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.ui.utils.rememberIsTelevision
import com.anitail.music.ui.utils.resetHeightOffset
import com.anitail.music.ui.utils.tvFocusable
import com.anitail.music.utils.LocaleManager
import com.anitail.music.utils.PermissionHelper
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.Updater
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import com.anitail.music.utils.reportException
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var downloadLibraryRepository: DownloadLibraryRepository

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    private var pendingExternalAction by mutableStateOf<String?>(null)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    /**
     * Request storage permissions at startup if not already granted.
     * Required for MediaStore downloads to Music/Anitail folder.
     */
    private fun requestStoragePermissionsIfNeeded() {
        // Check if permissions are already granted
        if (PermissionHelper.hasMediaStoreWritePermission(this)) {
            Timber.d("Storage permissions already granted")
            return
        }

        // Get required permissions for current Android version
        val permissions = PermissionHelper.getRequiredWritePermissions()
        if (permissions.isEmpty()) {
            // Android 10+ with no permissions needed (shouldn't happen with our fixed code)
            Timber.d("No storage permissions required")
            return
        }

        // Request permissions
        Timber.d("Requesting storage permissions at startup: ${permissions.joinToString()}")
        ActivityCompat.requestPermissions(this, permissions, 2000)
    }

    override fun onStart() {
        super.onStart()
        startMusicServiceSafely()
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    private fun startMusicServiceSafely() {
        val serviceIntent = Intent(this, MusicService::class.java)
        try {
            startService(serviceIntent)
        } catch (e: IllegalStateException) {
            Timber.w(
                e,
                "MusicService start deferred due background start restriction; binding will proceed"
            )
        }
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dataStore.get(
                StopMusicOnTaskClearKey,
                false
            ) && playerConnection?.isPlaying?.value == true && isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
            unbindService(serviceConnection)
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExternalAction = intent.action
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applySavedLocale(newBase))
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UnusedBoxWithConstraintsScope")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExternalAction = intent.action
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestNotificationPermissionIfNeeded()

        // Request storage permissions at startup for MediaStore downloads
        requestStoragePermissionsIfNeeded()

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            downloadLibraryRepository.cleanupOrphans()
        }

        setContent {
            // JAM: Observe JamViewModel and update MusicService JAM settings
            val jamViewModel: com.anitail.music.ui.screens.settings.JamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val isJamEnabled by jamViewModel.isJamEnabled.collectAsState()
            val isJamHost by jamViewModel.isJamHost.collectAsState()
            val hostIp by jamViewModel.hostIp.collectAsState()

            // Keep MusicService JAM settings in sync with UI
            LaunchedEffect(isJamEnabled, isJamHost, hostIp, playerConnection) {
                playerConnection?.service?.updateJamSettings(
                    enabled = isJamEnabled,
                    isHost = isJamHost,
                    hostIp = hostIp
                )

                // Optimizado: Solo actualizar cuando JAM esté habilitado y sea host
                if (isJamEnabled && isJamHost) {
                    while (isActive) {
                        playerConnection?.service?.lanJamServer?.clientList?.let { clients ->
                            jamViewModel.updateActiveConnections(
                                clients.map { client -> client.ip to client.connectedAt }
                            )
                        }
                        // Incrementar delay para reducir carga del CPU
                        delay(10000) // 10 segundos en lugar de 5
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                    Updater.getLatestVersionName().onSuccess {
                        latestVersionName = it
                    }
                }
            }

            val dynamicIconEnabled by rememberPreference(
                DynamicIconKey,
                defaultValue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            )
            val highRefreshRateEnabled by rememberPreference(
                HighRefreshRateKey,
                defaultValue = false
            )
            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val selectedThemePalette by rememberEnumPreference(
                ThemePaletteKey,
                defaultValue = ThemePalette.LAVENDER
            )
            val customThemeSeedColorInt by rememberPreference(
                CustomThemeSeedColorKey,
                defaultValue = 0xFFB39DDB.toInt()
            )
            val customThemePreviewColorInt = ThemePreviewState.customSeedPreviewColorInt
            val isPaletteCustomizationPreviewActive = ThemePreviewState.isPaletteCustomizationActive
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            LaunchedEffect(dynamicIconEnabled) {
                applyDynamicLauncherIcon(dynamicIconEnabled)
            }
            LaunchedEffect(highRefreshRateEnabled) {
                applyHighRefreshRate(highRefreshRateEnabled)
            }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(
                playerConnection,
                enableDynamicTheme,
                selectedThemePalette,
                customThemeSeedColorInt,
                customThemePreviewColorInt,
                isPaletteCustomizationPreviewActive,
                isSystemInDarkTheme
            ) {
                val playerConnection = playerConnection
                val previewColor = customThemePreviewColorInt
                    ?.takeIf { isPaletteCustomizationPreviewActive }
                    ?.let { Color(it.toLong() and 0xFFFFFFFF) }

                if (previewColor != null) {
                    themeColor = previewColor
                    return@LaunchedEffect
                }
                if (!enableDynamicTheme) {
                    themeColor = selectedThemePalette.seedColor(
                        customSeed = Color(customThemeSeedColorInt.toLong() and 0xFFFFFFFF)
                    )
                    return@LaunchedEffect
                }
                if (playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                // Cache para evitar reprocesar la misma imagen
                var lastThumbnailUrl: String? = null
                
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    themeColor =
                        if (song != null && song.thumbnailUrl != lastThumbnailUrl) {
                            lastThumbnailUrl = song.thumbnailUrl
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val result =
                                        imageLoader.execute(
                                            ImageRequest
                                                .Builder(this@MainActivity)
                                                .data(song.thumbnailUrl)
                                                .allowHardware(false) // pixel access is not supported on Config#HARDWARE bitmaps
                                                .diskCachePolicy(coil.request.CachePolicy.ENABLED) // Cache para mejor rendimiento
                                                .build(),
                                        )
                                    (result.drawable as? BitmapDrawable)?.bitmap?.extractThemeColor()
                                        ?: DefaultThemeColor
                                }.getOrElse { DefaultThemeColor }
                            }
                        } else if (song == null) {
                            lastThumbnailUrl = null
                            DefaultThemeColor
                        } else {
                            themeColor // Mantener color actual si es la misma imagen
                        }
                }
            }

            val isTelevision = rememberIsTelevision()
            CompositionLocalProvider(LocalIsTelevision provides isTelevision) {
                AnitailTheme(
                    darkMode = darkTheme,
                    pureBlack = pureBlack,
                    themeColor = themeColor,
                    preferFidelityStyle = isPaletteCustomizationPreviewActive ||
                    (!enableDynamicTheme && selectedThemePalette == ThemePalette.CUSTOM),
            ) {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                                )
                    ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val (previousTab) = rememberSaveable { mutableStateOf("home") }

                    val navigationItems = remember { Screens.MainScreens }
                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (useNewMiniPlayerDesign) = rememberPreference(
                        UseNewMiniPlayerDesignKey,
                        defaultValue = true
                    )
                    val defaultOpenTab =
                        remember {
                            dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                        }
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_EXPLORE -> NavigationTab.EXPLORE
                                else -> null
                            }
                        }

                    val topLevelScreens =
                        listOf(
                            Screens.Home.route,
                            Screens.Explore.route,
                            Screens.Library.route,
                            "settings",
                        )

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                            if (dataStore[PauseSearchHistoryKey] != true) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }
                    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

                    val shouldShowSearchBar =
                        remember(active, navBackStackEntry) {
                            active ||
                                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                    navBackStackEntry?.destination?.route?.startsWith("search/") == true
                        }

                    val shouldShowNavigationBar =
                        remember(navBackStackEntry, active) {
                            navBackStackEntry?.destination?.route == null ||
                                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                                    !active
                        }

                    fun getNavPadding(): Dp {
                        return if (shouldShowNavigationBar) {
                            if (slimNav) SlimNavBarHeight else NavigationBarHeight
                        } else {
                            0.dp
                        }
                    }

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = "",
                    )

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound = bottomInset + getNavPadding() + (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) + MiniPlayerHeight,
                            expandedBound = maxHeight,
                        )
                    val activePlayerFlow =
                        remember(playerConnection) { playerConnection?.service?.playerFlow }
                    val activePlayer by
                    (activePlayerFlow?.collectAsState(initial = playerConnection?.player)
                        ?: remember { mutableStateOf<Player?>(null) })

                    val playerAwareWindowInsets =
                        remember(
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                            useNewMiniPlayerDesign
                        ) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar) bottom += NavigationBarHeight
                            if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight + (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp)
                            windowsInsets
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    val searchBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    LaunchedEffect(navBackStackEntry) {
                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            val searchQuery =
                                withContext(Dispatchers.IO) {
                                    if (navBackStackEntry
                                            ?.arguments
                                            ?.getString(
                                                "query",
                                            )!!
                                            .contains(
                                                "%",
                                            )
                                    ) {
                                        navBackStackEntry?.arguments?.getString(
                                            "query",
                                        )!!
                                    } else {
                                        URLDecoder.decode(
                                            navBackStackEntry?.arguments?.getString("query")!!,
                                            "UTF-8"
                                        )
                                    }
                                }
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length)
                                )
                            )
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }
                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()
                    }
                    LaunchedEffect(active) {
                        if (active) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(activePlayer) {
                        val player = activePlayer ?: return@LaunchedEffect
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                    LaunchedEffect(pendingExternalAction, activePlayer) {
                        when (pendingExternalAction) {
                            ACTION_SEARCH -> {
                                openSearchImmediately = true
                                onActiveChange(true)
                            }

                            ACTION_LIBRARY -> {
                                onActiveChange(false)
                                navigateToScreen(navController, Screens.Library)
                            }

                            ACTION_EXPLORE -> {
                                onActiveChange(false)
                                navigateToScreen(navController, Screens.Explore)
                            }

                            ACTION_WIDGET_OPEN_QUEUE -> {
                                showLyrics = false
                                if (activePlayer?.currentMediaItem != null) {
                                    playerBottomSheetState.expandSoft()
                                }
                            }

                            ACTION_WIDGET_OPEN_LYRICS -> {
                                showLyrics = true
                                if (activePlayer?.currentMediaItem != null) {
                                    playerBottomSheetState.expandSoft()
                                }
                            }
                        }
                        pendingExternalAction = null
                    }
                    DisposableEffect(activePlayer, playerBottomSheetState) {
                        val player = activePlayer ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_IDLE && player.mediaItemCount == 0) {
                                        playerBottomSheetState.dismiss()
                                    }
                                }
                                // Detectar cuando los elementos multimedia son eliminados (parte del proceso de cierre)
                                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                                        if (mediaItem != null && playerBottomSheetState.isDismissed) {
                                            playerBottomSheetState.collapseSoft()
                                        } else if (mediaItem == null && player.mediaItemCount == 0) {
                                            playerBottomSheetState.dismiss()
                                        }
                                    }
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar =
                            !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleDeepLinkIntent(pendingIntent!!, navController)
                            pendingIntent = null
                        } else {
                            handleDeepLinkIntent(intent, navController)
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            handleDeepLinkIntent(intent, navController)
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Explore.route -> R.string.explore
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalDownloadLibraryRepository provides downloadLibraryRepository,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                    ) {
                        Scaffold(
                            topBar = {
                                if (shouldShowTopBar) {
                                    TopAppBar(
                                        title = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Image(
                                                    painter = painterResource(R.drawable.ic_anitail),
                                                    contentDescription = "App Logo",
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.app_name),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = { navController.navigate("history") }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.history),
                                                    contentDescription = stringResource(R.string.history)
                                                )
                                            }
                                            IconButton(onClick = { navController.navigate("stats") }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.stats),
                                                    contentDescription = stringResource(R.string.stats)
                                                )
                                            }
                                            IconButton(onClick = { onActiveChange(true) }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.search),
                                                    contentDescription = stringResource(R.string.search)
                                                )
                                            }

                                            // --- Avatar logic start ---
                                            // ViewModel and preferences
                                            val homeViewModel: com.anitail.music.viewmodels.HomeViewModel =
                                                hiltViewModel()
                                            val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
                                            val discordAvatarUrl by homeViewModel.discordAvatarUrl.collectAsState()
                                            val (preferredAvatarSource) = rememberEnumPreference(
                                                com.anitail.music.constants.PreferredAvatarSourceKey,
                                                defaultValue = com.anitail.music.constants.AvatarSource.YOUTUBE
                                            )

                                            // Determine which avatar to use
                                            val avatarUrl = when (preferredAvatarSource) {
                                                com.anitail.music.constants.AvatarSource.YOUTUBE -> accountImageUrl
                                                com.anitail.music.constants.AvatarSource.DISCORD -> discordAvatarUrl
                                            }

                                            IconButton(onClick = { navController.navigate("settings") }) {
                                                BadgedBox(badge = {
                                                    if (latestVersionName != BuildConfig.VERSION_NAME) {
                                                        Badge()
                                                    }
                                                }) {
                                                    if (!avatarUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = avatarUrl,
                                                            contentDescription = stringResource(R.string.settings),
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(CircleShape),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                            placeholder = painterResource(R.drawable.settings),
                                                            error = painterResource(R.drawable.settings)
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(R.drawable.settings),
                                                            contentDescription = stringResource(R.string.settings),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            // --- Avatar logic end ---
                                        },
                                        scrollBehavior =
                                        searchBarScrollBehavior,
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                            scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                            titleContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                                            actionIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            navigationIconContentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                if (active || navBackStackEntry?.destination?.route?.startsWith(
                                        "search/"
                                    ) == true
                                ) {
                                    TopSearch(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        active = active,
                                        onActiveChange = onActiveChange,
                                        placeholder = {
                                            Text(
                                                text = stringResource(
                                                    when (searchSource) {
                                                        SearchSource.LOCAL -> R.string.search_library
                                                        SearchSource.ONLINE -> R.string.search_yt_music
                                                    }
                                                ),
                                            )
                                        },
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        active -> onActiveChange(false)
                                                        !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> {
                                                            navController.navigateUp()
                                                        }

                                                        else -> onActiveChange(true)
                                                    }
                                                },
                                                onLongClick = {
                                                    when {
                                                        active -> {}
                                                        !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } -> {
                                                            navController.backToMain()
                                                        }

                                                        else -> {}
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painterResource(
                                                        if (active ||
                                                            !navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                                                        ) {
                                                            R.drawable.arrow_back
                                                        } else {
                                                            R.drawable.search
                                                        },
                                                    ),
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Row {
                                                if (active) {
                                                    if (query.text.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = {
                                                                onQueryChange(
                                                                    TextFieldValue(
                                                                        ""
                                                                    )
                                                                )
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.close),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            searchSource =
                                                                if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(
                                                                when (searchSource) {
                                                                    SearchSource.LOCAL -> R.drawable.library_music
                                                                    SearchSource.ONLINE -> R.drawable.language
                                                                },
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        modifier =
                                        Modifier
                                            .focusRequester(searchBarFocusRequester)
                                            .align(Alignment.TopCenter),
                                        focusRequester = searchBarFocusRequester,
                                        colors = if (pureBlack && active) {
                                            SearchBarDefaults.colors(
                                                containerColor = Color.Black,
                                                dividerColor = Color.DarkGray,
                                                inputFieldColors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.Gray,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    cursorColor = Color.White,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                )
                                            )
                                        } else {
                                            SearchBarDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            )
                                        }
                                    ) {
                                        Crossfade(
                                            targetState = searchSource,
                                            label = "",
                                            modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight + (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) else 0.dp)
                                                .navigationBarsPadding(),
                                        ) { searchSource ->
                                            when (searchSource) {
                                                SearchSource.LOCAL ->
                                                    LocalSearchScreen(
                                                        query = query.text,
                                                        navController = navController,
                                                        onDismiss = { onActiveChange(false) },
                                                        pureBlack = pureBlack,
                                                    )

                                                SearchSource.ONLINE ->
                                                    OnlineSearchScreen(
                                                        query = query.text,
                                                        onQueryChange = onQueryChange,
                                                        navController = navController,
                                                        onSearch = {
                                                            navController.navigate(
                                                                "search/${
                                                                    URLEncoder.encode(
                                                                        it,
                                                                        "UTF-8"
                                                                    )
                                                                }"
                                                            )
                                                            if (dataStore[PauseSearchHistoryKey] != true) {
                                                                database.query {
                                                                    insert(SearchHistory(query = it))
                                                                }
                                                            }
                                                        },
                                                        onDismiss = { onActiveChange(false) },
                                                        pureBlack = pureBlack
                                                    )
                                            }
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                Box {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack
                                    )
                                    NavigationBar(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInset + getNavPadding())
                                            .offset {
                                                if (navigationBarHeight == 0.dp) {
                                                    IntOffset(
                                                        x = 0,
                                                        y = (bottomInset + NavigationBarHeight).roundToPx(),
                                                    )
                                                } else {
                                                    val slideOffset =
                                                        (bottomInset + NavigationBarHeight) *
                                                                playerBottomSheetState.progress.coerceIn(
                                                                    0f,
                                                                    1f,
                                                                )
                                                    val hideOffset =
                                                        (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                    IntOffset(
                                                        x = 0,
                                                        y = (slideOffset + hideOffset).roundToPx(),
                                                    )
                                                }
                                            },
                                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        var lastTapTime by remember { mutableLongStateOf(0L) }
                                        var lastTappedIcon by remember { mutableStateOf<Int?>(null) }
                                        var navigateToExplore by remember { mutableStateOf(false) }
                                        navigationItems.fastForEach { screen ->
                                            val isSelected =
                                                navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                            NavigationBarItem(
                                                modifier = Modifier.tvFocusable(
                                                    shape = RoundedCornerShape(12.dp),
                                                ),
                                                selected = isSelected,
                                                icon = {
                                                    Icon(
                                                        painter = painterResource(
                                                            id = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                                                        ),
                                                        contentDescription = null,
                                                    )
                                                },
                                                label = {
                                                    if (!slimNav) {
                                                        Text(
                                                            text = stringResource(screen.titleId),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    val currentTapTime = System.currentTimeMillis()
                                                    val timeSinceLastTap =
                                                        currentTapTime - lastTapTime
                                                    val isDoubleTap =
                                                        screen.titleId == R.string.explore &&
                                                                lastTappedIcon == R.string.explore &&
                                                                timeSinceLastTap < 300

                                                    lastTapTime = currentTapTime
                                                    lastTappedIcon = screen.titleId

                                                    if (screen.titleId == R.string.explore) {
                                                        if (isDoubleTap) {
                                                            onActiveChange(true)
                                                            navigateToExplore = false
                                                        } else {
                                                            navigateToExplore = true
                                                            coroutineScope.launch {
                                                                delay(100)
                                                                if (navigateToExplore) {
                                                                        if (isSelected) {
                                                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                                                "scrollToTop",
                                                                                true
                                                                            )
                                                                        } else {
                                                                            navigateToScreen(navController, screen)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                        if (isSelected) {
                                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                                "scrollToTop",
                                                                true
                                                            )
                                                            coroutineScope.launch {
                                                                searchBarScrollBehavior.state.resetHeightOffset()
                                                            }
                                                        } else {
                                                            navigateToScreen(navController, screen)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                                    val insetBg = if (playerBottomSheetState.progress > 0f) Color.Transparent else baseBg

                                    Box(
                                        modifier = Modifier
                                            .background(insetBg)
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                        ) {
                            var transitionDirection =
                                AnimatedContentTransitionScope.SlideDirection.Left

                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                if (navigationItems.fastAny { it.route == previousTab }) {
                                    val curIndex = navigationItems.indexOf(
                                        navigationItems.fastFirstOrNull {
                                            it.route == navBackStackEntry?.destination?.route
                                        }
                                    )

                                    val prevIndex = navigationItems.indexOf(
                                        navigationItems.fastFirstOrNull {
                                            it.route == previousTab
                                        }
                                    )

                                    if (prevIndex > curIndex)
                                        AnimatedContentTransitionScope.SlideDirection.Right.also {
                                            transitionDirection = it
                                        }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                    NavigationTab.HOME -> Screens.Home
                                    NavigationTab.EXPLORE -> Screens.Explore
                                    NavigationTab.LIBRARY -> Screens.Library
                                }.route,
                                enterTransition = {
                                    if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                        fadeIn(tween(250))
                                    } else {
                                        fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                    }
                                },
                                exitTransition = {
                                    if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                        fadeOut(tween(200))
                                    } else {
                                        fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                    }
                                },
                                popEnterTransition = {
                                    if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                        fadeIn(tween(250))
                                    } else {
                                        fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                    }
                                },
                                popExitTransition = {
                                    if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                        fadeOut(tween(200))
                                    } else {
                                        fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                                    }
                                },
                                modifier = Modifier.nestedScroll(
                                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                        navBackStackEntry?.destination?.route?.startsWith("search/") == true
                                    ) {
                                        searchBarScrollBehavior.nestedScrollConnection
                                    } else {
                                        topAppBarScrollBehavior.nestedScrollConnection
                                    }
                                )
                            ) {
                                navigationBuilder(
                                    navController,
                                    topAppBarScrollBehavior,
                                    latestVersionName
                                )
                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            openSearchImmediately = false
                        }
                    }
                    }
                }
            }
        }
    }

    private fun navigateToScreen(
        navController: NavHostController,
        screen: Screens
    ) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        if (uri.scheme.equals("anitail", ignoreCase = true)) {
            val deepLinkQuery = uri.getQueryParameter("q")
                ?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("query")?.takeIf { it.isNotBlank() }
            if (uri.host.equals("search", ignoreCase = true) && !deepLinkQuery.isNullOrBlank()) {
                navController.navigate("search/${URLEncoder.encode(deepLinkQuery, "UTF-8")}")
            }
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                navController.navigate("album/$browseId")
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                videoId?.let {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            YouTube.queue(listOf(it), playlistId)
                        }.onSuccess {
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    WatchEndpoint(
                                        videoId = it.firstOrNull()?.id,
                                        playlistId = playlistId
                                    ),
                                    it.firstOrNull()?.toMediaMetadata()
                                )
                            )
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    private fun applyDynamicLauncherIcon(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val dynamicComponent = ComponentName(this, "$packageName$DYNAMIC_LAUNCHER_ALIAS_SUFFIX")
        val defaultComponent = ComponentName(this, "$packageName$DEFAULT_LAUNCHER_ALIAS_SUFFIX")
        val dynamicState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val defaultState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        runCatching {
            packageManager.setComponentEnabledSetting(
                dynamicComponent,
                dynamicState,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                defaultComponent,
                defaultState,
                PackageManager.DONT_KILL_APP
            )
        }.onFailure {
            Timber.w(it, "Failed to apply launcher icon mode")
        }
    }

    private fun applyHighRefreshRate(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return

        val preferredModeId = if (enabled) {
            activeDisplay.supportedModes.maxByOrNull { it.refreshRate }?.modeId ?: 0
        } else {
            0
        }

        val params = window.attributes
        if (params.preferredDisplayModeId != preferredModeId) {
            params.preferredDisplayModeId = preferredModeId
            window.attributes = params
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.anitail.music.action.SEARCH"
        const val ACTION_EXPLORE = "com.anitail.music.action.EXPLORE"
        const val ACTION_LIBRARY = "com.anitail.music.action.LIBRARY"
        const val ACTION_WIDGET_OPEN_QUEUE = "com.anitail.music.action.WIDGET_OPEN_QUEUE"
        const val ACTION_WIDGET_OPEN_LYRICS = "com.anitail.music.action.WIDGET_OPEN_LYRICS"
        private const val DEFAULT_LAUNCHER_ALIAS_SUFFIX = ".MainActivityLauncherDefault"
        private const val DYNAMIC_LAUNCHER_ALIAS_SUFFIX = ".MainActivityLauncherDynamic"
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private val storagePermissionCallback = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Timber.d("Storage permissions granted")
            lifecycleScope.launch {
                AutoBackupWorker.schedule(this@MainActivity)
            }
        } else {
            Timber.w("Storage permissions denied")
            // Show a toast explaining the need for storage permissions
            Toast.makeText(
                this,
                getString(R.string.storage_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val managedStoragePermissionCallback = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Timber.d("Managed storage permission granted")
            lifecycleScope.launch {
                AutoBackupWorker.schedule(this@MainActivity)
            }
        } else {
            Timber.w("Managed storage permission denied")
            Toast.makeText(
                this,
                getString(R.string.storage_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAndRequestStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE permission
            if (Environment.isExternalStorageManager()) {
                true
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = "package:$packageName".toUri()
                    managedStoragePermissionCallback.launch(intent)
                } catch (e: Exception) {
                    Timber.e(e, "Unable to request MANAGE_EXTERNAL_STORAGE permission")
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    managedStoragePermissionCallback.launch(intent)
                }
                false
            }
        } else {
            // Android 10 or lower use traditional runtime permissions
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                true
            } else {
                storagePermissionCallback.launch(permissions)
                false
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionCallback.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private val notificationPermissionCallback = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Timber.w("Notification permission denied")
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalDownloadLibraryRepository =
    staticCompositionLocalOf<DownloadLibraryRepository> { error("No DownloadLibraryRepository provided") }
