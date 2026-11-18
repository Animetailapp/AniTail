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
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface as SkiaSurface
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    val typography = rememberExpressiveTypography()

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = motionScheme,
        shapes = AnitailShapes,
        typography = typography,
        content = content,
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

private fun ImageBitmap.downsampleForColorSampling(maxSize: Int = 64): ImageBitmap {
    if (width <= maxSize && height <= maxSize) return this
    val scale = maxSize.toFloat() / max(width, height).toFloat()
    val dstW = (width * scale).roundToInt().coerceAtLeast(1)
    val dstH = (height * scale).roundToInt().coerceAtLeast(1)
    return runCatching {
        SkiaImage.makeFromBitmap(asSkiaBitmap()).use { src ->
            SkiaSurface.makeRasterN32Premul(dstW, dstH).use { surface ->
                SkiaPaint().use { paint ->
                    surface.canvas.drawImageRect(
                        src,
                        SkiaRect.makeWH(width.toFloat(), height.toFloat()),
                        SkiaRect.makeWH(dstW.toFloat(), dstH.toFloat()),
                        SamplingMode.LINEAR,
                        paint,
                        true,
                    )
                }
                surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
            }
        }
    }.getOrDefault(this)
}

private fun ImageBitmap.sampleColorPopulation(): Map<Int, Int> {
    val sampled = downsampleForColorSampling(64)
    val pixelMap = sampled.toPixelMap()
    val step = max(1, min(sampled.width, sampled.height) / 64)
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
