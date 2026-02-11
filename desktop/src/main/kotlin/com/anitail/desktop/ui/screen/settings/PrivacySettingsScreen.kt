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
import com.anitail.desktop.db.DesktopDatabase
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.ui.IconAssets
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySettingsScreen(
    preferences: DesktopPreferences,
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
    }
}
