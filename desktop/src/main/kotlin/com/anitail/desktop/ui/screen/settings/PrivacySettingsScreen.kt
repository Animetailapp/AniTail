package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences

@Composable
internal fun PrivacySettingsScreen(
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
