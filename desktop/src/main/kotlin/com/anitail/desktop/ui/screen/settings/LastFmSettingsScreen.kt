package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.DesktopLastFmProfile
import com.anitail.desktop.auth.DesktopLastFmService
import com.anitail.desktop.auth.LastFmServiceResult
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import de.umass.lastfm.Artist
import de.umass.lastfm.Track
import kotlinx.coroutines.launch

@Composable
internal fun LastFmSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lastFmEnabled by preferences.lastFmEnabled.collectAsState()
    val lastFmUsername by preferences.lastFmUsername.collectAsState()
    val lastFmSessionKey by preferences.lastFmSessionKey.collectAsState()
    val scrobbleEnabled by preferences.lastFmScrobbleEnabled.collectAsState()
    val loveTracksEnabled by preferences.lastFmLoveTracks.collectAsState()
    val showLastFmAvatar by preferences.lastFmShowAvatar.collectAsState()

    var profile by remember { mutableStateOf<DesktopLastFmProfile?>(null) }
    var recentTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var pendingScrobbles by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var uiError by remember { mutableStateOf<String?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    fun clearUiState() {
        profile = null
        recentTracks = emptyList()
        topTracks = emptyList()
        topArtists = emptyList()
        pendingScrobbles = 0
        uiError = null
    }

    fun refreshData() {
        if (!lastFmEnabled || lastFmSessionKey.isBlank() || lastFmUsername.isBlank()) {
            clearUiState()
            return
        }
        scope.launch {
            isLoading = true
            pendingScrobbles = DesktopLastFmService.getPendingScrobblesCount()
            when (val profileResult = DesktopLastFmService.getUserInfo()) {
                is LastFmServiceResult.Success -> {
                    profile = profileResult.value
                    uiError = null
                }

                is LastFmServiceResult.Error -> {
                    uiError = profileResult.message
                }
            }
            when (val recent = DesktopLastFmService.getRecentTracks(limit = 10)) {
                is LastFmServiceResult.Success -> recentTracks = recent.value
                is LastFmServiceResult.Error -> Unit
            }
            when (val top = DesktopLastFmService.getTopTracks(limit = 10)) {
                is LastFmServiceResult.Success -> topTracks = top.value
                is LastFmServiceResult.Error -> Unit
            }
            when (val top = DesktopLastFmService.getTopArtists(limit = 10)) {
                is LastFmServiceResult.Success -> topArtists = top.value
                is LastFmServiceResult.Error -> Unit
            }
            isLoading = false
        }
    }

    LaunchedEffect(lastFmEnabled, lastFmSessionKey, lastFmUsername) {
        refreshData()
    }

    SettingsSubScreen(
        title = stringResource("lastfm_settings"),
        onBack = onBack,
    ) {
        val lastFmConfigured = remember { DesktopLastFmService.isConfigured() }

        SettingsSectionTitle(title = stringResource("account"))

        if (!lastFmConfigured) {
            SettingsInfoItem(
                title = stringResource("lastfm_settings"),
                value = stringResource("lastfm_api_keys_missing"),
            )
        } else if (lastFmEnabled && lastFmSessionKey.isNotBlank() && lastFmUsername.isNotBlank()) {
            if (showLastFmAvatar && !profile?.imageUrl.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteImage(
                            url = profile?.imageUrl,
                            contentDescription = stringResource("lastfm_profile_picture"),
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = profile?.username ?: lastFmUsername,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!profile?.realName.isNullOrBlank()) {
                                Text(
                                    text = profile?.realName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SettingsInfoItem(
                title = stringResource("logged_in_as_lastfm", lastFmUsername),
                value = stringResource("playcount", profile?.playcount ?: 0),
            )
            profile?.country
                ?.takeIf { it.isNotBlank() }
                ?.let { country ->
                    SettingsInfoItem(
                        title = stringResource("user_country", country),
                        value = "",
                    )
                }
            profile?.age
                ?.takeIf { it > 0 }
                ?.let { age ->
                    SettingsInfoItem(
                        title = stringResource("user_age", age.toString()),
                        value = "",
                    )
                }
            SettingsButton(
                title = stringResource("logout"),
                subtitle = "",
                onClick = { showLogoutDialog = true },
                icon = IconAssets.logout(),
            )
        } else {
            SettingsInfoItem(
                title = stringResource("not_logged_in"),
                value = "",
            )
            SettingsButton(
                title = stringResource("login_to_lastfm"),
                subtitle = stringResource("lastfm_login_description"),
                onClick = { showLoginDialog = true },
                icon = IconAssets.account(),
            )
        }

        if (lastFmEnabled && lastFmSessionKey.isNotBlank()) {
            SettingsSectionTitle(title = stringResource("scrobble_progress"))
            SettingsInfoItem(
                title = if (isSyncing) {
                    stringResource("syncing_scrobbles")
                } else {
                    stringResource("pending_scrobbles", pendingScrobbles)
                },
                value = if (pendingScrobbles == 0) stringResource("scrobbles_synced") else "",
            )
            SettingsButton(
                title = stringResource("retry_pending_scrobbles"),
                subtitle = "",
                onClick = {
                    if (isSyncing) return@SettingsButton
                    scope.launch {
                        isSyncing = true
                        when (val result = DesktopLastFmService.retryPendingScrobbles()) {
                            is LastFmServiceResult.Success -> {
                                pendingScrobbles = DesktopLastFmService.getPendingScrobblesCount()
                                uiError = null
                            }

                            is LastFmServiceResult.Error -> {
                                uiError = result.message
                            }
                        }
                        isSyncing = false
                    }
                },
                icon = IconAssets.refresh(),
            )
            SettingsButton(
                title = stringResource("clear_pending_scrobbles"),
                subtitle = "",
                onClick = {
                    if (isSyncing) return@SettingsButton
                    scope.launch {
                        DesktopLastFmService.clearPendingScrobbles()
                        pendingScrobbles = 0
                    }
                },
                icon = IconAssets.delete(),
            )

            SettingsSectionTitle(title = stringResource("lastfm_settings"))
            SettingsSwitch(
                title = stringResource("enable_scrobbling"),
                subtitle = stringResource("enable_scrobbling_description"),
                checked = scrobbleEnabled,
                onCheckedChange = { DesktopLastFmService.setScrobblingEnabled(it) },
            )
            SettingsSwitch(
                title = stringResource("love_tracks"),
                subtitle = stringResource("love_tracks_description"),
                checked = loveTracksEnabled,
                onCheckedChange = { DesktopLastFmService.setLoveTracksEnabled(it) },
            )
            SettingsSwitch(
                title = stringResource("show_lastfm_avatar"),
                subtitle = stringResource("lastfm_profile_picture"),
                checked = showLastFmAvatar,
                onCheckedChange = { DesktopLastFmService.setShowAvatar(it) },
            )

            SettingsSectionTitle(title = stringResource("recent_tracks"))
            if (recentTracks.isEmpty() && !isLoading) {
                SettingsInfoItem(
                    title = stringResource("no_recent_tracks_data"),
                    value = "",
                )
            } else {
                recentTracks.take(5).forEach { track ->
                    SettingsInfoItem(
                        title = "${track.artist} - ${track.name}",
                        value = "",
                    )
                }
            }

            SettingsSectionTitle(title = stringResource("top_tracks"))
            if (topTracks.isEmpty() && !isLoading) {
                SettingsInfoItem(
                    title = stringResource("no_top_tracks_data"),
                    value = "",
                )
            } else {
                topTracks.take(5).forEachIndexed { index, track ->
                    SettingsInfoItem(
                        title = "${index + 1}. ${track.artist} - ${track.name}",
                        value = track.playcount.toString(),
                    )
                }
            }

            SettingsSectionTitle(title = stringResource("top_artists"))
            if (topArtists.isEmpty() && !isLoading) {
                SettingsInfoItem(
                    title = stringResource("no_top_artists_data"),
                    value = "",
                )
            } else {
                topArtists.take(5).forEachIndexed { index, artist ->
                    SettingsInfoItem(
                        title = "${index + 1}. ${artist.name}",
                        value = artist.playcount.toString(),
                    )
                }
            }
        }

        SettingsSectionTitle(title = stringResource("about"))
        SettingsButton(
            title = stringResource("about_lastfm"),
            subtitle = stringResource("about_lastfm_description"),
            onClick = {
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://www.last.fm/about"))
                }
            },
            icon = IconAssets.info(),
        )

        if (!uiError.isNullOrBlank()) {
            Text(
                text = uiError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showLoginDialog) {
        var usernameInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var loginError by remember { mutableStateOf<String?>(null) }
        var isLoginLoading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text(stringResource("login_to_lastfm")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource("lastfm_login_description"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource("username")) },
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource("password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                    )
                    if (!loginError.isNullOrBlank()) {
                        Text(
                            text = loginError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isLoginLoading && usernameInput.isNotBlank() && passwordInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isLoginLoading = true
                            when (val result = DesktopLastFmService.authenticate(usernameInput, passwordInput)) {
                                is LastFmServiceResult.Success -> {
                                    showLoginDialog = false
                                    loginError = null
                                    refreshData()
                                }

                                is LastFmServiceResult.Error -> {
                                    loginError = result.message
                                }
                            }
                            isLoginLoading = false
                        }
                    },
                ) { Text(stringResource("login")) }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource("logout")) },
            text = { Text(stringResource("logout_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            DesktopLastFmService.logout()
                            clearUiState()
                            showLogoutDialog = false
                        }
                    },
                ) {
                    Text(stringResource("logout"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }
}
