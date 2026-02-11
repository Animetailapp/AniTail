package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.pluralStringResource
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.AudioQuality
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.NotificationButtonTypePreference

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
    val audioOffload by preferences.audioOffload.collectAsState()
    val autoLoadMore by preferences.autoLoadMore.collectAsState()
    val similarContentEnabled by preferences.similarContentEnabled.collectAsState()
    val autoSkipNextOnError by preferences.autoSkipNextOnError.collectAsState()
    val stopMusicOnTaskClear by preferences.stopMusicOnTaskClear.collectAsState()
    val autoDownloadOnLike by preferences.autoDownloadOnLike.collectAsState()
    val autoDownloadLyrics by preferences.autoDownloadLyrics.collectAsState()
    val enableCast by preferences.enableCast.collectAsState()
    val notificationButtonType by preferences.notificationButtonType.collectAsState()

    val autoStartRadio by preferences.autoStartRadio.collectAsState()
    val showLyrics by preferences.showLyrics.collectAsState()
    val romanizeLyrics by preferences.romanizeLyrics.collectAsState()

    val castSupported = false

    SettingsSubScreen(
        title = stringResource("player_and_audio"),
        onBack = onBack,
    ) {
        val audioQualityOptions = AudioQuality.entries.map { quality ->
            stringResource(quality.labelKey)
        }
        val audioQualitySubtitle = stringResource(audioQuality.labelKey)

        SettingsSectionTitle(title = stringResource("player"))

        SettingsDropdown(
            title = stringResource("audio_quality"),
            subtitle = audioQualitySubtitle,
            options = audioQualityOptions,
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onSelect = { index ->
                preferences.setAudioQuality(AudioQuality.entries[index])
            },
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

        SettingsSwitch(
            title = stringResource("audio_normalization"),
            subtitle = "",
            checked = normalizeAudio,
            onCheckedChange = { preferences.setNormalizeAudio(it) },
        )

        // Desktop extra kept for current feature parity
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

        SettingsSectionTitle(title = stringResource("queue"))

        SettingsSwitch(
            title = stringResource("audio_offload"),
            subtitle = stringResource("audio_offload_description"),
            checked = audioOffload,
            onCheckedChange = { preferences.setAudioOffload(it) },
        )

        SettingsSwitch(
            title = stringResource("persistent_queue"),
            subtitle = stringResource("persistent_queue_desc"),
            checked = persistentQueue,
            onCheckedChange = { preferences.setPersistentQueue(it) },
        )

        SettingsSwitch(
            title = stringResource("auto_load_more"),
            subtitle = stringResource("auto_load_more_desc"),
            checked = autoLoadMore,
            onCheckedChange = { preferences.setAutoLoadMore(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_similar_content"),
            subtitle = stringResource("similar_content_desc"),
            checked = similarContentEnabled,
            onCheckedChange = { preferences.setSimilarContentEnabled(it) },
        )

        SettingsSwitch(
            title = stringResource("auto_skip_next_on_error"),
            subtitle = stringResource("auto_skip_next_on_error_desc"),
            checked = autoSkipNextOnError,
            onCheckedChange = { preferences.setAutoSkipNextOnError(it) },
        )

        // Desktop extra kept for current feature parity
        SettingsSwitch(
            title = stringResource("auto_start_radio"),
            subtitle = stringResource("auto_start_radio_desc"),
            checked = autoStartRadio,
            onCheckedChange = { preferences.setAutoStartRadio(it) },
        )

        SettingsSectionTitle(title = stringResource("misc"))

        SettingsSwitch(
            title = stringResource("stop_music_on_task_clear"),
            subtitle = "",
            checked = stopMusicOnTaskClear,
            onCheckedChange = { preferences.setStopMusicOnTaskClear(it) },
        )

        SettingsSwitch(
            title = stringResource("auto_download_on_like"),
            subtitle = stringResource("auto_download_on_like_desc"),
            checked = autoDownloadOnLike,
            onCheckedChange = { preferences.setAutoDownloadOnLike(it) },
        )

        SettingsSwitch(
            title = stringResource("auto_download_lyrics"),
            subtitle = stringResource("auto_download_lyrics_desc"),
            checked = autoDownloadLyrics,
            onCheckedChange = { preferences.setAutoDownloadLyrics(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_cast"),
            subtitle = if (castSupported) {
                stringResource("enable_cast_desc")
            } else {
                stringResource("cast_requires_google_play_services")
            },
            checked = enableCast && castSupported,
            onCheckedChange = { enabled ->
                if (castSupported) {
                    preferences.setEnableCast(enabled)
                }
            },
            enabled = castSupported,
        )

        SettingsSectionTitle(title = stringResource("notification"))
        val notificationOptions = NotificationButtonTypePreference.entries.map { type ->
            when (type) {
                NotificationButtonTypePreference.CLOSE -> stringResource("notification_button_close")
                NotificationButtonTypePreference.LIKE -> stringResource("notification_button_like")
            }
        }
        SettingsDropdown(
            title = stringResource("notification_button_type"),
            subtitle = when (notificationButtonType) {
                NotificationButtonTypePreference.CLOSE -> stringResource("notification_button_close")
                NotificationButtonTypePreference.LIKE -> stringResource("notification_button_like")
            },
            options = notificationOptions,
            selectedIndex = NotificationButtonTypePreference.entries.indexOf(notificationButtonType),
            onSelect = { index ->
                preferences.setNotificationButtonType(NotificationButtonTypePreference.entries[index])
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Desktop extras currently used by player UI
        SettingsSectionTitle(title = stringResource("lyrics"))
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
