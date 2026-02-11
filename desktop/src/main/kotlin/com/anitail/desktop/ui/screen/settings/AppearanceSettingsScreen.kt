package com.anitail.desktop.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Divider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.anitail.desktop.i18n.stringResource
import com.anitail.desktop.storage.DarkModePreference
import com.anitail.desktop.storage.DesktopPreferences
import com.anitail.desktop.storage.LyricsAnimationStylePreference
import com.anitail.desktop.storage.LyricsPositionPreference
import com.anitail.desktop.storage.NavigationTabPreference
import com.anitail.desktop.storage.PlayerBackgroundStyle
import com.anitail.desktop.storage.PlayerButtonsStyle
import com.anitail.desktop.storage.SliderStyle
import com.anitail.desktop.ui.screen.library.GridItemSize
import com.anitail.desktop.ui.screen.library.LibraryFilter
import kotlin.math.abs

@Composable
internal fun AppearanceSettingsScreen(
    preferences: DesktopPreferences,
    onBack: () -> Unit,
) {
    val darkMode by preferences.darkMode.collectAsState()
    val pureBlack by preferences.pureBlack.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val densityScale by preferences.densityScale.collectAsState()
    val customDensityValue by preferences.customDensityValue.collectAsState()
    val defaultOpenTab by preferences.defaultOpenTab.collectAsState()
    val defaultLibChip by preferences.defaultLibChip.collectAsState()
    val slimNavBar by preferences.slimNavBar.collectAsState()
    val swipeToSong by preferences.swipeToSong.collectAsState()
    val swipeThumbnail by preferences.swipeThumbnail.collectAsState()
    val swipeSensitivity by preferences.swipeSensitivity.collectAsState()
    val playerBackgroundStyle by preferences.playerBackgroundStyle.collectAsState()
    val playerButtonsStyle by preferences.playerButtonsStyle.collectAsState()
    val sliderStyle by preferences.sliderStyle.collectAsState()
    val lyricsTextPosition by preferences.lyricsTextPosition.collectAsState()
    val lyricsClick by preferences.lyricsClick.collectAsState()
    val lyricsScroll by preferences.lyricsScroll.collectAsState()
    val lyricsFontSize by preferences.lyricsFontSize.collectAsState()
    val lyricsCustomFontPath by preferences.lyricsCustomFontPath.collectAsState()
    val lyricsSmoothScroll by preferences.lyricsSmoothScroll.collectAsState()
    val lyricsAnimationStyle by preferences.lyricsAnimationStyle.collectAsState()
    val gridItemSize by preferences.gridItemSize.collectAsState()
    val showLikedPlaylist by preferences.showLikedPlaylist.collectAsState()
    val showDownloadedPlaylist by preferences.showDownloadedPlaylist.collectAsState()
    val showTopPlaylist by preferences.showTopPlaylist.collectAsState()
    val showCachedPlaylist by preferences.showCachedPlaylist.collectAsState()
    var customFontPathInput by remember(lyricsCustomFontPath) { mutableStateOf(lyricsCustomFontPath) }

    SettingsSubScreen(
        title = stringResource("appearance"),
        onBack = onBack,
    ) {
        SettingsDropdown(
            title = stringResource("dark_theme"),
            subtitle = when (darkMode) {
                DarkModePreference.ON -> stringResource("dark_theme_on")
                DarkModePreference.OFF -> stringResource("dark_theme_off")
                DarkModePreference.AUTO -> stringResource("dark_theme_follow_system")
                DarkModePreference.TIME_BASED -> stringResource("dark_theme_time_based")
            },
            options = DarkModePreference.entries.map {
                when (it) {
                    DarkModePreference.ON -> stringResource("dark_theme_on")
                    DarkModePreference.OFF -> stringResource("dark_theme_off")
                    DarkModePreference.AUTO -> stringResource("dark_theme_follow_system")
                    DarkModePreference.TIME_BASED -> stringResource("dark_theme_time_based")
                }
            },
            selectedIndex = DarkModePreference.entries.indexOf(darkMode),
            onSelect = { index ->
                preferences.setDarkMode(DarkModePreference.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("pure_black"),
            subtitle = stringResource("pure_black_desc"),
            checked = pureBlack,
            onCheckedChange = { preferences.setPureBlack(it) },
        )

        SettingsSwitch(
            title = stringResource("enable_dynamic_theme"),
            subtitle = stringResource("enable_dynamic_theme_desc"),
            checked = dynamicColor,
            onCheckedChange = { preferences.setDynamicColor(it) },
        )

        val densityPresetValues = listOf(1.0f, 0.75f, 0.65f, 0.55f)
        val densityPresetLabels = listOf("100%", "75%", "65%", "55%")
        val customDensityLabel = stringResource("custom_density_title")
        val densitySelectedIndex = densityPresetValues
            .indexOfFirst { abs(it - densityScale) < 0.001f }
            .takeIf { it >= 0 }
            ?: densityPresetValues.size

        SettingsDropdown(
            title = stringResource("display_density_title"),
            subtitle = if (densitySelectedIndex < densityPresetLabels.size) {
                densityPresetLabels[densitySelectedIndex]
            } else {
                "${(densityScale * 100f).toInt()}%"
            },
            options = densityPresetLabels + customDensityLabel,
            selectedIndex = densitySelectedIndex,
            onSelect = { index ->
                if (index < densityPresetValues.size) {
                    preferences.setDensityScale(densityPresetValues[index])
                } else {
                    preferences.setDensityScale(customDensityValue)
                }
            },
        )

        SettingsSlider(
            title = customDensityLabel,
            subtitle = "${(customDensityValue * 100f).toInt()}%",
            value = (customDensityValue * 100f).coerceIn(50f, 120f),
            valueRange = 50f..120f,
            steps = 69,
            onValueChange = { value ->
                val scale = (value / 100f).coerceIn(0.5f, 1.2f)
                preferences.setCustomDensityValue(scale)
                if (densitySelectedIndex == densityPresetValues.size) {
                    preferences.setDensityScale(scale)
                }
            },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsDropdown(
            title = stringResource("player_background_style"),
            subtitle = stringResource(playerBackgroundStyle.labelKey),
            options = PlayerBackgroundStyle.entries.map { stringResource(it.labelKey) },
            selectedIndex = PlayerBackgroundStyle.entries.indexOf(playerBackgroundStyle),
            onSelect = { index ->
                preferences.setPlayerBackgroundStyle(PlayerBackgroundStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = stringResource("player_buttons_style"),
            subtitle = stringResource(playerButtonsStyle.labelKey),
            options = PlayerButtonsStyle.entries.map { stringResource(it.labelKey) },
            selectedIndex = PlayerButtonsStyle.entries.indexOf(playerButtonsStyle),
            onSelect = { index ->
                preferences.setPlayerButtonsStyle(PlayerButtonsStyle.entries[index])
            },
        )

        SettingsDropdown(
            title = stringResource("player_slider_style"),
            subtitle = stringResource(sliderStyle.labelKey),
            options = SliderStyle.entries.map { stringResource(it.labelKey) },
            selectedIndex = SliderStyle.entries.indexOf(sliderStyle),
            onSelect = { index ->
                preferences.setSliderStyle(SliderStyle.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("enable_swipe_thumbnail"),
            subtitle = "",
            checked = swipeThumbnail,
            onCheckedChange = { preferences.setSwipeThumbnail(it) },
        )

        if (swipeThumbnail) {
            SettingsSlider(
                title = stringResource("swipe_sensitivity"),
                subtitle = "${(swipeSensitivity * 100f).toInt()}%",
                value = (swipeSensitivity * 100f).coerceIn(0f, 100f),
                valueRange = 0f..100f,
                steps = 99,
                onValueChange = { value ->
                    preferences.setSwipeSensitivity((value / 100f).coerceIn(0f, 1f))
                },
            )
        }

        SettingsDropdown(
            title = stringResource("lyrics_text_position"),
            subtitle = when (lyricsTextPosition) {
                LyricsPositionPreference.LEFT -> stringResource("left")
                LyricsPositionPreference.CENTER -> stringResource("center")
                LyricsPositionPreference.RIGHT -> stringResource("right")
            },
            options = LyricsPositionPreference.entries.map { position ->
                when (position) {
                    LyricsPositionPreference.LEFT -> stringResource("left")
                    LyricsPositionPreference.CENTER -> stringResource("center")
                    LyricsPositionPreference.RIGHT -> stringResource("right")
                }
            },
            selectedIndex = LyricsPositionPreference.entries.indexOf(lyricsTextPosition),
            onSelect = { index ->
                preferences.setLyricsTextPosition(LyricsPositionPreference.entries[index])
            },
        )

        SettingsSwitch(
            title = stringResource("lyrics_click_change"),
            subtitle = "",
            checked = lyricsClick,
            onCheckedChange = { preferences.setLyricsClick(it) },
        )

        SettingsSwitch(
            title = stringResource("lyrics_auto_scroll"),
            subtitle = "",
            checked = lyricsScroll,
            onCheckedChange = { preferences.setLyricsScroll(it) },
        )

        SettingsSwitch(
            title = stringResource("smooth_lyrics_animation"),
            subtitle = "",
            checked = lyricsSmoothScroll,
            onCheckedChange = { preferences.setLyricsSmoothScroll(it) },
        )

        SettingsDropdown(
            title = stringResource("lyrics_animation_style"),
            subtitle = stringResource(lyricsAnimationStyle.labelKey),
            options = LyricsAnimationStylePreference.entries.map { stringResource(it.labelKey) },
            selectedIndex = LyricsAnimationStylePreference.entries.indexOf(lyricsAnimationStyle),
            onSelect = { index ->
                preferences.setLyricsAnimationStyle(LyricsAnimationStylePreference.entries[index])
            },
        )

        SettingsSlider(
            title = stringResource("lyrics_font_size"),
            subtitle = "${lyricsFontSize.toInt()}sp",
            value = lyricsFontSize.coerceIn(12f, 36f),
            valueRange = 12f..36f,
            steps = 23,
            onValueChange = { preferences.setLyricsFontSize(it) },
        )

        SettingsTextField(
            title = stringResource("lyrics_custom_font"),
            subtitle = if (lyricsCustomFontPath.isBlank()) {
                stringResource("use_system_font")
            } else {
                lyricsCustomFontPath
            },
            value = customFontPathInput,
            onValueChange = { customFontPathInput = it },
            onSave = { preferences.setLyricsCustomFontPath(customFontPathInput.trim()) },
            placeholder = "",
        )

        SettingsSectionTitle(title = stringResource("misc"))

        SettingsDropdown(
            title = stringResource("default_open_tab"),
            subtitle = when (defaultOpenTab) {
                NavigationTabPreference.HOME -> stringResource("home")
                NavigationTabPreference.EXPLORE -> stringResource("explore")
                NavigationTabPreference.LIBRARY -> stringResource("filter_library")
            },
            options = NavigationTabPreference.entries.map { tab ->
                when (tab) {
                    NavigationTabPreference.HOME -> stringResource("home")
                    NavigationTabPreference.EXPLORE -> stringResource("explore")
                    NavigationTabPreference.LIBRARY -> stringResource("filter_library")
                }
            },
            selectedIndex = NavigationTabPreference.entries.indexOf(defaultOpenTab),
            onSelect = { index ->
                preferences.setDefaultOpenTab(NavigationTabPreference.entries[index])
            },
        )

        val defaultChipOptions = listOf(
            LibraryFilter.LIBRARY,
            LibraryFilter.PLAYLISTS,
            LibraryFilter.SONGS,
            LibraryFilter.ALBUMS,
            LibraryFilter.ARTISTS,
        )
        val defaultChipLabels = defaultChipOptions.map { filter ->
            when (filter) {
                LibraryFilter.SONGS -> stringResource("songs")
                LibraryFilter.ARTISTS -> stringResource("artists")
                LibraryFilter.ALBUMS -> stringResource("albums")
                LibraryFilter.PLAYLISTS -> stringResource("playlists")
                LibraryFilter.LIBRARY -> stringResource("filter_library")
                LibraryFilter.DOWNLOADED -> stringResource("filter_downloaded")
            }
        }
        SettingsDropdown(
            title = stringResource("default_lib_chips"),
            subtitle = run {
                val selected = defaultChipOptions.indexOf(defaultLibChip).coerceAtLeast(0)
                defaultChipLabels[selected]
            },
            options = defaultChipLabels,
            selectedIndex = defaultChipOptions.indexOf(defaultLibChip).coerceAtLeast(0),
            onSelect = { index ->
                preferences.setDefaultLibChip(defaultChipOptions[index])
            },
        )

        SettingsSwitch(
            title = stringResource("swipe_song_to_add"),
            subtitle = "",
            checked = swipeToSong,
            onCheckedChange = { preferences.setSwipeToSong(it) },
        )

        SettingsSwitch(
            title = stringResource("slim_navbar"),
            subtitle = "",
            checked = slimNavBar,
            onCheckedChange = { preferences.setSlimNavBar(it) },
        )

        SettingsDropdown(
            title = stringResource("grid_cell_size"),
            subtitle = when (gridItemSize) {
                GridItemSize.BIG -> stringResource("big")
                GridItemSize.SMALL -> stringResource("small")
            },
            options = GridItemSize.entries.map { itemSize ->
                when (itemSize) {
                    GridItemSize.BIG -> stringResource("big")
                    GridItemSize.SMALL -> stringResource("small")
                }
            },
            selectedIndex = GridItemSize.entries.indexOf(gridItemSize),
            onSelect = { index ->
                preferences.setGridItemSize(GridItemSize.entries[index])
            },
        )

        SettingsSectionTitle(title = stringResource("auto_playlists"))

        SettingsSwitch(
            title = stringResource("show_liked_playlist"),
            subtitle = "",
            checked = showLikedPlaylist,
            onCheckedChange = { preferences.setShowLikedPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_downloaded_playlist"),
            subtitle = "",
            checked = showDownloadedPlaylist,
            onCheckedChange = { preferences.setShowDownloadedPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_top_playlist"),
            subtitle = "",
            checked = showTopPlaylist,
            onCheckedChange = { preferences.setShowTopPlaylist(it) },
        )

        SettingsSwitch(
            title = stringResource("show_cached_playlist"),
            subtitle = "",
            checked = showCachedPlaylist,
            onCheckedChange = { preferences.setShowCachedPlaylist(it) },
        )
    }
}
