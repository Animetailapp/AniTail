package com.anitail.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.DarkModeKey
import com.anitail.music.constants.DynamicThemeKey
import com.anitail.music.constants.PureBlackKey
import com.anitail.music.constants.ThemePalette
import com.anitail.music.constants.ThemePaletteKey
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.theme.ThemePalettePreview
import com.anitail.music.ui.theme.ThemePalettePreviews
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference

private enum class ThemeModeOption {
    AUTO,
    LIGHT,
    DARK,
    BLACK,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeColorsSettings(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (themePalette, onThemePaletteChange) = rememberEnumPreference(
        ThemePaletteKey,
        defaultValue = ThemePalette.LAVENDER
    )

    val selectedMode = remember(darkMode, pureBlack) {
        when {
            darkMode == DarkMode.AUTO -> ThemeModeOption.AUTO
            darkMode == DarkMode.OFF -> ThemeModeOption.LIGHT
            darkMode == DarkMode.ON && pureBlack -> ThemeModeOption.BLACK
            else -> ThemeModeOption.DARK
        }
    }

    val activePalette = ThemePalettePreviews.firstOrNull { it.name == themePalette }
        ?: ThemePalettePreviews.firstOrNull { it.name == ThemePalette.LAVENDER }
        ?: ThemePalettePreviews.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_and_colors)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(horizontal = 10.dp)
        ) {
            ThemePreviewPhone(
                palette = activePalette,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.theme_mode),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeModeButton(
                            selected = selectedMode == ThemeModeOption.AUTO,
                            containerColor = Color(0xFF161523),
                            iconRes = R.drawable.sync,
                            onClick = {
                                onDarkModeChange(DarkMode.AUTO)
                                onPureBlackChange(false)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        ThemeModeButton(
                            selected = selectedMode == ThemeModeOption.LIGHT,
                            containerColor = Color(0xFFF0ECEC),
                            iconRes = null,
                            onClick = {
                                onDarkModeChange(DarkMode.OFF)
                                onPureBlackChange(false)
                            }
                        )
                        ThemeModeButton(
                            selected = selectedMode == ThemeModeOption.DARK,
                            containerColor = Color(0xFF231A24),
                            iconRes = null,
                            onClick = {
                                onDarkModeChange(DarkMode.ON)
                                onPureBlackChange(false)
                            }
                        )
                        ThemeModeButton(
                            selected = selectedMode == ThemeModeOption.BLACK,
                            containerColor = Color.Black,
                            iconRes = null,
                            onClick = {
                                onDarkModeChange(DarkMode.ON)
                                onPureBlackChange(true)
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.color_palette),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DynamicPaletteButton(
                            selected = dynamicTheme,
                            onClick = { onDynamicThemeChange(true) }
                        )

                        ThemePalettePreviews.forEach { palette ->
                            PaletteCircleButton(
                                selected = !dynamicTheme && themePalette == palette.name,
                                palette = palette,
                                onClick = {
                                    onDynamicThemeChange(false)
                                    onThemePaletteChange(palette.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeButton(
    selected: Boolean,
    containerColor: Color,
    iconRes: Int?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.done),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color(0xFFCBB7EE)
            )
        }
    }
}

@Composable
private fun DynamicPaletteButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.5f
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.palette),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaletteCircleButton(
    selected: Boolean,
    palette: ThemePalettePreview,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.45f
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
            .clip(CircleShape)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.chipA)
            )
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(palette.chipC)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(palette.chipD)
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewPhone(
    palette: ThemePalettePreview,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(120.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Color(0xFF211E2A))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 7.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.chipB)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 7.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.chipC)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.chipB)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.chipC)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.chipD)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (palette.chipA.luminance() < 0.25f) palette.seed else palette.chipA)
        )
    }
}
