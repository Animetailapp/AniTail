package com.anitail.music.ui.screens.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.CustomThemeSeedColorKey
import com.anitail.music.constants.DynamicThemeKey
import com.anitail.music.constants.ThemePalette
import com.anitail.music.constants.ThemePaletteKey
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class HsvState(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

private data class PresetPalette(
    val title: String,
    val colors: List<Color>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteCustomizationSettings(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val (customSeedColorInt, onCustomSeedColorChange) = rememberPreference(
        CustomThemeSeedColorKey,
        defaultValue = 0xFFB39DDB.toInt()
    )
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (_, onThemePaletteChange) = rememberEnumPreference(
        ThemePaletteKey,
        defaultValue = ThemePalette.LAVENDER
    )

    val savedColor = Color(customSeedColorInt.toLong() and 0xFFFFFFFF)
    var hsvState by remember(customSeedColorInt) { mutableStateOf(savedColor.toHsv()) }
    val selectedColor = Color.hsv(hsvState.hue, hsvState.saturation, hsvState.value)

    val presetPalettes = remember {
        listOf(
            PresetPalette(
                title = "Aislamiento",
                colors = listOf(
                    Color(0xFF4AA0C5),
                    Color(0xFF9ADBE5),
                    Color(0xFFF0C745),
                    Color(0xFFF6853E),
                    Color(0xFFF07252),
                ),
            ),
            PresetPalette(
                title = "Outrun",
                colors = listOf(
                    Color(0xFFF2F135),
                    Color(0xFFF1B45D),
                    Color(0xFFF67F72),
                    Color(0xFFF235A9),
                    Color(0xFF405A9D),
                ),
            ),
            PresetPalette(
                title = "Frambuesa",
                colors = listOf(
                    Color(0xFFB00024),
                    Color(0xFFF24363),
                    Color(0xFF4BBCC8),
                    Color(0xFF3EA8AF),
                    Color(0xFF2A7E76),
                ),
            ),
            PresetPalette(
                title = "Catodica",
                colors = listOf(
                    Color(0xFFB92782),
                    Color(0xFFFF1552),
                    Color(0xFFF56E46),
                    Color(0xFFE9D56E),
                    Color(0xFF3D9B9E),
                ),
            ),
            PresetPalette(
                title = "Chicle",
                colors = listOf(
                    Color(0xFFEFCB84),
                    Color(0xFFF15A78),
                    Color(0xFFF1228C),
                    Color(0xFF36ACBB),
                    Color(0xFF27E0CC),
                ),
            ),
            PresetPalette(
                title = "Springfield",
                colors = listOf(
                    Color(0xFF84C2D0),
                    Color(0xFFD78CE2),
                    Color(0xFFA877D9),
                    Color(0xFFE1CC83),
                    Color(0xFFA3DD82),
                ),
            ),
            PresetPalette(
                title = "Espectral",
                colors = listOf(
                    Color(0xFF65124D),
                    Color(0xFFC33767),
                    Color(0xFFFF5349),
                    Color(0xFFFF7746),
                    Color(0xFFFF9A4A),
                ),
            ),
            PresetPalette(
                title = "Noche en la playa",
                colors = listOf(
                    Color(0xFF2A2A49),
                    Color(0xFF5D4B69),
                    Color(0xFF9E736D),
                    Color(0xFFD08967),
                    Color(0xFFF4B96A),
                ),
            ),
            PresetPalette(
                title = "Casual",
                colors = listOf(
                    Color(0xFF5875A0),
                    Color(0xFF8BA3BA),
                    Color(0xFFE0CFA2),
                    Color(0xFFE2B07C),
                    Color(0xFFD19477),
                ),
            ),
            PresetPalette(
                title = "Patagonia",
                colors = listOf(
                    Color(0xFF1F3942),
                    Color(0xFF93A187),
                    Color(0xFF5F7D63),
                    Color(0xFFA9AF8F),
                    Color(0xFFD0D0AC),
                ),
            ),
            PresetPalette(
                title = "LCD",
                colors = listOf(
                    Color(0xFF074F0C),
                    Color(0xFF326F33),
                    Color(0xFF8BB20D),
                    Color(0xFFA4C40B),
                ),
            ),
            PresetPalette(
                title = "Pop",
                colors = listOf(
                    Color(0xFF0EFA3E),
                    Color(0xFF42A9E8),
                    Color(0xFFF149A1),
                    Color(0xFFF2F539),
                ),
            ),
            PresetPalette(
                title = "PICO-8",
                colors = listOf(
                    Color(0xFF1D2B53),
                    Color(0xFF7E2553),
                    Color(0xFF008751),
                    Color(0xFFAB5236),
                    Color(0xFF5F574F),
                    Color(0xFFC2C3C7),
                    Color(0xFFFF004D),
                    Color(0xFFFFA300),
                    Color(0xFFFFEC27),
                    Color(0xFF00E436),
                    Color(0xFF29ADFF),
                    Color(0xFF83769C),
                    Color(0xFFFF77A8),
                    Color(0xFFFFCCAA),
                ),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.palette_customization)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HueSaturationWheel(
                hue = hsvState.hue,
                saturation = hsvState.saturation,
                onColorChanged = { hue, saturation ->
                    hsvState = hsvState.copy(hue = hue, saturation = saturation)
                },
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .padding(top = 2.dp)
            )

            ValueGradientSlider(
                value = hsvState.value,
                hue = hsvState.hue,
                saturation = hsvState.saturation,
                onValueChanged = { value ->
                    hsvState = hsvState.copy(value = value)
                },
                modifier = Modifier.fillMaxWidth(0.88f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColorInfoSwatch(
                    hex = selectedColor.toHexArgb(),
                    color = selectedColor
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                ColorInfoSwatch(
                    hex = savedColor.toHexArgb(),
                    color = savedColor
                )
            }

            Button(
                onClick = {
                    onCustomSeedColorChange(selectedColor.toArgb())
                    onThemePaletteChange(ThemePalette.CUSTOM)
                    if (dynamicTheme) {
                        onDynamicThemeChange(false)
                    }
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.confirm_color),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            presetPalettes.forEach { preset ->
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    preset.colors.forEach { color ->
                        PalettePresetChip(
                            color = color,
                            selected = color.toArgb() == selectedColor.toArgb(),
                            onClick = {
                                hsvState = color.toHsv()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorInfoSwatch(
    hex: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = hex,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color)
        )
    }
}

@Composable
private fun HueSaturationWheel(
    hue: Float,
    saturation: Float,
    onColorChanged: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wheelRadius by remember { mutableFloatStateOf(1f) }
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }

    fun updateFrom(position: Offset) {
        val dx = position.x - wheelCenter.x
        val dy = position.y - wheelCenter.y
        val distance = sqrt((dx * dx) + (dy * dy))
        val sat = (distance / wheelRadius).coerceIn(0f, 1f)
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        onColorChanged(angle, sat)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .onSizeChanged {
                val minDim = it.width.coerceAtMost(it.height).toFloat()
                wheelRadius = minDim / 2f
                wheelCenter = Offset(it.width / 2f, it.height / 2f)
            }
            .pointerInput(wheelRadius) {
                detectTapGestures { position ->
                    updateFrom(position)
                }
            }
            .pointerInput(wheelRadius) {
                detectDragGestures { change, _ ->
                    updateFrom(change.position)
                }
            }
            .aspectRatio(1f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                )
            )

            val indicatorRadius = (size.minDimension / 2f) * saturation
            val angleInRadians = Math.toRadians(hue.toDouble())
            val indicatorX = center.x + (cos(angleInRadians) * indicatorRadius).toFloat()
            val indicatorY = center.y + (sin(angleInRadians) * indicatorRadius).toFloat()
            val indicatorCenter = Offset(indicatorX, indicatorY)

            drawCircle(
                color = Color.White,
                center = indicatorCenter,
                radius = 14f,
                style = Stroke(width = 6f)
            )
            drawCircle(
                color = Color.hsv(hue, saturation, 1f),
                center = indicatorCenter,
                radius = 10f
            )
        }
    }
}

@Composable
private fun ValueGradientSlider(
    value: Float,
    hue: Float,
    saturation: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun update(x: Float) {
        onValueChanged((x / widthPx).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .onSizeChanged {
                widthPx = it.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(widthPx) {
                detectTapGestures { position ->
                    update(position.x)
                }
            }
            .pointerInput(widthPx) {
                detectDragGestures { change, _ ->
                    update(change.position.x)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val activeColor = Color.hsv(hue, saturation, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, activeColor)
                ),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )

            val knobX = size.width * value
            val knobCenter = Offset(knobX, size.height / 2f)
            drawCircle(
                color = Color.White,
                center = knobCenter,
                radius = size.height * 0.34f,
                style = Stroke(width = size.height * 0.11f)
            )
        }
    }
}

@Composable
private fun PalettePresetChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    )
}

private fun Color.toHsv(): HsvState {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    return HsvState(
        hue = hsv[0].coerceIn(0f, 360f),
        saturation = hsv[1].coerceIn(0f, 1f),
        value = hsv[2].coerceIn(0f, 1f),
    )
}

private fun Color.toHexArgb(): String {
    val argb = toArgb()
    return String.format("#%08x", argb)
}
