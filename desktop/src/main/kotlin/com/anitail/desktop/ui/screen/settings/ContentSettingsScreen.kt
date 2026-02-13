package com.anitail.desktop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.CountryCodeToName
import com.anitail.desktop.i18n.LanguageCodeToName
import com.anitail.desktop.i18n.SYSTEM_DEFAULT
import com.anitail.desktop.i18n.appLanguageOptions
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.PreferredLyricsProvider
import com.anitail.desktop.storage.ProxyTypePreference
import com.anitail.desktop.storage.QuickPicks
import com.anitail.desktop.ui.IconAssets

@Composable
internal fun ContentSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
    onOpenRomanization: () -> Unit,
) {
    val contentLanguage by preferences.contentLanguage.collectAsState()
    val contentCountry by preferences.contentCountry.collectAsState()
    val appLanguage by preferences.appLanguage.collectAsState()
    val quickPicks by preferences.quickPicks.collectAsState()
    val topLength by preferences.topLength.collectAsState()
    val proxyEnabled by preferences.proxyEnabled.collectAsState()
    val proxyType by preferences.proxyType.collectAsState()
    val proxyUrl by preferences.proxyUrl.collectAsState()
    val proxyUsername by preferences.proxyUsername.collectAsState()
    val proxyPassword by preferences.proxyPassword.collectAsState()
    val enableBetterLyrics by preferences.enableBetterLyrics.collectAsState()
    val enableSimpMusic by preferences.enableSimpMusic.collectAsState()
    val enableLrcLib by preferences.enableLrcLib.collectAsState()
    val enableKuGou by preferences.enableKuGou.collectAsState()
    val preferredLyricsProvider by preferences.preferredLyricsProvider.collectAsState()
    val lyricsGlowEffect by preferences.lyricsGlowEffect.collectAsState()

    val languageCodes = appLanguageOptions()
    val countryCodes = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList()
    val systemDefaultLabel = stringResource("system_default")
    val languageLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else LanguageCodeToName[code] ?: code
    }
    val countryLabel: (String) -> String = { code ->
        if (code == SYSTEM_DEFAULT) systemDefaultLabel else CountryCodeToName[code] ?: code
    }
    val proxyTypeOptions = ProxyTypePreference.entries
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

    var showProxyConfigurationDialog by remember { mutableStateOf(false) }
    var topLengthInput by remember(topLength) { mutableStateOf(topLength) }

    if (showProxyConfigurationDialog) {
        var expandedProxyType by remember { mutableStateOf(false) }
        var tempProxyType by remember(proxyType) { mutableStateOf(proxyType) }
        var tempProxyUrl by remember(proxyUrl) { mutableStateOf(proxyUrl) }
        var tempProxyUsername by remember(proxyUsername) { mutableStateOf(proxyUsername) }
        var tempProxyPassword by remember(proxyPassword) { mutableStateOf(proxyPassword) }
        var authEnabled by remember(proxyUsername, proxyPassword) {
            mutableStateOf(proxyUsername.isNotBlank() || proxyPassword.isNotBlank())
        }

        AlertDialog(
            onDismissRequest = { showProxyConfigurationDialog = false },
            title = { Text(stringResource("config_proxy")) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = tempProxyType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource("proxy_type")) },
                        trailingIcon = {
                            TextButton(onClick = { expandedProxyType = true }) {
                                Text(stringResource("edit"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DropdownMenu(
                        expanded = expandedProxyType,
                        onDismissRequest = { expandedProxyType = false },
                    ) {
                        proxyTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    tempProxyType = option
                                    expandedProxyType = false
                                },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tempProxyUrl,
                        onValueChange = { tempProxyUrl = it },
                        label = { Text(stringResource("proxy_url")) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource("enable_authentication"))
                        Switch(
                            checked = authEnabled,
                            onCheckedChange = { enabled ->
                                authEnabled = enabled
                                if (!enabled) {
                                    tempProxyUsername = ""
                                    tempProxyPassword = ""
                                }
                            },
                        )
                    }

                    if (authEnabled) {
                        OutlinedTextField(
                            value = tempProxyUsername,
                            onValueChange = { tempProxyUsername = it },
                            label = { Text(stringResource("proxy_username")) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = tempProxyPassword,
                            onValueChange = { tempProxyPassword = it },
                            label = { Text(stringResource("proxy_password")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.setProxyType(tempProxyType)
                        preferences.setProxyUrl(tempProxyUrl)
                        preferences.setProxyUsername(if (authEnabled) tempProxyUsername else "")
                        preferences.setProxyPassword(if (authEnabled) tempProxyPassword else "")
                        showProxyConfigurationDialog = false
                    },
                ) {
                    Text(stringResource("save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProxyConfigurationDialog = false }) {
                    Text(stringResource("cancel"))
                }
            },
        )
    }

    SettingsSubScreen(
        title = stringResource("content"),
        onBack = onBack,
    ) {
        SettingsSectionTitle(title = stringResource("general"))

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

        SettingsSectionTitle(title = stringResource("app_language"))

        SettingsDropdown(
            title = stringResource("app_language"),
            subtitle = languageLabel(appLanguage),
            options = languageCodes.map(languageLabel),
            selectedIndex = languageCodes.indexOf(appLanguage).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setAppLanguage(languageCodes[index])
            },
        )

        SettingsSectionTitle(title = stringResource("proxy"))

        SettingsSwitch(
            title = stringResource("enable_proxy"),
            subtitle = "",
            checked = proxyEnabled,
            onCheckedChange = { preferences.setProxyEnabled(it) },
        )

        if (proxyEnabled) {
            SettingsButton(
                title = stringResource("config_proxy"),
                subtitle = "${proxyType.name} - ${proxyUrl.ifBlank { "host:port" }}",
                onClick = { showProxyConfigurationDialog = true },
                icon = IconAssets.settings(),
            )
        }

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

        SettingsDropdown(
            title = stringResource("set_first_lyrics_provider"),
            subtitle = providerLabel(preferredLyricsProvider),
            options = providerOptions.map(providerLabel),
            selectedIndex = providerOptions.indexOf(preferredLyricsProvider).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setPreferredLyricsProvider(providerOptions[index])
            },
        )

        SettingsSwitch(
            title = stringResource("lyrics_glow_effect"),
            subtitle = "",
            checked = lyricsGlowEffect,
            onCheckedChange = { preferences.setLyricsGlowEffect(it) },
        )

        SettingsButton(
            title = stringResource("lyrics_romanization"),
            subtitle = "",
            onClick = onOpenRomanization,
            icon = IconAssets.language(),
        )

        SettingsSectionTitle(title = stringResource("misc"))

        SettingsTextField(
            title = stringResource("top_length"),
            subtitle = "",
            value = topLengthInput,
            onValueChange = { value ->
                topLengthInput = value.filter { it.isDigit() }
            },
            onSave = {
                if (topLengthInput.toIntOrNull()?.let { it > 0 } == true) {
                    preferences.setTopLength(topLengthInput)
                }
            },
            placeholder = "50",
        )

        SettingsDropdown(
            title = stringResource("set_quick_picks"),
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
