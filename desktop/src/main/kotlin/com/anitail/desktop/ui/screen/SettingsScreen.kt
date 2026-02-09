package com.anitail.desktop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAccountTokenParser
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DarkModePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
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
import kotlinx.coroutines.launch

/**
 * Settings navigation destinations
 */
enum class SettingsDestination {
    MAIN,
    ACCOUNT,
    APPEARANCE,
    PLAYER,
    CONTENT,
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
    authService: DesktopAuthService,
    authCredentials: AuthCredentials?,
    onOpenLogin: () -> Unit,
    onAuthChanged: (AuthCredentials?) -> Unit,
) {
    var currentDestination by remember { mutableStateOf(SettingsDestination.MAIN) }

    Column(modifier = Modifier.fillMaxSize()) {
        when (currentDestination) {
            SettingsDestination.MAIN -> SettingsMainScreen(
                onNavigate = { currentDestination = it },
            )

            SettingsDestination.ACCOUNT -> AccountSettingsScreen(
                preferences = preferences,
                authService = authService,
                authCredentials = authCredentials,
                onBack = { currentDestination = SettingsDestination.MAIN },
                onOpenLogin = onOpenLogin,
                onAuthChanged = onAuthChanged,
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
            )

            SettingsDestination.PRIVACY -> PrivacySettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.STORAGE -> StorageSettingsScreen(
                preferences = preferences,
                onBack = { currentDestination = SettingsDestination.MAIN },
            )

            SettingsDestination.ABOUT -> AboutScreen(
                onBack = { currentDestination = SettingsDestination.MAIN },
            )
        }
    }
}

@Composable
private fun SettingsMainScreen(
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
private fun AccountSettingsScreen(
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
    val hasCookie = authCredentials?.cookie?.isNotBlank() == true
    val hasDataSyncId = authCredentials?.dataSyncId?.isNotBlank() == true
    val isLoggedIn = hasCookie
    var accountInfo by remember { mutableStateOf<com.anitail.desktop.auth.AccountInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    val loginEnabled = false
    val loginDisabled = !loginEnabled && !isLoggedIn
    val loginEntryAlpha = if (loginDisabled) 0.5f else 1f

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

    SettingsSubScreen(
        title = stringResource("account"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = "Google")

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
    }
}

@Composable
private fun SettingsCategoryItem(
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
private fun AppearanceSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()

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

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsDropdown(
            title = stringResource("player_background_style"),
            subtitle = playerBackgroundStyle.displayName,
            options = PlayerBackgroundStyle.entries.map { it.displayName },
            selectedIndex = PlayerBackgroundStyle.entries.indexOf(playerBackgroundStyle),
            onSelect = { index ->
                preferences.setPlayerBackgroundStyle(PlayerBackgroundStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = stringResource("player_buttons_style"),
            subtitle = playerButtonsStyle.displayName,
            options = PlayerButtonsStyle.entries.map { it.displayName },
            selectedIndex = PlayerButtonsStyle.entries.indexOf(playerButtonsStyle),
            onSelect = { index ->
                preferences.setPlayerButtonsStyle(PlayerButtonsStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = stringResource("player_slider_style"),
            subtitle = sliderStyle.displayName,
            options = SliderStyle.entries.map { it.displayName },
            selectedIndex = SliderStyle.entries.indexOf(sliderStyle),
            onSelect = { index ->
                preferences.setSliderStyle(SliderStyle.entries[index])
            },
        )
    }
}

@Composable
private fun PlayerSettingsScreen(
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
        val audioQualityLabels = mapOf(
            AudioQuality.LOW to stringResource("audio_quality_low"),
            AudioQuality.MEDIUM to stringResource("audio_quality_medium"),
            AudioQuality.HIGH to stringResource("audio_quality_high"),
            AudioQuality.AUTO to stringResource("audio_quality_auto"),
        )
        val audioQualityLabel: (AudioQuality) -> String = { quality ->
            audioQualityLabels[quality] ?: quality.name.lowercase()
        }

        // Audio Quality
        SettingsDropdown(
            title = stringResource("audio_quality"),
            subtitle = audioQualityLabel(audioQuality),
            options = AudioQuality.entries.map(audioQualityLabel),
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
            title = stringResource("auto_load_more"),
            subtitle = stringResource("auto_load_more_desc"),
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
private fun ContentSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val appLanguage by preferences.appLanguage.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicks by preferences.quickPicks.collectAsState()

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
private fun PrivacySettingsScreen(
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
private fun StorageSettingsScreen(
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
                title = { Text("¿Limpiar caché?") },
                text = { Text(stringResource("clear_cache_confirm_desc")) },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: Clear cache
                        showClearCacheDialog = false
                    }) {
                        Text(stringResource("clear"))
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

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
) {
    SettingsSubScreen(
        title = stringResource("about"),
        onBack = onBack,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = IconAssets.musicNote(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AniTail",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource("desktop_edition"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "v1.0.0-desktop",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsInfoItem(
            title = stringResource("about_version_title"),
            value = "1.0.0-desktop",
        )

        SettingsInfoItem(
            title = stringResource("about_kotlin"),
            value = "2.0+",
        )

        SettingsInfoItem(
            title = stringResource("about_compose_desktop"),
            value = "1.7.x",
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsButton(
            title = stringResource("about_source_code"),
            subtitle = stringResource("about_source_code_desc"),
            onClick = { /* TODO: Open GitHub */ },
        )

        SettingsButton(
            title = stringResource("about_licenses"),
            subtitle = stringResource("about_licenses_desc"),
            onClick = { /* TODO: Show licenses */ },
        )
    }
}

// === Helper Components ===

@Composable
private fun SettingsSubScreen(
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
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
private fun SettingsSwitch(
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
private fun SettingsDropdown(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
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
                    .clickable { expanded = true }
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = if (expanded) IconAssets.expandLess() else IconAssets.expandMore(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SettingsSlider(
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
private fun SettingsButton(
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
private fun SettingsTextField(
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
private fun SettingsInfoItem(
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

private data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: SettingsDestination,
)
