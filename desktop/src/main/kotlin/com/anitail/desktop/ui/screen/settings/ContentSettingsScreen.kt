package com.anitail.desktop.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anitail.desktop.i18n.CountryCodeToName
import com.anitail.desktop.i18n.LanguageCodeToName
import com.anitail.desktop.i18n.SYSTEM_DEFAULT
import com.anitail.desktop.i18n.appLanguageOptions
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PreferredLyricsProvider
import com.anitail.desktop.storage.QuickPicks

@Composable
internal fun ContentSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val appLanguage by preferences.appLanguage.collectAsState()
    val hideExplicit by preferences.hideExplicit.collectAsState()
    val quickPicks by preferences.quickPicks.collectAsState()
    val enableBetterLyrics by preferences.enableBetterLyrics.collectAsState()
    val enableSimpMusic by preferences.enableSimpMusic.collectAsState()
    val enableLrcLib by preferences.enableLrcLib.collectAsState()
    val enableKuGou by preferences.enableKuGou.collectAsState()
    val preferredLyricsProvider by preferences.preferredLyricsProvider.collectAsState()

    val languageCodes = appLanguageOptions()
    val countryCodes = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()
    val systemDefaultLabel = stringResource("system_default")
    val languageLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else LanguageCodeToName[code] ?: code
    }
    val countryLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else CountryCodeToName[code] ?: code
    }

    SettingsSubScreen(
        title = stringResource("content"),
        onBack = onBack,
    ) {
        SettingsDropdown(
            title = stringResource("app_language"),
            subtitle = languageLabel(appLanguage),
            options = languageCodes.map(languageLabel),
            selectedIndex = languageCodes.indexOf(appLanguage).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setAppLanguage(languageCodes[index])
            },
        )

        SettingsDropdown(
            title = stringResource("content_language"),
            subtitle = languageLabel(contentLanguage),
            options = languageCodes.map(languageLabel),
            selectedIndex = languageCodes.indexOf(contentLanguage).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentLanguage(languageCodes[index])
            },
        )

        SettingsDropdown(
            title = stringResource("content_country"),
            subtitle = countryLabel(contentCountry),
            options = countryCodes.map(countryLabel),
            selectedIndex = countryCodes.indexOf(contentCountry).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setContentCountry(countryCodes[index])
            },
        )

        SettingsSwitch(
            title = stringResource("hide_explicit"),
            subtitle = stringResource("hide_explicit_desc"),
            checked = hideExplicit,
            onCheckedChange = { preferences.setHideExplicit(it) },
        )

        SettingsSectionTitle(title = stringResource("lyrics"))

        SettingsSwitch(
            title = stringResource("enable_betterlyrics"),
            subtitle = "",
            checked = enableBetterLyrics,
            onCheckedChange = { preferences.setEnableBetterLyrics(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_simpmusic"),
            subtitle = "",
            checked = enableSimpMusic,
            onCheckedChange = { preferences.setEnableSimpMusic(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_lrclib"),
            subtitle = "",
            checked = enableLrcLib,
            onCheckedChange = { preferences.setEnableLrcLib(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_kugou"),
            subtitle = "",
            checked = enableKuGou,
            onCheckedChange = { preferences.setEnableKuGou(it) },
        )

        val providerOptions = listOf(
            PreferredLyricsProvider.LRCLIB,
            PreferredLyricsProvider.KUGOU,
            PreferredLyricsProvider.BETTER_LYRICS,
            PreferredLyricsProvider.SIMPMUSIC,
        )
        val betterLyricsLabel = stringResource("lyrics_provider_betterlyrics")
        val simpMusicLabel = stringResource("lyrics_provider_simpmusic")
        val providerLabel: (PreferredLyricsProvider) -> String = { provider ->
            when (provider) {
                PreferredLyricsProvider.LRCLIB -> "LrcLib"
                PreferredLyricsProvider.KUGOU -> "KuGou"
                PreferredLyricsProvider.BETTER_LYRICS -> betterLyricsLabel
                PreferredLyricsProvider.SIMPMUSIC -> simpMusicLabel
            }
        }

        SettingsDropdown(
            title = stringResource("set_first_lyrics_provider"),
            subtitle = providerLabel(preferredLyricsProvider),
            options = providerOptions.map(providerLabel),
            selectedIndex = providerOptions.indexOf(preferredLyricsProvider).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setPreferredLyricsProvider(providerOptions[index])
            },
        )

        SettingsDropdown(
            title = stringResource("quick_picks"),
            subtitle = when (quickPicks) {
                QuickPicks.QUICK_PICKS -> stringResource("quick_picks")
                QuickPicks.LAST_LISTEN -> stringResource("last_song_listened")
            },
            options = listOf(stringResource("quick_picks"), stringResource("last_song_listened")),
            selectedIndex = if (quickPicks == QuickPicks.QUICK_PICKS) 0 else 1,
            onSelect = { index ->
                preferences.setQuickPicks(if (index == 0) QuickPicks.QUICK_PICKS else QuickPicks.LAST_LISTEN)
            },
        )
    }
}
