package com.anitail.desktop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAccountTokenParser
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.AvatarSourcePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import kotlinx.coroutines.launch

@Composable
internal fun AccountSettingsScreen(
    preferences: DesktopPreferences,
    authService: DesktopAuthService,
    authCredentials: AuthCredentials?,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSpotifyImport: () -> Unit,
    onOpenDiscordSettings: () -> Unit,
    onAuthChanged: (AuthCredentials?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useLoginForBrowse by preferences.useLoginForBrowse.collectAsState()
    val ytmSync by preferences.ytmSync.collectAsState()
    val discordToken by preferences.discordToken.collectAsState()
    val preferredAvatarSource by preferences.preferredAvatarSource.collectAsState()
    val hasCookie = authCredentials?.cookie?.isNotBlank() == true
    val hasDataSyncId = authCredentials?.dataSyncId?.isNotBlank() == true
    val isLoggedIn = hasCookie
    var accountInfo by remember { mutableStateOf<com.anitail.desktop.auth.AccountInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showAvatarSourceDialog by remember { mutableStateOf(false) }
    val loginEnabled = false
    val loginDisabled = !loginEnabled && !isLoggedIn
    val loginEntryAlpha = if (loginDisabled) 0.5f else 1f
    val canUseDiscordAvatar = isLoggedIn || discordToken.isNotBlank()

    LaunchedEffect(isLoggedIn) {
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
                                ?: authCredentials.accountName
                                ?: stringResource("account")
                        } else {
                            stringResource("login")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val subtitle = if (isLoggedIn) {
                        accountInfo?.email
                            ?: authCredentials.accountEmail
                            ?: authCredentials.channelHandle
                    } else if (!loginEnabled) {
                        if (hasDataSyncId && !hasCookie) {
                            stringResource("login_requires_cookie")
                        } else {
                            stringResource("login_not_available_desktop")
                        }
                    } else {
                        null
                    }
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
            onClick = onOpenSpotifyImport,
        )

        AndroidPreferenceGroupTitle(title = stringResource("discord"))
        AndroidPreferenceEntry(
            title = stringResource("discord_integration"),
            icon = IconAssets.discord(),
            onClick = onOpenDiscordSettings,
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
