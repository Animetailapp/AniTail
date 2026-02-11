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
internal fun StorageSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val maxImageCacheSizeMB by preferences.maxImageCacheSizeMB.collectAsState()
    val maxSongCacheSizeMB by preferences.maxSongCacheSizeMB.collectAsState()
    val downloadAsMp3 by preferences.downloadAsMp3.collectAsState()

    SettingsSubScreen(
        title = stringResource("storage"),
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = stringResource("download_as_mp3"),
            subtitle = stringResource("download_as_mp3_desc"),
            checked = downloadAsMp3,
            onCheckedChange = { preferences.setDownloadAsMp3(it) },
        )

        SettingsSlider(
            title = stringResource("image_cache"),
            subtitle = "$maxImageCacheSizeMB MB",
            value = maxImageCacheSizeMB.toFloat(),
            valueRange = 100f..2000f,
            steps = 18,
            onValueChange = { preferences.setMaxImageCacheSizeMB(it.toInt()) },
        )

        SettingsSlider(
            title = stringResource("song_cache"),
            subtitle = "$maxSongCacheSizeMB MB",
            value = maxSongCacheSizeMB.toFloat(),
            valueRange = 500f..10000f,
            steps = 18,
            onValueChange = { preferences.setMaxSongCacheSizeMB(it.toInt()) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        var showClearCacheDialog by remember { mutableStateOf(false) }

        SettingsButton(
            title = stringResource("clear_cache"),
            subtitle = stringResource("clear_cache_desc"),
            onClick = { showClearCacheDialog = true },
        )

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource("clear_cache_confirm_title")) },
                text = { Text(stringResource("clear_cache_confirm_desc")) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearCacheDialog = false
                    }) {
                        Text(stringResource("clear_cache"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text(stringResource("cancel"))
                    }
                },
            )
        }
    }
}
