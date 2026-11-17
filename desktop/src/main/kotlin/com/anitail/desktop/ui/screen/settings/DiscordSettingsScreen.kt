package com.anitail.desktop.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anitail.desktop.auth.DesktopDiscordService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import com.anitail.desktop.ui.component.ExpressiveFramedAvatar
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
    val discordRpcEnabled by preferences.enableDiscordRPC.collectAsState()
    val discordToken by preferences.discordToken.collectAsState()
    val discordName by preferences.discordName.collectAsState()
    val discordUsername by preferences.discordUsername.collectAsState()
    val discordAvatarUrl by preferences.discordAvatarUrl.collectAsState()
    val advancedMode by preferences.discordAdvancedMode.collectAsState()
    val userStatus by preferences.discordUserStatus.collectAsState()
    val activityType by preferences.discordActivityType.collectAsState()
    val activityName by preferences.discordActivityName.collectAsState()
    val detailsTemplate by preferences.discordDetailsTemplate.collectAsState()
    val stateTemplate by preferences.discordStateTemplate.collectAsState()
    val button1Enabled by preferences.discordButton1Enabled.collectAsState()
    val button1Label by preferences.discordButton1Label.collectAsState()
    val button1Url by preferences.discordButton1Url.collectAsState()
    val button2Enabled by preferences.discordButton2Enabled.collectAsState()
    val button2Label by preferences.discordButton2Label.collectAsState()
    val button2Url by preferences.discordButton2Url.collectAsState()

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    fun applyDiscordToken(rawToken: String, rawRefreshToken: String? = null) {
        val token = rawToken.trim()
        if (token.isEmpty()) return
        scope.launch {
            isTesting = true
            testResult = null
            authError = null
            val ok = withContext(Dispatchers.IO) {
                val profile = DesktopDiscordService.fetchProfile(token)
                if (profile != null) {
                    preferences.setDiscordToken(token)
                    if (!rawRefreshToken.isNullOrBlank()) {
                        preferences.setDiscordRefreshToken(rawRefreshToken.trim())
                    }
                    preferences.setDiscordName(profile.name)
                    preferences.setDiscordUsername(profile.username)
                    preferences.setDiscordAvatarUrl(profile.avatarUrl.orEmpty())
                    true
                } else {
                    false
                }
            }
            if (!ok) authError = "No se pudo verificar el perfil de Discord"
            testResult = ok
            isTesting = false
        }
    }

    fun startDiscordLogin() {
        scope.launch {
            isTesting = true
            testResult = null
            authError = null
            val authResult = DesktopDiscordService.startBrowserOAuthLogin()
            authResult.onSuccess { result ->
                val ok = withContext(Dispatchers.IO) {
                    val profile = DesktopDiscordService.fetchProfile(result.accessToken)
                    if (profile != null) {
                        preferences.setDiscordToken(result.accessToken)
                        if (result.refreshToken.isNotBlank()) {
                            preferences.setDiscordRefreshToken(result.refreshToken)
                        }
                        preferences.setDiscordName(profile.name)
                        preferences.setDiscordUsername(profile.username)
                        preferences.setDiscordAvatarUrl(profile.avatarUrl.orEmpty())
                        true
                    } else {
                        false
                    }
                }
                if (!ok) authError = "No se pudo obtener el perfil de Discord"
                testResult = ok
            }.onFailure { err ->
                println("[DiscordAuth] Login failed: ${err.message}")
                authError = err.message ?: "Error al iniciar sesión con Discord"
                testResult = false
            }
            isTesting = false
        }
    }

    fun testConnection() {
        applyDiscordToken(discordToken)
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

        // Advanced Mode toggle
        SettingsSwitch(
            title = stringResource("discord_advanced_mode"),
            subtitle = stringResource("discord_advanced_mode_description"),
            checked = advancedMode,
            onCheckedChange = { preferences.setDiscordAdvancedMode(it) },
            enabled = discordRpcEnabled,
        )

        AnimatedVisibility(visible = advancedMode && discordRpcEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statusOptions = listOf(
                    "online" to stringResource("discord_status_online"),
                    "idle" to stringResource("discord_status_idle"),
                    "dnd" to stringResource("discord_status_dnd"),
                )
                SettingsDropdown(
                    title = stringResource("discord_status"),
                    subtitle = statusOptions.firstOrNull { it.first == userStatus }?.second
                        ?: stringResource("discord_status_online"),
                    options = statusOptions.map { it.second },
                    selectedIndex = statusOptions.indexOfFirst { it.first == userStatus }.coerceAtLeast(0),
                    onSelect = { index -> preferences.setDiscordUserStatus(statusOptions[index].first) },
                )

                val activityTypeOptions = listOf(
                    2 to stringResource("discord_activity_listening"),
                    0 to stringResource("discord_activity_playing"),
                    3 to stringResource("discord_activity_watching"),
                    5 to stringResource("discord_activity_competing"),
                )
                SettingsDropdown(
                    title = stringResource("discord_activity_type"),
                    subtitle = activityTypeOptions.firstOrNull { it.first == activityType }?.second
                        ?: stringResource("discord_activity_listening"),
                    options = activityTypeOptions.map { it.second },
                    selectedIndex = activityTypeOptions.indexOfFirst { it.first == activityType }.coerceAtLeast(0),
                    onSelect = { index -> preferences.setDiscordActivityType(activityTypeOptions[index].first) },
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = activityName,
                            onValueChange = { preferences.setDiscordActivityName(it) },
                            label = { Text(stringResource("discord_activity_name")) },
                            placeholder = { Text("AniTail Music") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = detailsTemplate,
                            onValueChange = { preferences.setDiscordDetailsTemplate(it) },
                            label = { Text(stringResource("discord_details")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = stateTemplate,
                            onValueChange = { preferences.setDiscordStateTemplate(it) },
                            label = { Text(stringResource("discord_state")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("{song.name}", "{artist.name}", "{album.name}", "{song.id}").forEach { token ->
                                SuggestionChip(
                                    onClick = { preferences.setDiscordDetailsTemplate("$detailsTemplate $token".trim()) },
                                    label = { Text(token, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }

                AndroidPreferenceGroupTitle(title = stringResource("discord_buttons"))

                DiscordButtonConfigCard(
                    title = stringResource("discord_button_1"),
                    enabled = button1Enabled,
                    onEnabledChange = { preferences.setDiscordButton1Enabled(it) },
                    label = button1Label,
                    onLabelChange = { preferences.setDiscordButton1Label(it) },
                    url = button1Url,
                    onUrlChange = { preferences.setDiscordButton1Url(it) },
                )

                DiscordButtonConfigCard(
                    title = stringResource("discord_button_2"),
                    enabled = button2Enabled,
                    onEnabledChange = { preferences.setDiscordButton2Enabled(it) },
                    label = button2Label,
                    onLabelChange = { preferences.setDiscordButton2Label(it) },
                    url = button2Url,
                    onUrlChange = { preferences.setDiscordButton2Url(it) },
                )
            }
        }

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
                                    text = authError ?: stringResource("discord_status_error"),
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

                if (discordToken.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (discordAvatarUrl.isNotBlank()) {
                            ExpressiveFramedAvatar(
                                url = discordAvatarUrl,
                                contentDescription = null,
                                shapeKey = discordUsername.ifBlank { discordName },
                                ringWidth = 1.5.dp,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = when {
                                discordName.isNotBlank() && discordUsername.isNotBlank() -> "$discordName (@$discordUsername)"
                                discordUsername.isNotBlank() -> "@$discordUsername"
                                else -> stringResource("discord_status_connected")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                preferences.setDiscordToken("")
                                preferences.setDiscordRefreshToken("")
                                preferences.setDiscordName("")
                                preferences.setDiscordUsername("")
                                preferences.setDiscordAvatarUrl("")
                                testResult = null
                            },
                        ) {
                            Text(stringResource("logout"))
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
                } else {
                    Button(
                        onClick = { startDiscordLogin() },
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = IconAssets.discord(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource("login"))
                    }
                }
            }
        }

        AndroidPreferenceGroupTitle(title = stringResource("preview"))
        DiscordPreviewCard(
            item = previewItem,
            positionMs = previewPositionMs,
            isPlaying = isPreviewPlaying,
            advancedMode = advancedMode,
            activityType = activityType,
            activityName = activityName,
            detailsTemplate = detailsTemplate,
            stateTemplate = stateTemplate,
            button1Enabled = button1Enabled,
            button1Label = button1Label,
            button1Url = button1Url,
            button2Enabled = button2Enabled,
            button2Label = button2Label,
            button2Url = button2Url,
        )
    }
}

@Composable
private fun DiscordButtonConfigCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource("discord_button_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource("discord_button_url")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DiscordPreviewCard(
    item: LibraryItem?,
    positionMs: Long,
    isPlaying: Boolean,
    advancedMode: Boolean,
    activityType: Int,
    activityName: String,
    detailsTemplate: String,
    stateTemplate: String,
    button1Enabled: Boolean,
    button1Label: String,
    button1Url: String,
    button2Enabled: Boolean,
    button2Label: String,
    button2Url: String,
) {
    val rawTitle = item?.title ?: "Song Title"
    val rawArtist = item?.artist ?: "Artist"
    val rawId = item?.id.orEmpty()
    fun render(tpl: String) = tpl
        .replace("{song.name}", rawTitle)
        .replace("{artist.name}", rawArtist)
        .replace("{album.name}", "Album")
        .replace("{song.id}", rawId)

    val headerPrefix = when (if (advancedMode) activityType else 2) {
        0 -> stringResource("discord_activity_playing")
        3 -> stringResource("discord_activity_watching")
        5 -> stringResource("discord_activity_competing")
        else -> stringResource("discord_activity_listening")
    }
    val headerName = if (advancedMode && activityName.isNotBlank()) activityName else "AniTail"
    val previewDetails = if (item != null && advancedMode) render(detailsTemplate.ifBlank { "{song.name}" }) else rawTitle
    val previewState = if (item != null && advancedMode) render(stateTemplate.ifBlank { "{artist.name}" }) else rawArtist
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
                text = "$headerPrefix $headerName",
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
                        text = previewDetails,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = previewState,
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

            val previewBtn1Label = if (advancedMode) {
                if (button1Enabled) render(button1Label).takeIf { it.isNotBlank() } else null
            } else {
                "Listen on YouTube Music"
            }
            val previewBtn1Url = if (advancedMode) render(button1Url) else "https://music.youtube.com/watch?v=$rawId"

            if (previewBtn1Label != null) {
                OutlinedButton(
                    enabled = item != null,
                    onClick = {
                        if (previewBtn1Url.startsWith("http")) {
                            val _ = runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(previewBtn1Url))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(previewBtn1Label)
                }
            }
            if (!advancedMode || button2Enabled) {
                val previewBtn2Label = if (advancedMode) render(button2Label).ifBlank { "Join our discord" } else "Join our discord"
                val previewBtn2Url = if (advancedMode) render(button2Url) else "https://discord.gg/H8x3yNbc67"
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        if (previewBtn2Url.startsWith("http")) {
                            val _ = runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(previewBtn2Url))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(previewBtn2Label)
                }
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
