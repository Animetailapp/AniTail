package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.auth.AuthCredentials
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.security.DesktopPaths
import com.anitail.desktop.storage.BackupFrequency
import com.anitail.desktop.storage.DesktopAutoBackupService
import com.anitail.desktop.storage.DesktopBackupRestoreService
import com.anitail.desktop.storage.DesktopGoogleDriveSyncService
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import javax.swing.JFileChooser

@Composable
internal fun AutoBackupSettingsScreen(
    preferences: DesktopPreferences,
    authService: DesktopAuthService,
    onAuthChanged: (AuthCredentials?) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backupService = remember(authService, preferences) {
        DesktopBackupRestoreService(
            preferences = preferences,
            authService = authService,
        )
    }

    val autoBackupEnabled by preferences.autoBackupEnabled.collectAsState()
    val frequencyHours by preferences.autoBackupFrequencyHours.collectAsState()
    val keepCount by preferences.autoBackupKeepCount.collectAsState()
    val useCustomLocation by preferences.autoBackupUseCustomLocation.collectAsState()
    val customLocation by preferences.autoBackupCustomLocation.collectAsState()
    val driveEmail by DesktopGoogleDriveSyncService.signedInEmail.collectAsState()

    var runningNow by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var diagnosticsText by remember { mutableStateOf<String?>(null) }
    var driveOperationRunning by remember { mutableStateOf(false) }
    var driveConfigured by remember { mutableStateOf(false) }

    val backupCreateSuccessLabel = stringResource("backup_create_success")
    val backupFailedLabel = stringResource("backup_failed")
    val notSupportedLabel = stringResource("disable_screenshot_not_supported_desktop")
    val pleaseWaitLabel = stringResource("please_wait")
    val syncNowLabel = "Sync now (Smart Merge)"
    val signedInAsPattern = stringResource("signed_in_as", "%s")

    val frequency = remember(frequencyHours) { BackupFrequency.fromHours(frequencyHours) }
    val frequencyOptions = remember {
        listOf(
            BackupFrequency.ONE_HOUR,
            BackupFrequency.THREE_HOURS,
            BackupFrequency.SIX_HOURS,
            BackupFrequency.DAILY,
            BackupFrequency.WEEKLY,
        )
    }

    LaunchedEffect(Unit) {
        driveConfigured = DesktopGoogleDriveSyncService.isConfigured()
        DesktopGoogleDriveSyncService.trySilentRestore()
    }

    SettingsSubScreen(
        title = stringResource("auto_backup_settings"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("auto_backup"))
        SettingsSwitch(
            title = stringResource("auto_backup"),
            subtitle = stringResource("auto_backup_settings_desc"),
            checked = autoBackupEnabled,
            onCheckedChange = { enabled ->
                preferences.setAutoBackupEnabled(enabled)
            },
        )

        SettingsDropdown(
            title = stringResource("backup_frequency"),
            subtitle = frequency.displayName(),
            options = frequencyOptions.map { it.displayName() },
            selectedIndex = frequencyOptions.indexOf(frequency).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setAutoBackupFrequencyHours(frequencyOptions[index].hours)
            },
            enabled = autoBackupEnabled,
        )

        SettingsSlider(
            title = stringResource("backups_to_keep"),
            subtitle = keepCount.toString(),
            value = keepCount.toFloat(),
            valueRange = 1f..20f,
            steps = 18,
            onValueChange = { value ->
                preferences.setAutoBackupKeepCount(value.toInt())
            },
        )

        SettingsSectionTitle(title = stringResource("auto_backup_custom_location"))
        SettingsSwitch(
            title = stringResource("backup_custom_location"),
            subtitle = stringResource("auto_backup_custom_location_desc"),
            checked = useCustomLocation,
            onCheckedChange = { enabled ->
                preferences.setAutoBackupUseCustomLocation(enabled)
            },
            enabled = autoBackupEnabled,
        )

        if (useCustomLocation) {
            SettingsButton(
                title = stringResource("auto_backup_custom_location"),
                subtitle = if (customLocation.isNotBlank()) customLocation else "Select folder",
                onClick = {
                    val selected = chooseBackupDirectory() ?: return@SettingsButton
                    preferences.setAutoBackupCustomLocation(selected.absolutePath)
                },
                icon = IconAssets.storage(),
            )
        }

        val defaultLocation = DesktopPaths.autoBackupDir().toAbsolutePath().toString()
        SettingsInfoItem(
            title = stringResource("backups_location_info"),
            value = if (useCustomLocation && customLocation.isNotBlank()) customLocation else defaultLocation,
        )

        SettingsSectionTitle(title = stringResource("google_drive_backup"))
        if (!driveConfigured) {
            SettingsInfoItem(
                title = stringResource("google_drive_backup"),
                value = "Client secret missing (set ANITAIL_GOOGLE_CLIENT_SECRET or place client_secret_*.json in project root)",
            )
        } else if (driveEmail == null) {
            SettingsButton(
                title = stringResource("sign_in_with_google"),
                subtitle = stringResource("google_drive_backup_desc"),
                onClick = {
                    if (driveOperationRunning) return@SettingsButton
                    scope.launch {
                        driveOperationRunning = true
                        errorMessage = null
                        successMessage = null
                        DesktopGoogleDriveSyncService.signIn()
                            .onSuccess {
                                successMessage = if (it.isNullOrBlank()) {
                                    "Google Drive connected"
                                } else {
                                    signedInAsPattern.replace("%s", it)
                                }
                            }
                            .onFailure { error ->
                                errorMessage = error.message.orEmpty()
                            }
                        driveOperationRunning = false
                    }
                },
                icon = IconAssets.account(),
            )
        } else {
            SettingsInfoItem(
                title = stringResource("signed_in_as", driveEmail ?: ""),
                value = stringResource("google_drive_backup_desc"),
            )

            SettingsButton(
                title = syncNowLabel,
                subtitle = "Download latest backup, apply locally, and upload fresh state",
                onClick = {
                    if (driveOperationRunning) return@SettingsButton
                    scope.launch {
                        driveOperationRunning = true
                        errorMessage = null
                        successMessage = null
                        runCatching {
                            withContext(Dispatchers.IO) {
                                backupService.syncWithDriveSmartMerge(
                                    downloadLatestBackup = { path ->
                                        DesktopGoogleDriveSyncService.downloadLatestBackup(path)
                                    },
                                    uploadBackup = { path ->
                                        DesktopGoogleDriveSyncService.uploadBackup(path)
                                    },
                                )
                            }
                        }.onSuccess { report ->
                            onAuthChanged(authService.credentials)
                            successMessage = if (report.hadRemoteBackup) {
                                "Sincronización completada"
                            } else {
                                "Copia inicial subida"
                            }
                        }.onFailure { error ->
                            errorMessage = error.message.orEmpty()
                        }
                        driveOperationRunning = false
                    }
                },
                icon = IconAssets.refresh(),
            )

            SettingsButton(
                title = stringResource("backup_to_drive_now"),
                subtitle = stringResource("google_drive_backup_desc"),
                onClick = {
                    if (driveOperationRunning) return@SettingsButton
                    scope.launch {
                        driveOperationRunning = true
                        errorMessage = null
                        successMessage = null
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val tempUpload = Files.createTempFile("anitail_drive_upload_", ".backup")
                                backupService.backupTo(tempUpload)
                                val uploadedId = DesktopGoogleDriveSyncService.uploadBackup(tempUpload).getOrThrow()
                                Files.deleteIfExists(tempUpload)
                                uploadedId
                            }
                        }.onSuccess {
                            successMessage = "Backup uploaded to Drive"
                        }.onFailure { error ->
                            errorMessage = error.message.orEmpty()
                        }
                        driveOperationRunning = false
                    }
                },
                icon = IconAssets.download(),
            )

            SettingsButton(
                title = stringResource("restore_from_drive"),
                subtitle = stringResource("restore_description"),
                onClick = {
                    if (driveOperationRunning) return@SettingsButton
                    scope.launch {
                        driveOperationRunning = true
                        errorMessage = null
                        successMessage = null
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val tempDownload = Files.createTempFile("anitail_drive_restore_", ".backup")
                                val downloadedPath = DesktopGoogleDriveSyncService.downloadLatestBackup(tempDownload).getOrThrow()
                                backupService.restoreFrom(downloadedPath)
                                Files.deleteIfExists(tempDownload)
                            }
                        }.onSuccess {
                            onAuthChanged(authService.credentials)
                            successMessage = "Backup restored from Drive"
                        }.onFailure { error ->
                            errorMessage = error.message.orEmpty()
                        }
                        driveOperationRunning = false
                    }
                },
                icon = IconAssets.windowRestore(),
            )

            SettingsButton(
                title = stringResource("sign_out_google"),
                subtitle = stringResource("google_drive_backup_desc"),
                onClick = {
                    if (driveOperationRunning) return@SettingsButton
                    scope.launch {
                        driveOperationRunning = true
                        DesktopGoogleDriveSyncService.signOut()
                        successMessage = "Google Drive disconnected"
                        driveOperationRunning = false
                    }
                },
                icon = IconAssets.logout(),
            )
        }

        SettingsButton(
            title = stringResource("backup_diagnostics"),
            subtitle = "Check backup/drive configuration and storage status",
            onClick = {
                scope.launch {
                    diagnosticsText = withContext(Dispatchers.IO) {
                        buildBackupDiagnostics(
                            preferences = preferences,
                            driveConfigured = DesktopGoogleDriveSyncService.isConfigured(),
                            driveEmail = DesktopGoogleDriveSyncService.signedInEmail.value,
                        )
                    }
                }
            },
            icon = IconAssets.info(),
        )

        SettingsButton(
            title = stringResource("create_backup_now"),
            subtitle = stringResource("backup_scheduled"),
            onClick = {
                if (!autoBackupEnabled || runningNow) return@SettingsButton
                scope.launch {
                    runningNow = true
                    errorMessage = null
                    successMessage = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            DesktopAutoBackupService.createBackupNow(
                                preferences = preferences,
                                authService = authService,
                            )
                        }
                    }.onSuccess { result ->
                        successMessage = "$backupCreateSuccessLabel: ${result.file.fileName}"
                    }.onFailure { error ->
                        errorMessage = "$backupFailedLabel: ${error.message.orEmpty()}"
                    }
                    runningNow = false
                }
            },
            icon = if (runningNow) IconAssets.refresh() else IconAssets.download(),
        )

        if (!driveConfigured) {
            SettingsInfoItem(
                title = stringResource("google_drive_backup"),
                value = "$notSupportedLabel (OAuth not configured)",
            )
        }
    }

    if (runningNow || driveOperationRunning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource("auto_backup")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(pleaseWaitLabel)
                }
            },
            confirmButton = {},
        )
    }

    if (diagnosticsText != null) {
        AlertDialog(
            onDismissRequest = { diagnosticsText = null },
            title = { Text(stringResource("backup_diagnostics")) },
            text = { Text(diagnosticsText.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { diagnosticsText = null }) {
                    Text(stringResource("ok"))
                }
            },
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource("ok"))
                }
            },
        )
    }

    if (successMessage != null) {
        AlertDialog(
            onDismissRequest = { successMessage = null },
            title = { Text("Success") },
            text = {
                Text(
                    text = successMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { successMessage = null }) {
                    Text(stringResource("ok"))
                }
            },
        )
    }
}

