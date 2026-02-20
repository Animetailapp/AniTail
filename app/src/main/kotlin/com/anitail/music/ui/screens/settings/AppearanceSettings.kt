package com.anitail.music.ui.screens.settings

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.ChipSortTypeKey
import com.anitail.music.constants.CustomDensityScaleKey
import com.anitail.music.constants.DefaultOpenTabKey
import com.anitail.music.constants.DensityScale
import com.anitail.music.constants.DensityScaleKey
import com.anitail.music.constants.DynamicIconKey
import com.anitail.music.constants.DynamicThemeKey
import com.anitail.music.constants.GridItemSize
import com.anitail.music.constants.GridItemsSizeKey
import com.anitail.music.constants.HighRefreshRateKey
import com.anitail.music.constants.LibraryFilter
import com.anitail.music.constants.LyricsAnimationStyle
import com.anitail.music.constants.LyricsAnimationStyleKey
import com.anitail.music.constants.LyricsClickKey
import com.anitail.music.constants.LyricsCustomFontPathKey
import com.anitail.music.constants.LyricsFontSizeKey
import com.anitail.music.constants.LyricsScrollKey
import com.anitail.music.constants.LyricsSmoothScrollKey
import com.anitail.music.constants.LyricsTextPositionKey
import com.anitail.music.constants.PlayerBackgroundStyle
import com.anitail.music.constants.PlayerBackgroundStyleKey
import com.anitail.music.constants.PlayerButtonsStyle
import com.anitail.music.constants.PlayerButtonsStyleKey
import com.anitail.music.constants.ShowCachedPlaylistKey
import com.anitail.music.constants.ShowDownloadedPlaylistKey
import com.anitail.music.constants.ShowLikedPlaylistKey
import com.anitail.music.constants.ShowTopPlaylistKey
import com.anitail.music.constants.SliderStyle
import com.anitail.music.constants.SliderStyleKey
import com.anitail.music.constants.SlimNavBarKey
import com.anitail.music.constants.SwipeSensitivityKey
import com.anitail.music.constants.SwipeThumbnailKey
import com.anitail.music.constants.SwipeToSongKey
import com.anitail.music.constants.TranslateLyricsKey
import com.anitail.music.constants.UseNewMiniPlayerDesignKey
import com.anitail.music.constants.UseNewPlayerDesignKey
import com.anitail.music.lyrics.TranslationUtils
import com.anitail.music.ui.component.DefaultDialog
import com.anitail.music.ui.component.EnumListPreference
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.ListPreference
import com.anitail.music.ui.component.PlayerSliderTrack
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.PreferenceGroupTitle
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.ui.utils.tvClickable
import com.anitail.music.utils.FontUtils
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.launch
import me.saket.squiggles.SquigglySlider
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicIcon, onDynamicIconChange) = rememberPreference(
        DynamicIconKey,
        defaultValue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    )
    val (highRefreshRate, onHighRefreshRateChange) = rememberPreference(
        HighRefreshRateKey,
        defaultValue = false
    )
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (useNewMiniPlayerDesign, onUseNewMiniPlayerDesignChange) = rememberPreference(
        UseNewMiniPlayerDesignKey,
        defaultValue = true
    )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (densityScale, setDensityScale) = rememberPreference(DensityScaleKey, defaultValue = 1.0f)
    val (customDensityValue, setCustomDensityValue) = rememberPreference(
        CustomDensityScaleKey,
        defaultValue = 0.85f
    )
    val context = LocalContext.current
    val supportsDynamicIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomDensityDialog by rememberSaveable { mutableStateOf(false) }

    val onDensityScaleChange: (Float) -> Unit = { newScale ->
        if (newScale == -1f) {
            // Custom option selected - show dialog for input
            showCustomDensityDialog = true
        } else {
            // Preset option selected - apply immediately
            setDensityScale(newScale)
            // Also write to SharedPreferences for DensityScaler to read on next startup
            context.getSharedPreferences("anitail_settings", android.content.Context.MODE_PRIVATE)
                .edit {
                    putFloat("density_scale_factor", newScale)
                }
            showRestartDialog = true
        }
    }

    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) = rememberEnumPreference(
        PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.CENTER
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsFontSize, onLyricsFontSizeChange) = rememberPreference(
        LyricsFontSizeKey,
        defaultValue = 20f
    )
    val (lyricsCustomFontPath, onLyricsCustomFontPathChange) = rememberPreference(
        LyricsCustomFontPathKey,
        defaultValue = ""
    )
    val (lyricsSmoothScroll, onLyricsSmoothScrollChange) = rememberPreference(
        LyricsSmoothScrollKey,
        defaultValue = true
    )
    val (lyricsAnimationStyle, onLyricsAnimationStyleChange) = rememberEnumPreference(
        LyricsAnimationStyleKey,
        defaultValue = LyricsAnimationStyle.APPLE
    )
    val (translateLyrics, onTranslateLyricsChange) = rememberPreference(
        TranslateLyricsKey,
        defaultValue = false
    )
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.DEFAULT
    )

    val scope = rememberCoroutineScope()

    // Font picker launcher
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val cacheDir = File(context.cacheDir, "fonts")
                    if (!cacheDir.exists()) cacheDir.mkdirs()

                    val fileName =
                        "custom_lyrics_font.${fileUri.toString().substringAfterLast(".")}"
                    val destFile = File(cacheDir, fileName)

                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    if (FontUtils.isValidFontFile(destFile.absolutePath)) {
                        onLyricsCustomFontPathChange(destFile.absolutePath)
                        FontUtils.clearCache()
                        Toast.makeText(
                            context,
                            context.getString(R.string.font_loaded_successfully),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        destFile.delete()
                        Toast.makeText(
                            context,
                            context.getString(R.string.font_loading_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.font_loading_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    val (slimNav, onSlimNavChange) = rememberPreference(
        SlimNavBarKey,
        defaultValue = false
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )

    PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSliderOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .tvClickable {
                            onSliderStyleChange(SliderStyle.DEFAULT)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.default_),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SQUIGGLY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .tvClickable {
                            onSliderStyleChange(SliderStyle.SQUIGGLY)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    SquigglySlider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.squiggly),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SLIM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .tvClickable {
                            onSliderStyleChange(SliderStyle.SLIM)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {}
                                )
                            }
                    )

                    Text(
                        text = stringResource(R.string.slim),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.theme),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_dynamic_icon)) },
            description = if (supportsDynamicIcon) {
                stringResource(R.string.enable_dynamic_icon_desc)
            } else {
                stringResource(R.string.dynamic_icon_not_supported_desc)
            },
            icon = { Icon(painterResource(R.drawable.radio_button_unchecked), null) },
            checked = dynamicIcon,
            onCheckedChange = onDynamicIconChange,
            isEnabled = supportsDynamicIcon,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_high_refresh_rate)) },
            description = stringResource(R.string.enable_high_refresh_rate_desc),
            icon = { Icon(painterResource(R.drawable.speed), null) },
            checked = highRefreshRate,
            onCheckedChange = onHighRefreshRateChange,
        )

        if (dynamicTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                icon = { Icon(painterResource(R.drawable.palette), null) },
                checked = dynamicTheme,
                onCheckedChange = onDynamicThemeChange,
            )
        }

        PreferenceEntry(
            title = { Text(stringResource(R.string.theme)) },
            description = stringResource(R.string.customize_app_theme),
            icon = { Icon(painterResource(R.drawable.palette), null) },
            onClick = { navController.navigate("settings/theme_colors") },
        )

        ListPreference(
            title = { Text(stringResource(R.string.display_density_title)) },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = densityScale,
            values = DensityScale.entries.map { it.value },
            valueText = { scale ->
                val densityEnum = DensityScale.fromValue(scale)
                if (densityEnum == DensityScale.CUSTOM) {
                    context.getString(
                        R.string.display_density_custom_label,
                        (customDensityValue * 100).toInt()
                    )
                } else {
                    densityEnum.label
                }
            },
            onValueSelected = onDensityScaleChange,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.player),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.new_player_design)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            checked = useNewPlayerDesign,
            onCheckedChange = onUseNewPlayerDesignChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.new_mini_player_design)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = useNewMiniPlayerDesign,
            onCheckedChange = onUseNewMiniPlayerDesignChange,
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.player_background_style)) },
            icon = { Icon(painterResource(R.drawable.gradient), null) },
            selectedValue = playerBackground,
            onValueSelected = onPlayerBackgroundChange,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                }
            },
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.player_buttons_style)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            selectedValue = playerButtonsStyle,
            onValueSelected = onPlayerButtonsStyleChange,
            valueText = {
                when (it) {
                    PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                    PlayerButtonsStyle.SECONDARY -> stringResource(R.string.secondary_color_style)
                }
            },
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.player_slider_style)) },
            description =
                when (sliderStyle) {
                    SliderStyle.DEFAULT -> stringResource(R.string.default_)
                    SliderStyle.SQUIGGLY -> stringResource(R.string.squiggly)
                    SliderStyle.SLIM -> stringResource(R.string.slim)
                },
            icon = { Icon(painterResource(R.drawable.sliders), null) },
            onClick = {
                showSliderOptionDialog = true
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeThumbnail,
            onCheckedChange = onSwipeThumbnailChange,
        )

        AnimatedVisibility(swipeThumbnail) {
            var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }

            if (showSensitivityDialog) {
                var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }

                DefaultDialog(
                    onDismiss = {
                        tempSensitivity = swipeSensitivity
                        showSensitivityDialog = false
                    },
                    buttons = {
                        TextButton(
                            onClick = {
                                tempSensitivity = 0.73f
                            }
                        ) {
                            Text(stringResource(R.string.reset))
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false
                            }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                onSwipeSensitivityChange(tempSensitivity)
                                showSensitivityDialog = false
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.swipe_sensitivity),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "${(tempSensitivity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Slider(
                            value = tempSensitivity,
                            onValueChange = { tempSensitivity = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            PreferenceEntry(
                title = { Text(stringResource(R.string.swipe_sensitivity)) },
                description = "${(swipeSensitivity * 100).roundToInt()}%",
                icon = { Icon(painterResource(R.drawable.tune), null) },
                onClick = { showSensitivityDialog = true }
            )
        }

        EnumListPreference(
            title = { Text(stringResource(R.string.lyrics_text_position)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsPosition,
            onValueSelected = onLyricsPositionChange,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_click_change)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsClick,
            onCheckedChange = onLyricsClickChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsScroll,
            onCheckedChange = onLyricsScrollChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.smooth_lyrics_animation)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyricsSmoothScroll,
            onCheckedChange = onLyricsSmoothScrollChange,
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.lyrics_animation_style)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsAnimationStyle,
            onValueSelected = onLyricsAnimationStyleChange,
            valueText = {
                when (it) {
                    LyricsAnimationStyle.NONE -> stringResource(R.string.lyrics_animation_none)
                    LyricsAnimationStyle.FADE -> stringResource(R.string.lyrics_animation_fade)
                    LyricsAnimationStyle.GLOW -> stringResource(R.string.lyrics_animation_glow)
                    LyricsAnimationStyle.SLIDE -> stringResource(R.string.lyrics_animation_slide)
                    LyricsAnimationStyle.KARAOKE -> stringResource(R.string.lyrics_animation_karaoke)
                    LyricsAnimationStyle.APPLE -> stringResource(R.string.lyrics_animation_apple)
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.translation_models)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            checked = translateLyrics,
            onCheckedChange = onTranslateLyricsChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_translation_models)) },
            icon = { Icon(painterResource(R.drawable.delete), null) },
            description = stringResource(R.string.clear),
            onClick = {
                scope.launch { TranslationUtils.close() }
            }
        )

        // Lyrics font size preference with dialog
        var showFontSizeDialog by rememberSaveable { mutableStateOf(false) }

        if (showFontSizeDialog) {
            var tempFontSize by remember { mutableFloatStateOf(lyricsFontSize) }

            DefaultDialog(
                onDismiss = {
                    tempFontSize = lyricsFontSize
                    showFontSizeDialog = false
                },
                buttons = {
                    TextButton(
                        onClick = {
                            tempFontSize = 20f
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            tempFontSize = lyricsFontSize
                            showFontSizeDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onLyricsFontSizeChange(tempFontSize)
                            showFontSizeDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_font_size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(R.string.font_size_preview),
                        fontSize = tempFontSize.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "${tempFontSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Slider(
                        value = tempFontSize,
                        onValueChange = { tempFontSize = it },
                        valueRange = 12f..36f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_font_size)) },
            description = "${lyricsFontSize.toInt()}sp",
            icon = { Icon(painterResource(R.drawable.tune), null) },
            onClick = { showFontSizeDialog = true }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_custom_font)) },
            description = if (lyricsCustomFontPath.isEmpty())
                stringResource(R.string.use_system_font)
            else
                File(lyricsCustomFontPath).name,
            icon = { Icon(painterResource(R.drawable.format_align_left), null) },
            onClick = {
                if (lyricsCustomFontPath.isEmpty()) {
                    fontPickerLauncher.launch("*/*")
                } else {
                    onLyricsCustomFontPathChange("")
                    FontUtils.clearCache()
                }
            }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc),
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.EXPLORE -> stringResource(R.string.explore)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
        )

        ListPreference(
            title = { Text(stringResource(R.string.default_lib_chips)) },
            icon = { Icon(painterResource(R.drawable.tab), null) },
            selectedValue = defaultChip,
            values = listOf(
                LibraryFilter.LIBRARY, LibraryFilter.PLAYLISTS, LibraryFilter.SONGS,
                LibraryFilter.ALBUMS, LibraryFilter.ARTISTS
            ),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                    LibraryFilter.DOWNLOADED -> stringResource(R.string.filter_downloaded)
                }
            },
            onValueSelected = onDefaultChipChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_song_to_add)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeToSong,
            onCheckedChange = onSwipeToSongChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.slim_navbar)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = slimNav,
            onCheckedChange = onSlimNavChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.grid_cell_size)) },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = gridItemSize,
            onValueSelected = onGridItemSizeChange,
            valueText = {
                when (it) {
                    GridItemSize.BIG -> stringResource(R.string.big)
                    GridItemSize.SMALL -> stringResource(R.string.small)
                }
            },
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.auto_playlists)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_liked_playlist)) },
            icon = { Icon(painterResource(R.drawable.favorite), null) },
            checked = showLikedPlaylist,
            onCheckedChange = onShowLikedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_downloaded_playlist)) },
            icon = { Icon(painterResource(R.drawable.offline), null) },
            checked = showDownloadedPlaylist,
            onCheckedChange = onShowDownloadedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_top_playlist)) },
            icon = { Icon(painterResource(R.drawable.trending_up), null) },
            checked = showTopPlaylist,
            onCheckedChange = onShowTopPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_cached_playlist)) },
            icon = { Icon(painterResource(R.drawable.cached), null) },
            checked = showCachedPlaylist,
            onCheckedChange = onShowCachedPlaylistChange
        )
    }

    if (showRestartDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            onDismiss = { showRestartDialog = false }
        ) {
            Text(
                text = "Please restart the app for the density changes to take effect.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (showCustomDensityDialog) {
        var inputText by remember { mutableStateOf((customDensityValue * 100).toInt().toString()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showCustomDensityDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        val percentValue = inputText.toIntOrNull()
                        if (percentValue == null) {
                            errorMessage = "Please enter a valid number"
                            return@TextButton
                        }
                        if (percentValue !in 50..120) {
                            errorMessage = "Value must be between 50 and 120"
                            return@TextButton
                        }

                        val value = percentValue / 100f

                        if (value != null && value in 0.5f..1.2f) {
                            setCustomDensityValue(value)
                            setDensityScale(value)

                            // Write to SharedPreferences
                            context.getSharedPreferences(
                                "anitail_settings",
                                android.content.Context.MODE_PRIVATE
                            )
                                .edit {
                                    putFloat("density_scale_factor", value)
                                }

                            showCustomDensityDialog = false
                            showRestartDialog = true
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            onDismiss = { showCustomDensityDialog = false }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_density_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.custom_density_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = null
                    },
                    label = { Text("Percentage") },
                    suffix = { Text("%") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
    TIME_BASED
}

enum class NavigationTab {
    HOME,
    EXPLORE,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}
