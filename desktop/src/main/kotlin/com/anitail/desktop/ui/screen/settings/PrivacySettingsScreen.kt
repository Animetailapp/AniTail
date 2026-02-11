package com.anitail.desktop.ui.screen

import androidx.compose.material3.AlertDialog
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
import com.anitail.desktop.auth.DesktopAuthService
import com.anitail.desktop.auth.DesktopLastFmService
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.lyrics.DesktopLyricsService
import com.anitail.desktop.security.DesktopAppDataCleaner
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySettingsScreen(
    preferences: DesktopPreferences,
    authService: DesktopAuthService,
    onBack: () -> Unit,
) {
    val database = remember { DesktopDatabase.getInstance() }
    val scope = rememberCoroutineScope()
    val pauseListenHistory by preferences.pauseListenHistory.collectAsState()
    val pauseSearchHistory by preferences.pauseSearchHistory.collectAsState()
    val disableScreenshot by preferences.disableScreenshot.collectAsState()

    LaunchedEffect(disableScreenshot) {
        if (disableScreenshot) {
            preferences.setDisableScreenshot(false)
        }
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showClearSensitiveDialog by remember { mutableStateOf(false) }
    var showResetAppDialog by remember { mutableStateOf(false) }

    SettingsSubScreen(
        title = stringResource("privacy"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("listen_history"))

        SettingsSwitch(
            title = stringResource("pause_listen_history"),
            subtitle = "",
            checked = pauseListenHistory,
            onCheckedChange = { preferences.setPauseListenHistory(it) },
        )

        SettingsButton(
            title = stringResource("clear_listen_history"),
            subtitle = "",
            onClick = { showClearHistoryDialog = true },
            icon = IconAssets.deleteHistory(),
        )

        SettingsSectionTitle(title = stringResource("search_history"))

        SettingsSwitch(
            title = stringResource("pause_search_history"),
            subtitle = "",
            checked = pauseSearchHistory,
            onCheckedChange = { preferences.setPauseSearchHistory(it) },
        )

        SettingsButton(
            title = stringResource("clear_search_history"),
            subtitle = "",
            onClick = { showClearSearchDialog = true },
            icon = IconAssets.delete(),
        )

        SettingsSectionTitle(title = stringResource("security"))

        SettingsButton(
            title = stringResource("clear_sensitive_data"),
            subtitle = stringResource("clear_sensitive_data_description"),
            onClick = { showClearSensitiveDialog = true },
            icon = IconAssets.lock(),
        )

        SettingsButton(
            title = stringResource("reset_app_keep_downloads"),
            subtitle = stringResource("reset_app_keep_downloads_description"),
            onClick = { showResetAppDialog = true },
            icon = IconAssets.refresh(),
        )

        SettingsSectionTitle(title = stringResource("misc"))

        SettingsSwitch(
            title = stringResource("disable_screenshot"),
            subtitle = stringResource("disable_screenshot_not_supported_desktop"),
            checked = false,
            onCheckedChange = {},
            enabled = false,
        )

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text(stringResource("clear_listen_history")) },
                text = { Text(stringResource("clear_listen_history_confirm")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearHistoryDialog = false
                            scope.launch { database.clearEvents() }
                        },
                    ) {
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
                    TextButton(
                        onClick = {
                            showClearSearchDialog = false
                            scope.launch { database.clearSearchHistory() }
                        },
                    ) {
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

        if (showClearSensitiveDialog) {
            AlertDialog(
                onDismissRequest = { showClearSensitiveDialog = false },
                title = { Text(stringResource("clear_sensitive_data")) },
                text = { Text(stringResource("clear_sensitive_data_confirm")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearSensitiveDialog = false
                            scope.launch {
                                authService.logout()
                                preferences.clearSensitiveData()
                                DesktopLastFmService.logout()
                                DesktopLastFmService.clearPendingScrobbles()
                            }
                        },
                    ) {
                        Text(stringResource("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearSensitiveDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }

        if (showResetAppDialog) {
            AlertDialog(
                onDismissRequest = { showResetAppDialog = false },
                title = { Text(stringResource("reset_app_keep_downloads")) },
                text = { Text(stringResource("reset_app_keep_downloads_confirm")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetAppDialog = false
                            scope.launch {
                                authService.logout()
                                preferences.clearSensitiveData()
                                DesktopLastFmService.logout()
                                DesktopLastFmService.clearPendingScrobbles()
                                DesktopLyricsService.clearAllOverrides()
                                database.clearAllData()
                                DesktopAppDataCleaner.clearAppDataPreservingDownloads()
                                preferences.resetToDefaults(clearSensitive = true)
                            }
                        },
                    ) {
                        Text(stringResource("reset"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAppDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }
    }
}
