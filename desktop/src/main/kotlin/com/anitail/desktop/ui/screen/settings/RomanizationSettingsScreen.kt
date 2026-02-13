package com.anitail.desktop.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences

@Composable
internal fun RomanizationSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val lyricsRomanizeJapanese by preferences.lyricsRomanizeJapanese.collectAsState()
    val lyricsRomanizeKorean by preferences.lyricsRomanizeKorean.collectAsState()
    val lyricsRomanizeRussian by preferences.lyricsRomanizeRussian.collectAsState()
    val lyricsRomanizeUkrainian by preferences.lyricsRomanizeUkrainian.collectAsState()
    val lyricsRomanizeSerbian by preferences.lyricsRomanizeSerbian.collectAsState()
    val lyricsRomanizeBulgarian by preferences.lyricsRomanizeBulgarian.collectAsState()
    val lyricsRomanizeBelarusian by preferences.lyricsRomanizeBelarusian.collectAsState()
    val lyricsRomanizeKyrgyz by preferences.lyricsRomanizeKyrgyz.collectAsState()
    val lyricsRomanizeCyrillicByLine by preferences.lyricsRomanizeCyrillicByLine.collectAsState()
    var showLineByLineDialog by remember { mutableStateOf(false) }

    SettingsSubScreen(
        title = stringResource("lyrics_romanize_title"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("general"))

        SettingsSwitch(
            title = stringResource("lyrics_romanize_japanese"),
            subtitle = "",
            checked = lyricsRomanizeJapanese,
            onCheckedChange = { preferences.setLyricsRomanizeJapanese(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_korean"),
            subtitle = "",
            checked = lyricsRomanizeKorean,
            onCheckedChange = { preferences.setLyricsRomanizeKorean(it) },
        )

        SettingsSectionTitle(title = stringResource("lyrics_romanization_cyrillic"))

        SettingsSwitch(
            title = stringResource("lyrics_romanize_russian"),
            subtitle = "",
            checked = lyricsRomanizeRussian,
            onCheckedChange = { preferences.setLyricsRomanizeRussian(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_ukrainian"),
            subtitle = "",
            checked = lyricsRomanizeUkrainian,
            onCheckedChange = { preferences.setLyricsRomanizeUkrainian(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_serbian"),
            subtitle = "",
            checked = lyricsRomanizeSerbian,
            onCheckedChange = { preferences.setLyricsRomanizeSerbian(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_bulgarian"),
            subtitle = "",
            checked = lyricsRomanizeBulgarian,
            onCheckedChange = { preferences.setLyricsRomanizeBulgarian(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_belarusian"),
            subtitle = "",
            checked = lyricsRomanizeBelarusian,
            onCheckedChange = { preferences.setLyricsRomanizeBelarusian(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanize_kyrgyz"),
            subtitle = "",
            checked = lyricsRomanizeKyrgyz,
            onCheckedChange = { preferences.setLyricsRomanizeKyrgyz(it) },
        )

        SettingsSwitch(
            title = stringResource("line_by_line_option_title"),
            subtitle = stringResource("line_by_line_option_desc"),
            checked = lyricsRomanizeCyrillicByLine,
            onCheckedChange = { enabled ->
                if (enabled) {
                    showLineByLineDialog = true
                } else {
                    preferences.setLyricsRomanizeCyrillicByLine(false)
                }
            },
        )
    }

    if (showLineByLineDialog) {
        AlertDialog(
            onDismissRequest = { showLineByLineDialog = false },
            title = { Text(stringResource("line_by_line_dialog_title")) },
            text = { Text(stringResource("line_by_line_dialog_desc")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.setLyricsRomanizeCyrillicByLine(true)
                        showLineByLineDialog = false
                    },
                ) {
                    Text(stringResource("save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLineByLineDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }
}
