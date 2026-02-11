package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DesktopPreferences

@Composable
internal fun PlayerSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val audioQuality by preferences.audioQuality.collectAsState()
    val normalizeAudio by preferences.normalizeAudio.collectAsState()
    val skipSilence by preferences.skipSilence.collectAsState()
    val crossfadeDuration by preferences.crossfadeDuration.collectAsState()
    val historyDuration by preferences.historyDuration.collectAsState()
    val persistentQueue by preferences.persistentQueue.collectAsState()
    val autoStartRadio by preferences.autoStartRadio.collectAsState()
    val showLyrics by preferences.showLyrics.collectAsState()
    val romanizeLyrics by preferences.romanizeLyrics.collectAsState()

    SettingsSubScreen(
        title = stringResource("player_and_audio"),
        onBack = onBack,
    ) {
        val audioQualityOptions = AudioQuality.entries.map { quality ->
            stringResource(quality.labelKey)
        }
        val audioQualitySubtitle = stringResource(audioQuality.labelKey)

        SettingsDropdown(
            title = stringResource("audio_quality"),
            subtitle = audioQualitySubtitle,
            options = audioQualityOptions,
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onSelect = { index ->
                preferences.setAudioQuality(AudioQuality.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("audio_normalization"),
            subtitle = "",
            checked = normalizeAudio,
            onCheckedChange = { preferences.setNormalizeAudio(it) },
        )

        SettingsSlider(
            title = stringResource("history_duration"),
            subtitle = if (historyDuration <= 0f) {
                stringResource("unlimited")
            } else {
                pluralStringResource("seconds", historyDuration.toInt(), historyDuration.toInt())
            },
            value = historyDuration.coerceIn(0f, 60f),
            valueRange = 0f..60f,
            steps = 59,
            onValueChange = { preferences.setHistoryDuration(it) },
        )

        SettingsSwitch(
            title = stringResource("skip_silence"),
            subtitle = "",
            checked = skipSilence,
            onCheckedChange = { preferences.setSkipSilence(it) },
        )

        SettingsSlider(
            title = stringResource("crossfade"),
            subtitle = if (crossfadeDuration == 0) {
                stringResource("disabled")
            } else {
                pluralStringResource("seconds", crossfadeDuration, crossfadeDuration)
            },
            value = crossfadeDuration.toFloat(),
            valueRange = 0f..12f,
            steps = 11,
            onValueChange = { preferences.setCrossfadeDuration(it.toInt()) },
        )

        SettingsSwitch(
            title = stringResource("persistent_queue"),
            subtitle = "",
            checked = persistentQueue,
            onCheckedChange = { preferences.setPersistentQueue(it) },
        )

        SettingsSwitch(
            title = stringResource("auto_start_radio"),
            subtitle = stringResource("auto_start_radio_desc"),
            checked = autoStartRadio,
            onCheckedChange = { preferences.setAutoStartRadio(it) },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            title = stringResource("lyrics"),
            subtitle = "",
            checked = showLyrics,
            onCheckedChange = { preferences.setShowLyrics(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_romanization"),
            subtitle = "",
            checked = romanizeLyrics,
            onCheckedChange = { preferences.setRomanizeLyrics(it) },
            enabled = showLyrics,
        )
    }
}
