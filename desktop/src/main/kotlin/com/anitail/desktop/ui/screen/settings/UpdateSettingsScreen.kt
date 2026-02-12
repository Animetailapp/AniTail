package com.anitail.desktop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.LocalStrings
import com.anitail.desktop.i18n.StringResolver
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.UpdateCheckFrequency
import com.anitail.desktop.update.DesktopUpdateInstaller
import com.anitail.desktop.update.DesktopUpdater
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.launch

@Composable
internal fun UpdateSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    val autoUpdateEnabled by preferences.autoUpdateEnabled.collectAsState()
    val frequency by preferences.autoUpdateCheckFrequency.collectAsState()
    val latestVersionName by preferences.latestVersionName.collectAsState()
    val latestVersionDownloadUrl by preferences.latestVersionDownloadUrl.collectAsState()
    val latestVersionReleaseNotes by preferences.latestVersionReleaseNotes.collectAsState()
    val latestVersionReleasePageUrl by preferences.latestVersionReleasePageUrl.collectAsState()
    val currentVersionName = remember { DesktopUpdater.currentVersionName() }

    val isUpdateAvailable = remember(latestVersionName, currentVersionName) {
        latestVersionName.isNotBlank() && DesktopUpdater.isVersionNewer(latestVersionName, currentVersionName)
    }
    val releaseNotesLines = remember(latestVersionReleaseNotes) {
        parseReleaseNotes(latestVersionReleaseNotes)
    }

    var showFrequencyDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val releasePageUrl = if (latestVersionReleasePageUrl.isNotBlank()) {
        latestVersionReleasePageUrl
    } else {
        "https://github.com/Animetailapp/Anitail-Desktop/releases/latest"
    }

    LaunchedEffect(Unit) {
        if (latestVersionName.isBlank()) {
            DesktopUpdater.checkForUpdates(preferences = preferences, force = false)
        }
    }

    SettingsSubScreen(
        title = stringResource("update_settings"),
        onBack = onBack,
    ) {
        UpdateStatusCard(
            currentVersionName = currentVersionName,
            latestVersionName = latestVersionName,
            isUpdateAvailable = isUpdateAvailable,
        )

        if (isUpdateAvailable) {
            SettingsButton(
                title = stringResource("download_update"),
                subtitle = if (isInstalling) {
                    stringResource("update_notification_description")
                } else {
                    stringResource("new_version_available_description", latestVersionName)
                },
                onClick = {
                    if (isInstalling) return@SettingsButton
                    isInstalling = true
                    feedbackMessage = null
                    val targetUrl = if (latestVersionDownloadUrl.isNotBlank()) {
                        latestVersionDownloadUrl
                    } else {
                        releasePageUrl
                    }
                    scope.launch {
                        val result = DesktopUpdateInstaller.downloadAndInstall(targetUrl)
                        feedbackMessage = if (result.isSuccess) {
                            strings.get("update_notification_description")
                        } else {
                            strings.get("update_check_failed")
                        }
                        isInstalling = false
                    }
                },
                icon = IconAssets.download(),
            )
            SettingsButton(
                title = stringResource("release_notes"),
                subtitle = releasePageUrl,
                onClick = {
                    DesktopUpdater.openDownloadUrl(releasePageUrl)
                },
                icon = IconAssets.openInNew(),
            )
            if (releaseNotesLines.isNotEmpty()) {
                ReleaseNotesCard(lines = releaseNotesLines)
            }
        }

        SettingsSectionTitle(title = stringResource("options"))
        SettingsSwitch(
            title = stringResource("auto_update_enabled"),
            subtitle = stringResource("auto_update_enabled_description"),
            checked = autoUpdateEnabled,
            onCheckedChange = { preferences.setAutoUpdateEnabled(it) },
        )
        SettingsButton(
            title = stringResource("update_check_frequency"),
            subtitle = frequency.toLabel(strings),
            onClick = {
                if (autoUpdateEnabled) {
                    showFrequencyDialog = true
                }
            },
            icon = IconAssets.tune(),
        )

        SettingsSectionTitle(title = stringResource("actions"))
        SettingsButton(
            title = stringResource("check_for_updates_now"),
            subtitle = if (isChecking) "..." else stringResource("check_for_updates_now"),
            onClick = {
                if (isChecking) return@SettingsButton
                isChecking = true
                feedbackMessage = null
                scope.launch {
                    val result = DesktopUpdater.checkForUpdates(preferences = preferences, force = true)
                    if (result.isSuccess) {
                        val report = result.getOrThrow()
                        feedbackMessage = if (report.isUpdateAvailable) {
                            strings.get("new_version_available_toast", report.releaseInfo.versionName)
                        } else {
                            strings.get("app_up_to_date")
                        }
                    } else {
                        val details = result.exceptionOrNull()?.message.orEmpty().trim()
                        feedbackMessage = if (details.isNotBlank()) {
                            "${strings.get("update_check_failed")}: $details"
                        } else {
                            strings.get("update_check_failed")
                        }
                    }
                    isChecking = false
                }
            },
            icon = IconAssets.update(),
        )

        if (!feedbackMessage.isNullOrBlank()) {
            Text(
                text = feedbackMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text(stringResource("update_check_frequency")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UpdateCheckFrequency.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    preferences.setAutoUpdateCheckFrequency(option)
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == frequency,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.toLabel(strings),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrequencyDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }
}

@Composable
private fun ReleaseNotesCard(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource("release_notes"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            lines.take(8).forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    currentVersionName: String,
    latestVersionName: String,
    isUpdateAvailable: Boolean,
) {
    val tintColor = if (isUpdateAvailable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isUpdateAvailable) IconAssets.info() else IconAssets.check(),
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isUpdateAvailable) {
                        stringResource("new_version_available")
                    } else {
                        stringResource("app_up_to_date")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource("current_version", currentVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (isUpdateAvailable && latestVersionName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v$latestVersionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = tintColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun UpdateCheckFrequency.toLabel(strings: StringResolver): String {
    return when (this) {
        UpdateCheckFrequency.DAILY -> strings.get("daily")
        UpdateCheckFrequency.WEEKLY -> strings.get("weekly")
        UpdateCheckFrequency.MONTHLY -> strings.get("monthly")
        UpdateCheckFrequency.NEVER -> strings.get("never")
    }
}

private fun parseReleaseNotes(releaseNotesRaw: String): List<String> {
    if (releaseNotesRaw.isBlank()) return emptyList()
    return releaseNotesRaw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            line
                .removePrefix("-")
                .removePrefix("*")
                .trim()
        }
        .filter { it.isNotBlank() }
        .toList()
}
