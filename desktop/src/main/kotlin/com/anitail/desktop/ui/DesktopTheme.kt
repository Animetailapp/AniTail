package com.anitail.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLocaleList
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.anitail.desktop.storage.DarkModePreference
import com.anitail.shared.ui.theme.AppTypography
import com.materialkolor.MaterialKolors
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

fun isNight(): Boolean {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    return hour < 6 || hour >= 19
}

val DefaultThemeColor = Color(0xFFED5564)

val AnitailShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
private fun rememberExpressiveTypography(
    localeList: LocaleList = LocalLocaleList.current,
): Typography {
    val primaryTag = localeList.firstOrNull()?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    val resolvedFamily = remember(primaryTag) {
        resolveLocaleAwareSystemFontFamily(primaryTag)
    }
    return remember(resolvedFamily, localeList) {
        buildExpressiveTypography(
            fontFamily = resolvedFamily,
            localeList = localeList,
        )
    }
}

private fun buildExpressiveTypography(
    fontFamily: FontFamily,
    localeList: LocaleList,
): Typography = Typography(
    fontFamily = fontFamily,
    displayLarge = AppTypography.displayLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    displayMedium = AppTypography.displayMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    displaySmall = AppTypography.displaySmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    headlineLarge = AppTypography.headlineLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    headlineMedium = AppTypography.headlineMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    headlineSmall = AppTypography.headlineSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        localeList = localeList,
    ),
    titleLarge = AppTypography.titleLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        localeList = localeList,
    ),
    titleMedium = AppTypography.titleMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        localeList = localeList,
    ),
    titleSmall = AppTypography.titleSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        localeList = localeList,
    ),
    bodyLarge = AppTypography.bodyLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        localeList = localeList,
    ),
    bodyMedium = AppTypography.bodyMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        localeList = localeList,
    ),
    bodySmall = AppTypography.bodySmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        localeList = localeList,
    ),
    labelLarge = AppTypography.labelLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        localeList = localeList,
    ),
    labelMedium = AppTypography.labelMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        localeList = localeList,
    ),
    labelSmall = AppTypography.labelSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        localeList = localeList,
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun resolveLocaleAwareSystemFontFamily(languageTag: String): FontFamily =
    runCatching {
        val locale = Locale.forLanguageTag(languageTag)
        val sample = locale.getDisplayName(locale)
        val manager = FontMgr.default
        val base = manager.matchFamilyStyle(null, FontStyle.NORMAL)
            ?: return@runCatching FontFamily.Default
        try {
            val missingCodePoint = sample.codePoints().toArray().firstOrNull {
                base.getUTF32Glyph(it).toInt() == 0
            } ?: return@runCatching FontFamily.Default

            val matched = manager.matchFamilyStyleCharacter(
                base.familyName,
                FontStyle.NORMAL,
                arrayOf(languageTag),
                missingCodePoint,
            ) ?: return@runCatching FontFamily.Default

            try {
                val familyName = matched.familyName
                if (familyName.isNotBlank()) FontFamily(familyName) else FontFamily.Default
            } finally {
                matched.close()
            }
        } finally {
            base.close()
        }
    }.getOrDefault(FontFamily.Default)

