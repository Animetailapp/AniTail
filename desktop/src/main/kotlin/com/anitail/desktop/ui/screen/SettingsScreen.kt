package com.anitail.desktop.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.AccountInfo
import com.anitail.desktop.auth.DesktopAccountTokenParser
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.auth.DesktopDiscordService
import com.anitail.desktop.download.DesktopDownloadService
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.AvatarSourcePreference
import com.anitail.desktop.storage.DarkModePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.LyricsAnimationStylePreference
import com.anitail.desktop.storage.LyricsPositionPreference
import com.anitail.desktop.storage.NavigationTabPreference
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
import com.anitail.desktop.storage.PreferredLyricsProvider
import com.anitail.desktop.storage.QuickPicks
import com.anitail.desktop.storage.SliderStyle
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.NavigationTitle
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.i18n.CountryCodeToName
import com.anitail.desktop.i18n.LanguageCodeToName
import com.anitail.desktop.i18n.SYSTEM_DEFAULT
import com.anitail.desktop.i18n.appLanguageOptions
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.player.PlayerState
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.LibraryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Settings navigation destinations
 */
enum class SettingsDestination {
    MAIN,
    ACCOUNT,
    LASTFM,
    DISCORD,
    SPOTIFY_IMPORT,
    APPEARANCE,
    PLAYER,
    CONTENT,
    CONTENT_ROMANIZATION,
    PRIVACY,
    STORAGE,
    ABOUT,
}

