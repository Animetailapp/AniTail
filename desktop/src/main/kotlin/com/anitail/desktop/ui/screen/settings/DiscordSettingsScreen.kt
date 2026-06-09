package com.anitail.desktop.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.RemoteImage
import com.anitail.desktop.util.DiscordIpcClient
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
    val discordRpcEnabled by preferences.enableDiscordRPC.collectAsState()

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    fun testConnection() {
        scope.launch {
            isTesting = true
            testResult = null
            val result = withContext(Dispatchers.IO) {
                val client = DiscordIpcClient("1271273225120125040")
                val connected = client.connect()
                if (connected) client.close()
                connected
            }
            testResult = result
            isTesting = false
        }
    }

    SettingsSubScreen(
        title = stringResource("discord_integration"),
        onBack = onBack,
    ) {
        // Info card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = IconAssets.info(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource("discord_ipc_info"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        AndroidPreferenceGroupTitle(title = stringResource("options"))

        // Enable toggle
        SettingsSwitch(
            title = stringResource("enable_discord_rpc"),
            subtitle = stringResource("discord_ipc_subtitle"),
            checked = discordRpcEnabled,
            onCheckedChange = { preferences.setEnableDiscordRPC(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Test connection card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource("discord_connection_state"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                if (isTesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource("discord_status_connecting"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    when (testResult) {
                        true -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource("discord_status_connected"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50),
                                )
                            }
                        }
                        false -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource("discord_ipc_not_found"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        null -> {
                            Text(
                                text = stringResource("discord_ipc_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { testConnection() },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = IconAssets.discord(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource("discord_test_connection"))
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
            .padding(vertical = 8.dp)
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
                            url = item?.artworkUrl,
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
        }
    }
}

private fun formatDiscordPreviewTime(milliseconds: Long): String {
    val safeMs = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
