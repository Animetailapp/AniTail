package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.auth.DesktopDiscordService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.shared.model.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun DiscordSettingsScreen(
    preferences: DesktopPreferences,
    previewItem: LibraryItem?,
    previewPositionMs: Long,
    isPreviewPlaying: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val discordToken by preferences.discordToken.collectAsState()
    val discordName by preferences.discordName.collectAsState()
    val discordUsername by preferences.discordUsername.collectAsState()
    val discordAvatarUrl by preferences.discordAvatarUrl.collectAsState()
    val infoDismissed by preferences.discordInfoDismissed.collectAsState()
    val discordRpcEnabled by preferences.enableDiscordRPC.collectAsState()
    val lastSyncEpochMillis by preferences.discordLastSyncEpochMillis.collectAsState()
    val lastErrorMessage by preferences.discordLastErrorMessage.collectAsState()

    var showDiscordEditor by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val isLoggedIn = discordToken.isNotBlank()
    val statusLabel = when {
        !discordRpcEnabled -> stringResource("discord_status_disabled")
        isRefreshing -> stringResource("discord_status_connecting")
        lastErrorMessage.isNotBlank() -> stringResource("discord_status_error")
        isLoggedIn -> stringResource("discord_status_connected")
        else -> stringResource("discord_status_idle")
    }

    val lastSyncLabel = if (lastSyncEpochMillis <= 0L) {
        stringResource("discord_last_sync_never")
    } else {
        relativeTimeLabel(lastSyncEpochMillis)
    }

    val refreshLabel = if (isRefreshing) {
        stringResource("discord_manual_refreshing")
    } else {
        stringResource("discord_manual_refresh")
    }
    val discordStatusErrorText = stringResource("discord_status_error")

    fun refreshProfile(token: String) {
        val sanitizedToken = token.trim()
        if (sanitizedToken.isBlank()) {
            preferences.setDiscordName("")
            preferences.setDiscordUsername("")
            preferences.setDiscordAvatarUrl("")
            preferences.setDiscordLastErrorMessage("")
            return
        }

        scope.launch {
            isRefreshing = true
            val profile = withContext(Dispatchers.IO) {
                DesktopDiscordService.fetchProfile(sanitizedToken)
            }
            if (profile != null) {
                preferences.setDiscordName(profile.name)
                preferences.setDiscordUsername(profile.username)
                preferences.setDiscordAvatarUrl(profile.avatarUrl.orEmpty())
                preferences.setDiscordLastErrorMessage("")
                preferences.setDiscordLastSyncEpochMillis(System.currentTimeMillis())
            } else {
                preferences.setDiscordLastErrorMessage(discordStatusErrorText)
            }
            isRefreshing = false
        }
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
                    TextButton(
                        onClick = {
                            runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI("https://discord.com/channels/@me"))
                            }
                        }
                    ) {
                        Text(stringResource("open_in_browser"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isRefreshing,
                    onClick = {
                        val token = discordTokenInput.trim()
                        preferences.setDiscordToken(token)
                        if (token.isBlank()) {
                            preferences.setDiscordName("")
                            preferences.setDiscordUsername("")
                            preferences.setDiscordAvatarUrl("")
                            preferences.setDiscordLastErrorMessage("")
                            preferences.setDiscordLastSyncEpochMillis(0L)
                        } else {
                            refreshProfile(token)
                        }
                        showDiscordEditor = false
                    },
                ) { Text(stringResource("save")) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscordEditor = false }) { Text(stringResource("cancel")) }
            },
        )
    }

    SettingsSubScreen(
        title = stringResource("discord_integration"),
        onBack = onBack,
    ) {
        if (!infoDismissed) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {
                Icon(
                    imageVector = IconAssets.info(),
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = stringResource("discord_information"),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = { preferences.setDiscordInfoDismissed(true) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(16.dp),
                ) {
                    Text(stringResource("dismiss"))
                }
            }
        }

        AndroidPreferenceGroupTitle(title = stringResource("account"))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (discordAvatarUrl.isNotBlank()) {
                    RemoteImage(
                        url = discordAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        imageVector = IconAssets.discord(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLoggedIn) 1f else 0.5f),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = if (isLoggedIn) {
                            discordName.ifBlank { discordUsername.ifBlank { stringResource("discord") } }
                        } else {
                            stringResource("not_logged_in")
                        },
                        modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                    )
                    if (discordUsername.isNotBlank()) {
                        Text(
                            text = "@$discordUsername",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (isLoggedIn) {
                            preferences.setDiscordToken("")
                            preferences.setDiscordName("")
                            preferences.setDiscordUsername("")
                            preferences.setDiscordAvatarUrl("")
                            preferences.setDiscordLastErrorMessage("")
                            preferences.setDiscordLastSyncEpochMillis(0L)
                        } else {
                            showDiscordEditor = true
                        }
                    },
                ) {
                    Text(stringResource(if (isLoggedIn) "logout" else "login"))
                }
            }
        }

        AndroidPreferenceGroupTitle(title = stringResource("options"))
        SettingsSwitch(
            title = stringResource("enable_discord_rpc"),
            subtitle = "",
            checked = discordRpcEnabled,
            onCheckedChange = { preferences.setEnableDiscordRPC(it) },
            enabled = isLoggedIn,
        )

        if (isLoggedIn) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    SettingsInfoItem(
                        title = stringResource("discord_connection_state"),
                        value = statusLabel,
                    )
                    SettingsInfoItem(
                        title = stringResource("discord_last_sync"),
                        value = lastSyncLabel,
                    )

                    if (lastErrorMessage.isNotBlank()) {
                        Text(
                            text = stringResource("discord_error_format", lastErrorMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    OutlinedButton(
                        onClick = { refreshProfile(discordToken) },
                        enabled = !isRefreshing && discordRpcEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(refreshLabel)
                    }
                }
            }
        }

        AndroidPreferenceGroupTitle(title = stringResource("preview"))
        DiscordPreviewCard(
            item = previewItem,
            positionMs = previewPositionMs,
            isPlaying = isPreviewPlaying,
        )
    }
}

@Composable
private fun DiscordPreviewCard(
    item: LibraryItem?,
    positionMs: Long,
    isPlaying: Boolean,
) {
    val durationMs = max(item?.durationMs ?: 0L, 1L)
    val progress = if (item != null) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Listening to AniTail",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(108.dp)) {
                    if (!item?.artworkUrl.isNullOrBlank()) {
                        RemoteImage(
                            url = item.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .align(Alignment.TopStart),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = item?.title ?: "Song title",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item?.artist ?: "Artist",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDiscordPreviewTime(positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatDiscordPreviewTime(if (item != null) durationMs else 0L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                enabled = item != null,
                onClick = {
                    val trackId = item?.id?.trim().orEmpty()
                    if (trackId.isNotBlank()) {
                        runCatching {
                            java.awt.Desktop.getDesktop().browse(java.net.URI("https://music.youtube.com/watch?v=$trackId"))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Listen on YouTube Music")
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("https://discord.gg/H8x3yNbc67"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Join our discord")
            }
        }
    }
}

private fun relativeTimeLabel(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "just now"
        diff < hour -> "${diff / minute}m ago"
        diff < day -> "${diff / hour}h ago"
        else -> "${diff / day}d ago"
    }
}

private fun formatDiscordPreviewTime(milliseconds: Long): String {
    val safeMs = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