/**
 * Main Settings Screen with navigation to sub-settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: DesktopPreferences = DesktopPreferences.getInstance(),
    downloadService: DesktopDownloadService,
    authService: DesktopAuthService,
    authCredentials: AuthCredentials?,
    accountInfo: AccountInfo? = null,
    playerState: PlayerState? = null,
    onOpenLogin: () -> Unit,
    onAuthChanged: (AuthCredentials?) -> Unit,
) {
    var currentDestination by remember { mutableStateOf(SettingsDestination.MAIN) }
    val preferredAvatarSource by preferences.preferredAvatarSource.collectAsState()
    val discordUsername by preferences.discordUsername.collectAsState()
    val discordAvatarUrl by preferences.discordAvatarUrl.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        when (currentDestination) {
            SettingsDestination.MAIN -> SettingsMainScreen(
                onNavigate = { currentDestination = it },
                preferredAvatarSource = preferredAvatarSource,
                googleAccountName = accountInfo?.name ?: authCredentials?.accountName,
                googleAvatarUrl = accountInfo?.thumbnailUrl,
                discordUsername = discordUsername,
                discordAvatarUrl = discordAvatarUrl,
            )

            SettingsDestination.ACCOUNT -> AccountSettingsScreen(
                preferences = preferences,
                authService = authService,
                authCredentials = authCredentials,
                onBack = { currentDestination = SettingsDestination.MAIN },
                onOpenLogin = onOpenLogin,
                onOpenSpotifyImport = { currentDestination = SettingsDestination.SPOTIFY_IMPORT },
                onOpenDiscordSettings = { currentDestination = SettingsDestination.DISCORD },
                onAuthChanged = onAuthChanged,
            )

            SettingsDestination.LASTFM -> LastFmSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.DISCORD -> DiscordSettingsScreen(
                preferences = preferences,
                previewItem = playerState?.currentItem,
                previewPositionMs = playerState?.position ?: 0L,
                isPreviewPlaying = playerState?.isPlaying == true,
                onBack = { currentDestination = SettingsDestination.ACCOUNT },
            )

            SettingsDestination.SPOTIFY_IMPORT -> SpotifyImportSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.ACCOUNT },
            )

            SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.PLAYER -> PlayerSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.CONTENT -> ContentSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
                onOpenRomanization = { currentDestination = SettingsDestination.CONTENT_ROMANIZATION },
            )

            SettingsDestination.CONTENT_ROMANIZATION -> RomanizationSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.CONTENT },
            )

            SettingsDestination.PRIVACY -> PrivacySettingsScreen(
                preferences = preferences,
                authService = authService,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.STORAGE -> StorageSettingsScreen(
                preferences = preferences,
                downloadService = downloadService,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.ABOUT -> AboutScreen(
                onBack = { currentDestination = SettingsDestination.MAIN },
            )
        }
    }
}

@Composable
internal fun LegacySettingsMainScreen(
    onNavigate: (SettingsDestination) -> Unit,
) {
    val settingsCategories = listOf(
        SettingsCategory(
            title = stringResource("account"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.account(),
            destination = SettingsDestination.ACCOUNT,
        ),
        SettingsCategory(
            title = stringResource("lastfm_settings"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.musicNote(),
            destination = SettingsDestination.LASTFM,
        ),
        SettingsCategory(
            title = stringResource("appearance"),
            subtitle = stringResource("category_interface"),
            icon = IconAssets.palette(),
            destination = SettingsDestination.APPEARANCE,
        ),
        SettingsCategory(
            title = stringResource("player_and_audio"),
            subtitle = stringResource("category_player"),
            icon = IconAssets.play(),
            destination = SettingsDestination.PLAYER,
        ),
        SettingsCategory(
            title = stringResource("content"),
            subtitle = stringResource("category_content"),
            icon = IconAssets.language(),
            destination = SettingsDestination.CONTENT,
        ),
        SettingsCategory(
            title = stringResource("privacy"),
            subtitle = stringResource("category_content"),
            icon = IconAssets.security(),
            destination = SettingsDestination.PRIVACY,
        ),
        SettingsCategory(
            title = stringResource("storage"),
            subtitle = stringResource("category_system"),
            icon = IconAssets.storage(),
            destination = SettingsDestination.STORAGE,
        ),
        SettingsCategory(
            title = stringResource("about"),
            subtitle = stringResource("category_system"),
            icon = IconAssets.info(),
            destination = SettingsDestination.ABOUT,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            NavigationTitle(title = stringResource("settings"))
        }

        items(settingsCategories) { category ->
            SettingsCategoryItem(
                category = category,
                onClick = { onNavigate(category.destination) },
            )
        }
    }
}

@Composable
internal fun LegacyAccountSettingsScreen(
    preferences: DesktopPreferences,
    authService: DesktopAuthService,
    authCredentials: AuthCredentials?,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onAuthChanged: (AuthCredentials?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useLoginForBrowse by preferences.useLoginForBrowse.collectAsState()
    val ytmSync by preferences.ytmSync.collectAsState()
    val discordToken by preferences.discordToken.collectAsState()
    val discordUsername by preferences.discordUsername.collectAsState()
    val preferredAvatarSource by preferences.preferredAvatarSource.collectAsState()
    val hasCookie = authCredentials?.cookie?.isNotBlank() == true
    val hasDataSyncId = authCredentials?.dataSyncId?.isNotBlank() == true
    val isLoggedIn = hasCookie
    var accountInfo by remember { mutableStateOf<com.anitail.desktop.auth.AccountInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showDiscordEditor by remember { mutableStateOf(false) }
    var showAvatarSourceDialog by remember { mutableStateOf(false) }
    var discordSyncError by remember { mutableStateOf<String?>(null) }
    var isRefreshingDiscordProfile by remember { mutableStateOf(false) }
    val loginEnabled = false
    val loginDisabled = !loginEnabled && !isLoggedIn
    val loginEntryAlpha = if (loginDisabled) 0.5f else 1f
    val canUseDiscordAvatar = isLoggedIn || discordToken.isNotBlank()
    val discordStatusErrorText = stringResource("discord_status_error")
    val openUrl: (String) -> Unit = { url ->
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url.trim())) }
    }

    fun refreshDiscordProfile(token: String) {
        val sanitizedToken = token.trim()
        if (sanitizedToken.isBlank()) {
            preferences.setDiscordUsername("")
            preferences.setDiscordAvatarUrl("")
            discordSyncError = null
            return
        }

        scope.launch {
            isRefreshingDiscordProfile = true
            val profile = withContext(Dispatchers.IO) {
                DesktopDiscordService.fetchProfile(sanitizedToken)
            }
            if (profile != null) {
                preferences.setDiscordUsername(profile.username)
                preferences.setDiscordAvatarUrl(profile.avatarUrl.orEmpty())
                discordSyncError = null
            } else {
                discordSyncError = discordStatusErrorText
            }
            isRefreshingDiscordProfile = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            isRefreshing = true
            accountInfo = authService.refreshAccountInfo()
            onAuthChanged(authService.credentials)
            isRefreshing = false
        } else {
            accountInfo = null
            showToken = false
        }
    }

    if (showTokenEditor) {
        var tokenText by remember(authCredentials, showTokenEditor) {
            mutableStateOf(DesktopAccountTokenParser.buildTokenText(authCredentials ?: AuthCredentials()))
        }
        val parsedToken = remember(tokenText) { DesktopAccountTokenParser.parse(tokenText) }
        val cookieValue = parsedToken.cookie.orEmpty()
        val hasAcceptedCookie = cookieValue.isBlank() ||
            cookieValue.contains("SAPISID") ||
            cookieValue.contains("__Secure-1PAPISID") ||
            cookieValue.contains("__Secure-3PAPISID")
        val canSave = parsedToken.hasAnyValue() && hasAcceptedCookie

        AlertDialog(
            onDismissRequest = { showTokenEditor = false },
            title = { Text(stringResource("advanced_login")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 20,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource("token_adv_login_description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        val base = authCredentials ?: AuthCredentials()
                        val updated = base.copy(
                            cookie = parsedToken.cookie ?: base.cookie,
                            visitorData = parsedToken.visitorData ?: base.visitorData,
                            dataSyncId = parsedToken.dataSyncId ?: base.dataSyncId,
                            accountName = parsedToken.accountName ?: base.accountName,
                            accountEmail = parsedToken.accountEmail ?: base.accountEmail,
                            channelHandle = parsedToken.channelHandle ?: base.channelHandle,
                        )
                        scope.launch {
                            authService.saveCredentials(updated)
                            accountInfo = authService.refreshAccountInfo()
                            onAuthChanged(authService.credentials)
                            showTokenEditor = false
                        }
                    },
                ) { Text(stringResource("save")) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenEditor = false }) { Text(stringResource("cancel")) }
            },
        )
    }

    if (showDiscordEditor) {
        var discordTokenInput by remember(discordToken, showDiscordEditor) { mutableStateOf(discordToken) }
        AlertDialog(
            onDismissRequest = { showDiscordEditor = false },
            title = { Text(stringResource("discord_integration")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discordTokenInput,
                        onValueChange = { discordTokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Discord token") },
                    )
                    Text(
                        text = stringResource("discord_information"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (discordSyncError != null) {
                        Text(
                            text = discordSyncError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (discordUsername.isNotBlank()) {
                        Text(
                            text = discordUsername,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = { openUrl("https://discord.com/channels/@me") }) {
                        Text(stringResource("open_in_browser"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isRefreshingDiscordProfile,
                    onClick = {
                        val token = discordTokenInput.trim()
                        preferences.setDiscordToken(token)
                        refreshDiscordProfile(token)
                        showDiscordEditor = false
                    },
                ) { Text(stringResource("save")) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscordEditor = false }) { Text(stringResource("cancel")) }
            },
        )
    }

    if (showAvatarSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarSourceDialog = false },
            title = { Text(stringResource("avatar_source")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AvatarSourcePreference.entries.forEach { source ->
                        val selected = source == preferredAvatarSource
                        val label = when (source) {
                            AvatarSourcePreference.YOUTUBE -> stringResource("avatar_source_youtube")
                            AvatarSourcePreference.DISCORD -> stringResource("avatar_source_discord")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    preferences.setPreferredAvatarSource(source)
                                    showAvatarSourceDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    preferences.setPreferredAvatarSource(source)
                                    showAvatarSourceDialog = false
                                },
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarSourceDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }

    SettingsSubScreen(
        title = stringResource("account"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("google"))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = loginEnabled && !isLoggedIn) {
                    if (loginEnabled) onOpenLogin()
                },
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(loginEntryAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoggedIn && accountInfo?.thumbnailUrl != null) {
                    RemoteImage(
                        url = accountInfo?.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.account(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoggedIn) {
                            accountInfo?.name
                                ?: authCredentials?.accountName
                                ?: stringResource("account")
                        } else {
                            stringResource("login")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val subtitle = if (isLoggedIn) {
                        accountInfo?.email
                            ?: authCredentials?.accountEmail
                            ?: authCredentials?.channelHandle
                    } else if (!loginEnabled) {
                        if (hasDataSyncId && !hasCookie) {
                            stringResource("login_requires_cookie")
                        } else {
                            stringResource("login_not_available_desktop")
                        }
                    } else null
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLoggedIn) {
                    TextButton(onClick = {
                        scope.launch {
                            authService.logout()
                            accountInfo = null
                            onAuthChanged(null)
                        }
                    }) {
                        Text(stringResource("logout"))
                    }
                } else if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }

        SettingsButton(
            title = if (!isLoggedIn) {
                stringResource("advanced_login")
            } else {
                if (showToken) stringResource("token_shown") else stringResource("token_hidden")
            },
            subtitle = stringResource("token_adv_login_description"),
            onClick = {
                if (!isLoggedIn) {
                    showTokenEditor = true
                } else if (!showToken) {
                    showToken = true
                } else {
                    showTokenEditor = true
                }
            },
            icon = IconAssets.lock(),
        )

        if (isLoggedIn) {
            SettingsSwitch(
                title = stringResource("use_login_for_browse"),
                subtitle = stringResource("use_login_for_browse_desc"),
                checked = useLoginForBrowse,
                onCheckedChange = {
                    preferences.setUseLoginForBrowse(it)
                    com.anitail.innertube.YouTube.useLoginForBrowse = it
                },
            )

            SettingsSwitch(
                title = stringResource("ytm_sync"),
                subtitle = "",
                checked = ytmSync,
                onCheckedChange = { preferences.setYtmSync(it) },
            )
        }

        AndroidPreferenceGroupTitle(title = stringResource("title_spotify"))
        AndroidPreferenceEntry(
            title = stringResource("import_from_spotify"),
            icon = IconAssets.spotify(),
            onClick = { openUrl("https://developer.spotify.com/dashboard/") },
        )

        AndroidPreferenceGroupTitle(title = stringResource("discord"))
        AndroidPreferenceEntry(
            title = stringResource("discord_integration"),
            icon = IconAssets.discord(),
            onClick = { showDiscordEditor = true },
        )

        AndroidPreferenceGroupTitle(title = stringResource("avatar"))
        AndroidPreferenceEntry(
            title = stringResource("avatar_source"),
            subtitle = when (preferredAvatarSource) {
                AvatarSourcePreference.YOUTUBE -> stringResource("avatar_source_youtube")
                AvatarSourcePreference.DISCORD -> stringResource("avatar_source_discord")
            },
            icon = IconAssets.person(),
            onClick = {
                if (canUseDiscordAvatar) {
                    showAvatarSourceDialog = true
                }
            },
            enabled = canUseDiscordAvatar,
        )
    }
}

@Composable
internal fun SettingsCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = IconAssets.chevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// === Sub-screens ===

@Composable
internal fun LegacyAppearanceSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val densityScale by preferences.densityScale.collectAsState()
    val customDensityValue by preferences.customDensityValue.collectAsState()
    val defaultOpenTab by preferences.defaultOpenTab.collectAsState()
    val defaultLibChip by preferences.defaultLibChip.collectAsState()
    val slimNavBar by preferences.slimNavBar.collectAsState()
    val swipeToSong by preferences.swipeToSong.collectAsState()
    val swipeThumbnail by preferences.swipeThumbnail.collectAsState()
    val swipeSensitivity by preferences.swipeSensitivity.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()
    val lyricsTextPosition by preferences.lyricsTextPosition.collectAsState()
    val lyricsClick by preferences.lyricsClick.collectAsState()
    val lyricsScroll by preferences.lyricsScroll.collectAsState()
    val lyricsFontSize by preferences.lyricsFontSize.collectAsState()
    val lyricsCustomFontPath by preferences.lyricsCustomFontPath.collectAsState()
    val lyricsSmoothScroll by preferences.lyricsSmoothScroll.collectAsState()
    val lyricsAnimationStyle by preferences.lyricsAnimationStyle.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()
    val showLikedPlaylist by preferences.showLikedPlaylist.collectAsState()
    val showDownloadedPlaylist by preferences.showDownloadedPlaylist.collectAsState()
    val showTopPlaylist by preferences.showTopPlaylist.collectAsState()
    val showCachedPlaylist by preferences.showCachedPlaylist.collectAsState()
    var customFontPathInput by remember(lyricsCustomFontPath) { mutableStateOf(lyricsCustomFontPath) }

    SettingsSubScreen(
        title = stringResource("appearance"),
        onBack = onBack,
    ) {
        // Dark Mode
        SettingsDropdown(
            title = stringResource("dark_theme"),
            subtitle = when (darkMode) {
                DarkModePreference.ON -> stringResource("dark_theme_on")
                DarkModePreference.OFF -> stringResource("dark_theme_off")
                DarkModePreference.AUTO -> stringResource("dark_theme_follow_system")
                DarkModePreference.TIME_BASED -> stringResource("dark_theme_time_based")
            },
            options = DarkModePreference.entries.map {
                when (it) {
                    DarkModePreference.ON -> stringResource("dark_theme_on")
                    DarkModePreference.OFF -> stringResource("dark_theme_off")
                    DarkModePreference.AUTO -> stringResource("dark_theme_follow_system")
                    DarkModePreference.TIME_BASED -> stringResource("dark_theme_time_based")
                }
            },
            selectedIndex = DarkModePreference.entries.indexOf(darkMode),
            onSelect = { index ->
                preferences.setDarkMode(DarkModePreference.entries[index])
            },
        )

        // Pure Black
        SettingsSwitch(
            title = stringResource("pure_black"),
            subtitle = stringResource("pure_black_desc"),
            checked = pureBlack,
            onCheckedChange = { preferences.setPureBlack(it) },
        )

        // Dynamic Color
        SettingsSwitch(
            title = stringResource("enable_dynamic_theme"),
            subtitle = stringResource("enable_dynamic_theme_desc"),
            checked = dynamicColor,
            onCheckedChange = { preferences.setDynamicColor(it) },
        )

        val densityPresetValues = listOf(1.0f, 0.75f, 0.65f, 0.55f)
        val densityPresetLabels = listOf("100%", "75%", "65%", "55%")
        val customDensityLabel = stringResource("custom_density_title")
        val densitySelectedIndex = densityPresetValues
            .indexOfFirst { abs(it - densityScale) < 0.001f }
            .takeIf { it >= 0 }
            ?: densityPresetValues.size

        SettingsDropdown(
            title = stringResource("display_density_title"),
            subtitle = if (densitySelectedIndex < densityPresetLabels.size) {
                densityPresetLabels[densitySelectedIndex]
            } else {
                "${(densityScale * 100f).toInt()}%"
            },
            options = densityPresetLabels + customDensityLabel,
            selectedIndex = densitySelectedIndex,
            onSelect = { index ->
                if (index < densityPresetValues.size) {
                    preferences.setDensityScale(densityPresetValues[index])
                } else {
                    preferences.setDensityScale(customDensityValue)
                }
            },
        )

        SettingsSlider(
            title = customDensityLabel,
            subtitle = "${(customDensityValue * 100f).toInt()}%",
            value = (customDensityValue * 100f).coerceIn(50f, 120f),
            valueRange = 50f..120f,
            steps = 69,
            onValueChange = { value ->
                val scale = (value / 100f).coerceIn(0.5f, 1.2f)
                preferences.setCustomDensityValue(scale)
                if (densitySelectedIndex == densityPresetValues.size) {
                    preferences.setDensityScale(scale)
                }
            },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        val backgroundStyleOptions = mutableListOf<String>()
        for (style in PlayerBackgroundStyle.entries) {
            backgroundStyleOptions.add(stringResource(style.labelKey))
        }
        val backgroundStyleSubtitle = stringResource(playerBackgroundStyle.labelKey)
        SettingsDropdown(
            title = stringResource("player_background_style"),
            subtitle = backgroundStyleSubtitle,
            options = backgroundStyleOptions,
            selectedIndex = PlayerBackgroundStyle.entries.indexOf(playerBackgroundStyle),
            onSelect = { index ->
                preferences.setPlayerBackgroundStyle(PlayerBackgroundStyle.entries[index])
            },
        )

        val buttonStyleOptions = mutableListOf<String>()
        for (style in PlayerButtonsStyle.entries) {
            buttonStyleOptions.add(stringResource(style.labelKey))
        }
        val buttonStyleSubtitle = stringResource(playerButtonsStyle.labelKey)
        SettingsDropdown(
            title = stringResource("player_buttons_style"),
            subtitle = buttonStyleSubtitle,
            options = buttonStyleOptions,
            selectedIndex = PlayerButtonsStyle.entries.indexOf(playerButtonsStyle),
            onSelect = { index ->
                preferences.setPlayerButtonsStyle(PlayerButtonsStyle.entries[index])
            },
        )

        val sliderStyleOptions = mutableListOf<String>()
        for (style in SliderStyle.entries) {
            sliderStyleOptions.add(stringResource(style.labelKey))
        }
        val sliderStyleSubtitle = stringResource(sliderStyle.labelKey)
        SettingsDropdown(
            title = stringResource("player_slider_style"),
            subtitle = sliderStyleSubtitle,
            options = sliderStyleOptions,
            selectedIndex = SliderStyle.entries.indexOf(sliderStyle),
            onSelect = { index ->
                preferences.setSliderStyle(SliderStyle.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("enable_swipe_thumbnail"),
            subtitle = "",
            checked = swipeThumbnail,
            onCheckedChange = { preferences.setSwipeThumbnail(it) },
        )

        if (swipeThumbnail) {
            SettingsSlider(
                title = stringResource("swipe_sensitivity"),
                subtitle = "${(swipeSensitivity * 100f).toInt()}%",
                value = (swipeSensitivity * 100f).coerceIn(0f, 100f),
                valueRange = 0f..100f,
                steps = 99,
                onValueChange = { value ->
                    preferences.setSwipeSensitivity((value / 100f).coerceIn(0f, 1f))
                },
            )
        }

        val lyricsPositionOptions = LyricsPositionPreference.entries.map { position ->
            when (position) {
                LyricsPositionPreference.LEFT -> stringResource("left")
                LyricsPositionPreference.CENTER -> stringResource("center")
                LyricsPositionPreference.RIGHT -> stringResource("right")
            }
        }
        SettingsDropdown(
            title = stringResource("lyrics_text_position"),
            subtitle = when (lyricsTextPosition) {
                LyricsPositionPreference.LEFT -> stringResource("left")
                LyricsPositionPreference.CENTER -> stringResource("center")
                LyricsPositionPreference.RIGHT -> stringResource("right")
            },
            options = lyricsPositionOptions,
            selectedIndex = LyricsPositionPreference.entries.indexOf(lyricsTextPosition),
            onSelect = { index ->
                preferences.setLyricsTextPosition(LyricsPositionPreference.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("lyrics_click_change"),
            subtitle = "",
            checked = lyricsClick,
            onCheckedChange = { preferences.setLyricsClick(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_auto_scroll"),
            subtitle = "",
            checked = lyricsScroll,
            onCheckedChange = { preferences.setLyricsScroll(it) },
        )

        SettingsSwitch(
            title = stringResource("smooth_lyrics_animation"),
            subtitle = "",
            checked = lyricsSmoothScroll,
            onCheckedChange = { preferences.setLyricsSmoothScroll(it) },
        )

        val animationStyleOptions = LyricsAnimationStylePreference.entries.map { style ->
            stringResource(style.labelKey)
        }
        SettingsDropdown(
            title = stringResource("lyrics_animation_style"),
            subtitle = stringResource(lyricsAnimationStyle.labelKey),
            options = animationStyleOptions,
            selectedIndex = LyricsAnimationStylePreference.entries.indexOf(lyricsAnimationStyle),
            onSelect = { index ->
                preferences.setLyricsAnimationStyle(LyricsAnimationStylePreference.entries[index])
            },
        )

        SettingsSlider(
            title = stringResource("lyrics_font_size"),
            subtitle = "${lyricsFontSize.toInt()}sp",
            value = lyricsFontSize.coerceIn(12f, 36f),
            valueRange = 12f..36f,
            steps = 23,
            onValueChange = { preferences.setLyricsFontSize(it) },
        )

        SettingsTextField(
            title = stringResource("lyrics_custom_font"),
            subtitle = if (lyricsCustomFontPath.isBlank()) {
                stringResource("use_system_font")
            } else {
                lyricsCustomFontPath
            },
            value = customFontPathInput,
            onValueChange = { customFontPathInput = it },
            onSave = { preferences.setLyricsCustomFontPath(customFontPathInput.trim()) },
            placeholder = "",
        )

        SettingsSectionTitle(title = stringResource("misc"))

        val defaultTabOptions = NavigationTabPreference.entries.map { tab ->
            when (tab) {
                NavigationTabPreference.HOME -> stringResource("home")
                NavigationTabPreference.EXPLORE -> stringResource("explore")
                NavigationTabPreference.LIBRARY -> stringResource("filter_library")
            }
        }
        SettingsDropdown(
            title = stringResource("default_open_tab"),
            subtitle = when (defaultOpenTab) {
                NavigationTabPreference.HOME -> stringResource("home")
                NavigationTabPreference.EXPLORE -> stringResource("explore")
                NavigationTabPreference.LIBRARY -> stringResource("filter_library")
            },
            options = defaultTabOptions,
            selectedIndex = NavigationTabPreference.entries.indexOf(defaultOpenTab),
            onSelect = { index ->
                preferences.setDefaultOpenTab(NavigationTabPreference.entries[index])
            },
        )

        val defaultChipOptions = listOf(
            LibraryFilter.LIBRARY,
            LibraryFilter.PLAYLISTS,
            LibraryFilter.SONGS,
            LibraryFilter.ALBUMS,
            LibraryFilter.ARTISTS,
        )
        val defaultChipLabels = defaultChipOptions.map { filter ->
            when (filter) {
                LibraryFilter.SONGS -> stringResource("songs")
                LibraryFilter.ARTISTS -> stringResource("artists")
                LibraryFilter.ALBUMS -> stringResource("albums")
                LibraryFilter.PLAYLISTS -> stringResource("playlists")
                LibraryFilter.LIBRARY -> stringResource("filter_library")
                LibraryFilter.DOWNLOADED -> stringResource("filter_downloaded")
            }
        }
        SettingsDropdown(
            title = stringResource("default_lib_chips"),
            subtitle = run {
                val selected = defaultChipOptions.indexOf(defaultLibChip).coerceAtLeast(0)
                defaultChipLabels[selected]
            },
            options = defaultChipLabels,
            selectedIndex = defaultChipOptions.indexOf(defaultLibChip).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setDefaultLibChip(defaultChipOptions[index])
            },
        )

        SettingsSwitch(
            title = stringResource("swipe_song_to_add"),
            subtitle = "",
            checked = swipeToSong,
            onCheckedChange = { preferences.setSwipeToSong(it) },
        )

        SettingsSwitch(
            title = stringResource("slim_navbar"),
            subtitle = "",
            checked = slimNavBar,
            onCheckedChange = { preferences.setSlimNavBar(it) },
        )

        val gridOptions = GridItemSize.entries.map { itemSize ->
            when (itemSize) {
                GridItemSize.BIG -> stringResource("big")
                GridItemSize.SMALL -> stringResource("small")
            }
        }
        SettingsDropdown(
            title = stringResource("grid_cell_size"),
            subtitle = when (gridItemSize) {
                GridItemSize.BIG -> stringResource("big")
                GridItemSize.SMALL -> stringResource("small")
            },
            options = gridOptions,
            selectedIndex = GridItemSize.entries.indexOf(gridItemSize),
            onSelect = { index ->
                preferences.setGridItemSize(GridItemSize.entries[index])
            },
        )

        SettingsSectionTitle(title = stringResource("auto_playlists"))

        SettingsSwitch(
            title = stringResource("show_liked_playlist"),
            subtitle = "",
            checked = showLikedPlaylist,
            onCheckedChange = { preferences.setShowLikedPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_downloaded_playlist"),
            subtitle = "",
            checked = showDownloadedPlaylist,
            onCheckedChange = { preferences.setShowDownloadedPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_top_playlist"),
            subtitle = "",
            checked = showTopPlaylist,
            onCheckedChange = { preferences.setShowTopPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_cached_playlist"),
            subtitle = "",
            checked = showCachedPlaylist,
            onCheckedChange = { preferences.setShowCachedPlaylist(it) },
        )
    }
}

@Composable
internal fun LegacyPlayerSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val audioQuality by preferences.audioQuality.collectAsState()
    val normalizeAudio by preferences.normalizeAudio.collectAsState()
    val skipSilence by preferences.skipSilence.collectAsState()
    val crossfadeDuration by preferences.crossfadeDuration.collectAsState()
    val historyDuration by preferences.historyDuration.collectAsState()
    val persistentQueue by preferences.persistentQueue.collectAsState()
    val autoStartRadio by preferences.autoStartRadio.collectAsState()
    val showLyrics by preferences.showLyrics.collectAsState()
    val romanizeLyrics by preferences.romanizeLyrics.collectAsState()

    SettingsSubScreen(
        title = stringResource("player_and_audio"),
        onBack = onBack,
    ) {
        val audioQualityOptions = mutableListOf<String>()
        for (quality in AudioQuality.entries) {
            audioQualityOptions.add(stringResource(quality.labelKey))
        }
        val audioQualitySubtitle = stringResource(audioQuality.labelKey)

        // Audio Quality
        SettingsDropdown(
            title = stringResource("audio_quality"),
            subtitle = audioQualitySubtitle,
            options = audioQualityOptions,
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onSelect = { index ->
                preferences.setAudioQuality(AudioQuality.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("audio_normalization"),
            subtitle = "",
            checked = normalizeAudio,
            onCheckedChange = { preferences.setNormalizeAudio(it) },
        )

        // Historial de reproducción
        SettingsSlider(
            title = stringResource("history_duration"),
            subtitle = if (historyDuration <= 0f) {
                stringResource("unlimited")
            } else {
                pluralStringResource("seconds", historyDuration.toInt(), historyDuration.toInt())
            },
            value = historyDuration.coerceIn(0f, 60f),
            valueRange = 0f..60f,
            steps = 59,
            onValueChange = { preferences.setHistoryDuration(it) },
        )

        // Skip Silence
        SettingsSwitch(
            title = stringResource("skip_silence"),
            subtitle = "",
            checked = skipSilence,
            onCheckedChange = { preferences.setSkipSilence(it) },
        )

        // Crossfade
        SettingsSlider(
            title = stringResource("crossfade"),
            subtitle = if (crossfadeDuration == 0) {
                stringResource("disabled")
            } else {
                pluralStringResource("seconds", crossfadeDuration, crossfadeDuration)
            },
            value = crossfadeDuration.toFloat(),
            valueRange = 0f..12f,
            steps = 11,
            onValueChange = { preferences.setCrossfadeDuration(it.toInt()) },
        )

        // Persistent Queue
        SettingsSwitch(
            title = stringResource("persistent_queue"),
            subtitle = "",
            checked = persistentQueue,
            onCheckedChange = { preferences.setPersistentQueue(it) },
        )

        // Auto Start Radio
        SettingsSwitch(
            title = stringResource("auto_start_radio"),
            subtitle = stringResource("auto_start_radio_desc"),
            checked = autoStartRadio,
            onCheckedChange = { preferences.setAutoStartRadio(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Show Lyrics
        SettingsSwitch(
            title = stringResource("lyrics"),
            subtitle = "",
            checked = showLyrics,
            onCheckedChange = { preferences.setShowLyrics(it) },
        )

        // Romanize Lyrics
        SettingsSwitch(
            title = stringResource("lyrics_romanization"),
            subtitle = "",
            checked = romanizeLyrics,
            onCheckedChange = { preferences.setRomanizeLyrics(it) },
            enabled = showLyrics,
        )
    }
}

@Composable
internal fun LegacyContentSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val appLanguage by preferences.appLanguage.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicks by preferences.quickPicks.collectAsState()
    val enableBetterLyrics by preferences.enableBetterLyrics.collectAsState()
    val enableSimpMusic by preferences.enableSimpMusic.collectAsState()
    val enableLrcLib by preferences.enableLrcLib.collectAsState()
    val enableKuGou by preferences.enableKuGou.collectAsState()
    val preferredLyricsProvider by preferences.preferredLyricsProvider.collectAsState()

    val languageCodes = appLanguageOptions()
    val countryCodes = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()
    val systemDefaultLabel = stringResource("system_default")
    val languageLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else LanguageCodeToName[code] ?: code
    }
    val countryLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else CountryCodeToName[code] ?: code
    }

    SettingsSubScreen(
        title = stringResource("content"),
        onBack = onBack,
    ) {
        SettingsDropdown(
            title = stringResource("app_language"),
            subtitle = languageLabel(appLanguage),
            options = languageCodes.map(languageLabel),
            selectedIndex = languageCodes.indexOf(appLanguage).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setAppLanguage(languageCodes[index])
            },
        )

        // Language
        SettingsDropdown(
            title = stringResource("content_language"),
            subtitle = languageLabel(contentLanguage),
            options = languageCodes.map(languageLabel),
            selectedIndex = languageCodes.indexOf(contentLanguage).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentLanguage(languageCodes[index])
            },
        )

        // Country
        SettingsDropdown(
            title = stringResource("content_country"),
            subtitle = countryLabel(contentCountry),
            options = countryCodes.map(countryLabel),
            selectedIndex = countryCodes.indexOf(contentCountry).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentCountry(countryCodes[index])
            },
        )

        // Hide Explicit
        SettingsSwitch(
            title = stringResource("hide_explicit"),
            subtitle = stringResource("hide_explicit_desc"),
            checked = hideExplicit,
            onCheckedChange = { preferences.setHideExplicit(it) },
        )

        SettingsSectionTitle(title = stringResource("lyrics"))

        SettingsSwitch(
            title = stringResource("enable_betterlyrics"),
            subtitle = "",
            checked = enableBetterLyrics,
            onCheckedChange = { preferences.setEnableBetterLyrics(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_simpmusic"),
            subtitle = "",
            checked = enableSimpMusic,
            onCheckedChange = { preferences.setEnableSimpMusic(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_lrclib"),
            subtitle = "",
            checked = enableLrcLib,
            onCheckedChange = { preferences.setEnableLrcLib(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_kugou"),
            subtitle = "",
            checked = enableKuGou,
            onCheckedChange = { preferences.setEnableKuGou(it) },
        )

        val providerOptions = listOf(
            PreferredLyricsProvider.LRCLIB,
            PreferredLyricsProvider.KUGOU,
            PreferredLyricsProvider.BETTER_LYRICS,
            PreferredLyricsProvider.SIMPMUSIC,
        )
        val betterLyricsLabel = stringResource("lyrics_provider_betterlyrics")
        val simpMusicLabel = stringResource("lyrics_provider_simpmusic")
        val providerLabel: (PreferredLyricsProvider) -> String = { provider ->
            when (provider) {
                PreferredLyricsProvider.LRCLIB -> "LrcLib"
                PreferredLyricsProvider.KUGOU -> "KuGou"
                PreferredLyricsProvider.BETTER_LYRICS -> betterLyricsLabel
                PreferredLyricsProvider.SIMPMUSIC -> simpMusicLabel
            }
        }

        SettingsDropdown(
            title = stringResource("set_first_lyrics_provider"),
            subtitle = providerLabel(preferredLyricsProvider),
            options = providerOptions.map(providerLabel),
            selectedIndex = providerOptions.indexOf(preferredLyricsProvider).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setPreferredLyricsProvider(providerOptions[index])
            },
        )

        // Quick Picks mode
        SettingsDropdown(
            title = stringResource("quick_picks"),
            subtitle = when (quickPicks) {
                QuickPicks.QUICK_PICKS -> stringResource("quick_picks")
                QuickPicks.LAST_LISTEN -> stringResource("last_song_listened")
            },
            options = listOf(stringResource("quick_picks"), stringResource("last_song_listened")),
            selectedIndex = if (quickPicks == QuickPicks.QUICK_PICKS) 0 else 1,
            onSelect = { index ->
                preferences.setQuickPicks(if (index == 0) QuickPicks.QUICK_PICKS else QuickPicks.LAST_LISTEN)
            },
        )
    }
}

@Composable
internal fun LegacyPrivacySettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val pauseListenHistory by preferences.pauseListenHistory.collectAsState()
    val pauseSearchHistory by preferences.pauseSearchHistory.collectAsState()

    SettingsSubScreen(
        title = stringResource("privacy"),
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = stringResource("pause_listen_history"),
            subtitle = "",
            checked = pauseListenHistory,
            onCheckedChange = { preferences.setPauseListenHistory(it) },
        )

        SettingsSwitch(
            title = stringResource("pause_search_history"),
            subtitle = "",
            checked = pauseSearchHistory,
            onCheckedChange = { preferences.setPauseSearchHistory(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Clear buttons
        var showClearHistoryDialog by remember { mutableStateOf(false) }
        var showClearSearchDialog by remember { mutableStateOf(false) }

        SettingsButton(
            title = stringResource("clear_listen_history"),
            subtitle = "",
            onClick = { showClearHistoryDialog = true },
        )

        SettingsButton(
            title = stringResource("clear_search_history"),
            subtitle = "",
            onClick = { showClearSearchDialog = true },
        )

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text(stringResource("clear_listen_history")) },
                text = { Text(stringResource("clear_listen_history_confirm")) },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear history via database
                        showClearHistoryDialog = false
                    }) {
                        Text(stringResource("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }

        if (showClearSearchDialog) {
            AlertDialog(
                onDismissRequest = { showClearSearchDialog = false },
                title = { Text(stringResource("clear_search_history")) },
                text = { Text(stringResource("clear_search_history_confirm")) },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear search history via database
                        showClearSearchDialog = false
                    }) {
                        Text(stringResource("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearSearchDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }
    }
}

@Composable
internal fun LegacyStorageSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val maxImageCacheSizeMB by preferences.maxImageCacheSizeMB.collectAsState()
    val maxSongCacheSizeMB by preferences.maxSongCacheSizeMB.collectAsState()
    val downloadAsMp3 by preferences.downloadAsMp3.collectAsState()

    SettingsSubScreen(
        title = stringResource("storage"),
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = stringResource("download_as_mp3"),
            subtitle = stringResource("download_as_mp3_desc"),
            checked = downloadAsMp3,
            onCheckedChange = { preferences.setDownloadAsMp3(it) },
        )

        // Image Cache Size
        SettingsSlider(
            title = stringResource("image_cache"),
            subtitle = "$maxImageCacheSizeMB MB",
            value = maxImageCacheSizeMB.toFloat(),
            valueRange = 100f..2000f,
            steps = 18,
            onValueChange = { preferences.setMaxImageCacheSizeMB(it.toInt()) },
        )

        // Song Cache Size
        SettingsSlider(
            title = stringResource("song_cache"),
            subtitle = "$maxSongCacheSizeMB MB",
            value = maxSongCacheSizeMB.toFloat(),
            valueRange = 500f..10000f,
            steps = 18,
            onValueChange = { preferences.setMaxSongCacheSizeMB(it.toInt()) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        var showClearCacheDialog by remember { mutableStateOf(false) }

        SettingsButton(
            title = stringResource("clear_cache"),
            subtitle = stringResource("clear_cache_desc"),
            onClick = { showClearCacheDialog = true },
        )

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource("clear_cache_confirm_title")) },
                text = { Text(stringResource("clear_cache_confirm_desc")) },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear cache
                        showClearCacheDialog = false
                    }) {
                        Text(stringResource("clear_cache"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }
    }
}

// ==================== DOMAIN MODELS ====================
private data class AboutBuildInfo(
    val buildType: String,
    val versionName: String,
    val versionCode: Int,
    val deviceInfo: String,
)

private data class AboutCardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val url: String,
)

private data class AboutTeamMember(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: String,
    val url: String,
)

private const val PulseLabel = "pulse"
private const val ShimmerLabel = "shimmer"

@Composable
internal fun LegacyAboutScreen(
    onBack: () -> Unit,
) {
    // ==================== DATA ====================
    val osName = System.getProperty("os.name") ?: "Desktop"
    val osVersion = System.getProperty("os.version") ?: ""
    val osArch = System.getProperty("os.arch") ?: ""

    val buildInfo = AboutBuildInfo(
        buildType = "Desktop",
        versionName = "1.13.1",
        versionCode = 1,
        deviceInfo = "$osName $osVersion ($osArch)",
    )

    val linkItems = listOf(
        AboutCardItem(
            title = stringResource("my_channel"),
            subtitle = stringResource("my_channel_info"),
            icon = IconAssets.discord(),
            url = "https://discord.gg/fvskrQZb9j",
        ),
        AboutCardItem(
            title = stringResource("other_apps"),
            subtitle = stringResource("other_apps_info"),
            icon = IconAssets.babelSoftwareApps(),
            url = "https://github.com/Animetailapp",
        ),
        AboutCardItem(
            title = stringResource("patreon"),
            subtitle = stringResource("patreon_info"),
            icon = IconAssets.patreon(),
            url = "https://www.patreon.com/abydev",
        ),
    )

    val developer = AboutTeamMember(
        id = "dev",
        name = "[̲̅A̲̅][̲̅b̲̅][̲̅y̲̅]",
        role = stringResource("info_dev"),
        avatarUrl = "https://avatars.githubusercontent.com/u/21024973?v=4",
        url = "https://github.com/Dark25",
    )

    val testers = listOf(
        AboutTeamMember(id = "t1", name = "im.shoul", role = "Beta Tester", avatarUrl = "https://i.ibb.co/pjkzBvGn/image.png", url = "https://discord.com/users/237686500567810058"),
        AboutTeamMember(id = "t2", name = "Lucia (Lú)", role = "Beta Tester", avatarUrl = "https://i.ibb.co/DPjf5V78/61fc6cc5422936a8fd81a913fbdf773b.png", url = "https://discord.com/users/553307420688908320"),
        AboutTeamMember(id = "t3", name = "ElDeLasTojas", role = "Beta Tester", avatarUrl = "https://i.ibb.co/mrgvc1nb/14f2a18fa8b9e553a048027375db5f81.png", url = "https://discord.com/users/444680132393697291"),
        AboutTeamMember(id = "t4", name = "SKHLORDKIRA", role = "Beta Tester", avatarUrl = "https://i.ibb.co/gnXkhnJ/be81ecb723cfd4186e85bfe81793f594.png", url = "https://discord.com/users/445310321717018626"),
        AboutTeamMember(id = "t5", name = "Abyss", role = "Beta Tester", avatarUrl = "https://i.ibb.co/TDdPq2jF/0f0f47f2a47eca3eda2a433237b4a05d.png", url = "https://discord.com/users/341662495301304323"),
        AboutTeamMember(id = "t6", name = "Jack", role = "Beta Tester", avatarUrl = "https://i.ibb.co/3YPX1wsj/dec881377d42d58473b6d988165406b6.png", url = "https://discord.com/users/1166985299885309954"),
        AboutTeamMember(id = "t7", name = "R4fa3l_2008", role = "Beta Tester", avatarUrl = "https://i.ibb.co/htmds91/b514910877f4b585309265fbe922f020.png", url = "https://discord.com/users/1318948121782521890"),
        AboutTeamMember(id = "t8", name = "Ryak", role = "Beta Tester", avatarUrl = "https://i.ibb.co/mrwz7J7K/165cbedbd96ae35c2489286c8db9777d.png", url = "https://discord.com/users/1075797587770228856"),
        AboutTeamMember(id = "t9", name = "LucianRC", role = "Beta Tester", avatarUrl = "https://i.ibb.co/LXXWGJCt/e8cdcf2c32ee7056806c5a8bfa607830.png", url = "https://discord.com/users/420641532446769157"),
        AboutTeamMember(id = "t10", name = "Alexx", role = "Beta Tester", avatarUrl = "https://i.ibb.co/8Dc1f67r/image.png", url = "https://discord.com/users/743896907184734268"),
    )

    // ==================== ANIMATIONS ====================
    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isVisible = true
    }

    // Helper to open URLs on desktop
    val openUrl: (String) -> Unit = { url ->
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url.trim()))
        } catch (_: Exception) { }
    }

    // ==================== UI ====================
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = IconAssets.arrowBack(),
                    contentDescription = stringResource("about"),
                )
            }
            Text(
                text = stringResource("about"),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alpha(
                    androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                    ).value
                ),
            )
        }

        // Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ============ HEADER CARD ============
            item(key = "header") {
                AboutHeaderCard(buildInfo)
            }

            // ============ LINKS SECTION ============
            item(key = "links") {
                AboutSectionCard(
                    title = stringResource("links_about"),
                    icon = IconAssets.links(),
                    iconTint = MaterialTheme.colorScheme.primary,
                    items = linkItems,
                    onItemClick = openUrl,
                )
            }

            // ============ DEVELOPER SECTION ============
            item(key = "dev") {
                AboutTeamGrid(
                    title = stringResource("developer_about"),
                    icon = IconAssets.person(),
                    members = listOf(developer),
                    onMemberClick = openUrl,
                )
            }

            // ============ BETA TESTERS SECTION ============
            item(key = "testers") {
                AboutTeamGrid(
                    title = stringResource("beta_testers"),
                    icon = IconAssets.person(),
                    members = testers,
                    onMemberClick = openUrl,
                )
            }
        }
    }
}

// ==================== PREMIUM COMPOSABLES ====================

@Composable
private fun AboutHeaderCard(buildInfo: AboutBuildInfo) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface,
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
        ) {
            // Animated logo with pulse
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            ) {
                val infiniteTransition = rememberInfiniteTransition(PulseLabel)
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable<Float>(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = PulseLabel,
                )

                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = IconAssets.icAni(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        // Shimmer overlay
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                        ) {
                            AboutShimmerOverlay()
                        }
                    }
                }
            }

            // Build info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AboutBuildInfoRow(IconAssets.buildIcon(), stringResource("about_build"), buildInfo.buildType)
                AboutBuildInfoRow(IconAssets.verified(), stringResource("about_version_title"), buildInfo.versionName)
                AboutBuildInfoRow(IconAssets.devices(), stringResource("about_device"), buildInfo.deviceInfo)
            }
        }
    }
}

@Composable
private fun AboutShimmerOverlay() {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0f),
        Color.White.copy(alpha = 0.3f),
        Color.White.copy(alpha = 0f),
    )
    val transition = rememberInfiniteTransition(ShimmerLabel)
    val translate by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable<Float>(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Restart,
        ),
        label = ShimmerLabel,
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translate * size.width, 0f),
                end = Offset(translate * size.width + size.width / 2, 0f),
            ),
            blendMode = BlendMode.Screen,
        )
    }
}

@Composable
private fun AboutBuildInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label + ":",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    items: List<AboutCardItem>,
    onItemClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Link cards
            items.forEach { item ->
                AboutLinkCardItem(item.title, item.subtitle, item.icon, item.url, onItemClick)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AboutLinkCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    url: String,
    onItemClick: (String) -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh),
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onItemClick(url) },
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = IconAssets.openInNew(),
                contentDescription = stringResource("open_link"),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutTeamGrid(
    title: String,
    icon: ImageVector,
    members: List<AboutTeamMember>,
    onMemberClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = members.size.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            // Member grid — desktop uses 5 columns
            val columns = 5
            val rows = members.chunked(columns)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { member ->
                        AboutTeamMemberCard(
                            id = member.id,
                            name = member.name,
                            role = member.role,
                            avatarUrl = member.avatarUrl,
                            onClick = { onMemberClick(member.url) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Fill remaining columns with spacers
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AboutTeamMemberCard(
    id: String,
    name: String,
    role: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
    )

    Column(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            shadowElevation = if (isPressed) 2.dp else 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                if (avatarUrl != null) {
                    RemoteImage(
                        url = avatarUrl.trim(),
                        contentDescription = name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.person(),
                        contentDescription = name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                // Online indicator (for developer)
                if (id == "dev") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// === Helper Components ===

@Composable
internal fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = IconAssets.arrowBack(),
                    contentDescription = stringResource("back"),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
internal fun AndroidPreferenceGroupTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
internal fun AndroidPreferenceEntry(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null, onClick = onClick ?: {})
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDropdown(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
                Icon(
                    imageVector = if (expanded) IconAssets.expandLess() else IconAssets.expandMore(),
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                        leadingIcon = if (index == selectedIndex) {
                            { Icon(IconAssets.check(), contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

@Composable
internal fun SettingsButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector = IconAssets.link(),
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun SettingsTextField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    placeholder: String = "",
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource("save"))
            }
        }
    }
}

@Composable
internal fun SettingsInfoItem(
    title: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: SettingsDestination,
)