private fun chooseBackupDirectory(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select backup folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile
}

private fun buildBackupDiagnostics(
    preferences: DesktopPreferences,
    driveConfigured: Boolean,
    driveEmail: String?,
): String {
    val lines = mutableListOf<String>()

    val defaultAutoBackupPath = DesktopPaths.autoBackupDir()
    val effectiveBackupPath = if (preferences.autoBackupUseCustomLocation.value &&
        preferences.autoBackupCustomLocation.value.isNotBlank()
    ) {
        Path.of(preferences.autoBackupCustomLocation.value)
    } else {
        defaultAutoBackupPath
    }

    val backupFilesCount = if (Files.exists(effectiveBackupPath)) {
        Files.list(effectiveBackupPath).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { path ->
                    val name = path.fileName.toString().lowercase()
                    name.endsWith(".backup") || name.endsWith(".zip")
                }
                .count()
                .toInt()
        }
    } else {
        0
    }

    lines += "Auto backup enabled: ${preferences.autoBackupEnabled.value}"
    lines += "Frequency (hours): ${preferences.autoBackupFrequencyHours.value}"
    lines += "Keep count: ${preferences.autoBackupKeepCount.value}"
    lines += "Use custom location: ${preferences.autoBackupUseCustomLocation.value}"
    lines += "Effective backup folder: ${effectiveBackupPath.toAbsolutePath()}"
    lines += "Backup files found: $backupFilesCount"
    lines += "Last auto-backup error: ${DesktopAutoBackupService.lastErrorMessage.ifBlank { "none" }}"

    lines += "Google Drive OAuth configured: $driveConfigured"
    lines += "Google Drive signed in: ${!driveEmail.isNullOrBlank()}"
    lines += "Google Drive account: ${driveEmail ?: "-"}"

    val tokenStorePath = DesktopPaths.appDataDir().resolve("google_tokens")
    lines += "Token store exists: ${Files.exists(tokenStorePath)}"

    return lines.joinToString("\n")
}