@Composable
fun AnitailTheme(
    darkMode: DarkModePreference = DarkModePreference.AUTO,
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = remember(darkMode, isSystemDark) {
        when (darkMode) {
            DarkModePreference.ON -> true
            DarkModePreference.OFF -> false
            DarkModePreference.AUTO -> isSystemDark
            DarkModePreference.TIME_BASED -> isNight()
        }
    }

    val baseColorScheme = remember(themeColor, useDarkTheme) {
        buildDynamicColorScheme(
            seedColor = themeColor,
            isDark = useDarkTheme,
        )
    }

    val colorScheme = remember(baseColorScheme, pureBlack, useDarkTheme) {
        if (useDarkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    val motionScheme = remember { MotionScheme.expressive() }
    val animatedColorScheme = animateColorScheme(colorScheme, motionScheme)
    val typography = rememberExpressiveTypography()

    MaterialTheme(
        colorScheme = animatedColorScheme,
        motionScheme = motionScheme,
        shapes = AnitailShapes,
        typography = typography,
        content = content,
    )
}

@Composable
private fun animateColorScheme(
    colorScheme: ColorScheme,
    motionScheme: MotionScheme,
): ColorScheme {
    var start by remember { mutableStateOf(colorScheme) }
    var end by remember { mutableStateOf(colorScheme) }
    val progress = remember { Animatable(1f) }
    val displayed = interpolateColorScheme(
        start = start,
        end = end,
        fraction = progress.value,
    )
    val current by rememberUpdatedState(displayed)
    LaunchedEffect(colorScheme, motionScheme) {
        start = current
        end = colorScheme
        val animate = start !== end
        progress.snapTo(if (animate) 0f else 1f)
        if (animate) {
            progress.animateTo(1f, motionScheme.defaultEffectsSpec())
        }
    }
    return displayed
}

private fun interpolateColorScheme(
    start: ColorScheme,
    end: ColorScheme,
    fraction: Float,
): ColorScheme =
    if (start === end || fraction >= 1f) {
        end
    } else {
        start.copy(
            primary = lerp(start.primary, end.primary, fraction),
            onPrimary = lerp(start.onPrimary, end.onPrimary, fraction),
            primaryContainer = lerp(start.primaryContainer, end.primaryContainer, fraction),
            onPrimaryContainer = lerp(start.onPrimaryContainer, end.onPrimaryContainer, fraction),
            inversePrimary = lerp(start.inversePrimary, end.inversePrimary, fraction),
            secondary = lerp(start.secondary, end.secondary, fraction),
            onSecondary = lerp(start.onSecondary, end.onSecondary, fraction),
            secondaryContainer = lerp(start.secondaryContainer, end.secondaryContainer, fraction),
            onSecondaryContainer = lerp(start.onSecondaryContainer, end.onSecondaryContainer, fraction),
            tertiary = lerp(start.tertiary, end.tertiary, fraction),
            onTertiary = lerp(start.onTertiary, end.onTertiary, fraction),
            tertiaryContainer = lerp(start.tertiaryContainer, end.tertiaryContainer, fraction),
            onTertiaryContainer = lerp(start.onTertiaryContainer, end.onTertiaryContainer, fraction),
            background = lerp(start.background, end.background, fraction),
            onBackground = lerp(start.onBackground, end.onBackground, fraction),
            surface = lerp(start.surface, end.surface, fraction),
            onSurface = lerp(start.onSurface, end.onSurface, fraction),
            surfaceVariant = lerp(start.surfaceVariant, end.surfaceVariant, fraction),
            onSurfaceVariant = lerp(start.onSurfaceVariant, end.onSurfaceVariant, fraction),
            surfaceTint = lerp(start.surfaceTint, end.surfaceTint, fraction),
            inverseSurface = lerp(start.inverseSurface, end.inverseSurface, fraction),
            inverseOnSurface = lerp(start.inverseOnSurface, end.inverseOnSurface, fraction),
            error = lerp(start.error, end.error, fraction),
            onError = lerp(start.onError, end.onError, fraction),
            errorContainer = lerp(start.errorContainer, end.errorContainer, fraction),
            onErrorContainer = lerp(start.onErrorContainer, end.onErrorContainer, fraction),
            outline = lerp(start.outline, end.outline, fraction),
            outlineVariant = lerp(start.outlineVariant, end.outlineVariant, fraction),
            scrim = lerp(start.scrim, end.scrim, fraction),
            surfaceBright = lerp(start.surfaceBright, end.surfaceBright, fraction),
            surfaceDim = lerp(start.surfaceDim, end.surfaceDim, fraction),
            surfaceContainer = lerp(start.surfaceContainer, end.surfaceContainer, fraction),
            surfaceContainerHigh = lerp(start.surfaceContainerHigh, end.surfaceContainerHigh, fraction),
            surfaceContainerHighest = lerp(start.surfaceContainerHighest, end.surfaceContainerHighest, fraction),
            surfaceContainerLow = lerp(start.surfaceContainerLow, end.surfaceContainerLow, fraction),
            surfaceContainerLowest = lerp(start.surfaceContainerLowest, end.surfaceContainerLowest, fraction),
            primaryFixed = lerp(start.primaryFixed, end.primaryFixed, fraction),
            primaryFixedDim = lerp(start.primaryFixedDim, end.primaryFixedDim, fraction),
            onPrimaryFixed = lerp(start.onPrimaryFixed, end.onPrimaryFixed, fraction),
            onPrimaryFixedVariant = lerp(start.onPrimaryFixedVariant, end.onPrimaryFixedVariant, fraction),
            secondaryFixed = lerp(start.secondaryFixed, end.secondaryFixed, fraction),
            secondaryFixedDim = lerp(start.secondaryFixedDim, end.secondaryFixedDim, fraction),
            onSecondaryFixed = lerp(start.onSecondaryFixed, end.onSecondaryFixed, fraction),
            onSecondaryFixedVariant = lerp(start.onSecondaryFixedVariant, end.onSecondaryFixedVariant, fraction),
            tertiaryFixed = lerp(start.tertiaryFixed, end.tertiaryFixed, fraction),
            tertiaryFixedDim = lerp(start.tertiaryFixedDim, end.tertiaryFixedDim, fraction),
            onTertiaryFixed = lerp(start.onTertiaryFixed, end.onTertiaryFixed, fraction),
            onTertiaryFixedVariant = lerp(start.onTertiaryFixedVariant, end.onTertiaryFixedVariant, fraction),
        )
    }

private fun buildDynamicColorScheme(
    seedColor: Color,
    isDark: Boolean,
): ColorScheme {
    val hct = Hct.fromInt(seedColor.toArgb())
    val scheme = SchemeTonalSpot(
        hct,
        isDark,
        0.0,
        ColorSpec.SpecVersion.SPEC_2025,
        DynamicScheme.Platform.PHONE,
    )
    val colors = MaterialKolors(scheme, isDark)
    return if (isDark) {
        darkColorScheme(
            primary = colors.primary(),
            onPrimary = colors.onPrimary(),
            primaryContainer = colors.primaryContainer(),
            onPrimaryContainer = colors.onPrimaryContainer(),
            inversePrimary = colors.inversePrimary(),
            secondary = colors.secondary(),
            onSecondary = colors.onSecondary(),
            secondaryContainer = colors.secondaryContainer(),
            onSecondaryContainer = colors.onSecondaryContainer(),
            tertiary = colors.tertiary(),
            onTertiary = colors.onTertiary(),
            tertiaryContainer = colors.tertiaryContainer(),
            onTertiaryContainer = colors.onTertiaryContainer(),
            background = colors.background(),
            onBackground = colors.onBackground(),
            surface = colors.surface(),
            onSurface = colors.onSurface(),
            surfaceVariant = colors.surfaceVariant(),
            onSurfaceVariant = colors.onSurfaceVariant(),
            surfaceTint = colors.surfaceTint(),
            inverseSurface = colors.inverseSurface(),
            inverseOnSurface = colors.inverseOnSurface(),
            error = colors.error(),
            onError = colors.onError(),
            errorContainer = colors.errorContainer(),
            onErrorContainer = colors.onErrorContainer(),
            outline = colors.outline(),
            outlineVariant = colors.outlineVariant(),
            scrim = colors.scrim(),
            surfaceBright = colors.surfaceBright(),
            surfaceDim = colors.surfaceDim(),
            surfaceContainer = colors.surfaceContainer(),
            surfaceContainerHigh = colors.surfaceContainerHigh(),
            surfaceContainerHighest = colors.surfaceContainerHighest(),
            surfaceContainerLow = colors.surfaceContainerLow(),
            surfaceContainerLowest = colors.surfaceContainerLowest(),
        )
    } else {
        lightColorScheme(
            primary = colors.primary(),
            onPrimary = colors.onPrimary(),
            primaryContainer = colors.primaryContainer(),
            onPrimaryContainer = colors.onPrimaryContainer(),
            inversePrimary = colors.inversePrimary(),
            secondary = colors.secondary(),
            onSecondary = colors.onSecondary(),
            secondaryContainer = colors.secondaryContainer(),
            onSecondaryContainer = colors.onSecondaryContainer(),
            tertiary = colors.tertiary(),
            onTertiary = colors.onTertiary(),
            tertiaryContainer = colors.tertiaryContainer(),
            onTertiaryContainer = colors.onTertiaryContainer(),
            background = colors.background(),
            onBackground = colors.onBackground(),
            surface = colors.surface(),
            onSurface = colors.onSurface(),
            surfaceVariant = colors.surfaceVariant(),
            onSurfaceVariant = colors.onSurfaceVariant(),
            surfaceTint = colors.surfaceTint(),
            inverseSurface = colors.inverseSurface(),
            inverseOnSurface = colors.inverseOnSurface(),
            error = colors.error(),
            onError = colors.onError(),
            errorContainer = colors.errorContainer(),
            onErrorContainer = colors.onErrorContainer(),
            outline = colors.outline(),
            outlineVariant = colors.outlineVariant(),
            scrim = colors.scrim(),
            surfaceBright = colors.surfaceBright(),
            surfaceDim = colors.surfaceDim(),
            surfaceContainer = colors.surfaceContainer(),
            surfaceContainerHigh = colors.surfaceContainerHigh(),
            surfaceContainerHighest = colors.surfaceContainerHighest(),
            surfaceContainerLow = colors.surfaceContainerLow(),
            surfaceContainerLowest = colors.surfaceContainerLowest(),
        )
    }
}

fun ColorScheme.pureBlack(apply: Boolean): ColorScheme =
    if (apply) {
        copy(
            surface = Color.Black,
            background = Color.Black,
        )
    } else {
        this
    }

internal fun ImageBitmap.extractThemeColor(): Color {
    val colorsToPopulation = sampleColorPopulation()
    if (colorsToPopulation.isEmpty()) return DefaultThemeColor
    val rankedColors = Score.score(colorsToPopulation)
    return if (rankedColors.isNotEmpty()) {
        Color(rankedColors.first())
    } else {
        DefaultThemeColor
    }
}

internal fun ImageBitmap.extractGradientColors(): List<Color> {
    val colorsToPopulation = sampleColorPopulation()
    if (colorsToPopulation.isEmpty()) {
        return listOf(Color(0xFF595959), Color(0xFF0D0D0D))
    }
    val rankedColors = Score.score(colorsToPopulation, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }
    return if (rankedColors.size >= 2) {
        listOf(Color(rankedColors[0]), Color(rankedColors[1]))
    } else {
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
    }
}

private fun ImageBitmap.sampleColorPopulation(): Map<Int, Int> {
    val pixelMap = toPixelMap()
    val step = max(1, min(width, height) / 64)
    val buckets = mutableMapOf<Int, Int>()

    var y = 0
    while (y < pixelMap.height) {
        var x = 0
        while (x < pixelMap.width) {
            val argb = pixelMap.buffer[pixelMap.bufferOffset + y * pixelMap.stride + x]
            val alpha = (argb ushr 24) and 0xFF
            if (alpha >= 0x40) {
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val rq = r shr 3
                val gq = g shr 3
                val bq = b shr 3
                val bucket = (rq shl 10) or (gq shl 5) or bq
                buckets[bucket] = (buckets[bucket] ?: 0) + 1
            }
            x += step
        }
        y += step
    }

    if (buckets.isEmpty()) return emptyMap()

    return buckets.entries.associate { (bucket, count) ->
        val rq = (bucket shr 10) and 0x1F
        val gq = (bucket shr 5) and 0x1F
        val bq = bucket and 0x1F
        val r = (rq shl 3) or (rq shr 2)
        val g = (gq shl 3) or (gq shr 2)
        val b = (bq shl 3) or (bq shr 2)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        color to count
    }
}
