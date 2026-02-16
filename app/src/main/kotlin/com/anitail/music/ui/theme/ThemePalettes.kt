package com.anitail.music.ui.theme

import androidx.compose.ui.graphics.Color
import com.anitail.music.constants.ThemePalette

data class ThemePalettePreview(
    val name: ThemePalette,
    val seed: Color,
    val chipA: Color,
    val chipB: Color,
    val chipC: Color,
    val chipD: Color,
)

val ThemePalettePreviews = listOf(
    ThemePalettePreview(
        name = ThemePalette.SUNSET,
        seed = Color(0xFFE8B17A),
        chipA = Color(0xFF8E1E2D),
        chipB = Color(0xFFE8B17A),
        chipC = Color(0xFFE3C1C2),
        chipD = Color(0xFFEBCB95),
    ),
    ThemePalettePreview(
        name = ThemePalette.CHERRY,
        seed = Color(0xFFCF6E82),
        chipA = Color(0xFF7D1F35),
        chipB = Color(0xFFCF6E82),
        chipC = Color(0xFFDAB9C3),
        chipD = Color(0xFFE8C08E),
    ),
    ThemePalettePreview(
        name = ThemePalette.VIOLET,
        seed = Color(0xFFA88BD6),
        chipA = Color(0xFF6E2C79),
        chipB = Color(0xFFA88BD6),
        chipC = Color(0xFFDCCAE8),
        chipD = Color(0xFFF0B5B6),
    ),
    ThemePalettePreview(
        name = ThemePalette.INDIGO,
        seed = Color(0xFF7E8DE0),
        chipA = Color(0xFF4B4797),
        chipB = Color(0xFF7E8DE0),
        chipC = Color(0xFFC1C2DD),
        chipD = Color(0xFFE2B2C8),
    ),
    ThemePalettePreview(
        name = ThemePalette.LAVENDER,
        seed = Color(0xFFB39DDB),
        chipA = Color(0xFF2B2736),
        chipB = Color(0xFFB39DDB),
        chipC = Color(0xFFC9C1D9),
        chipD = Color(0xFFD9A9B7),
    ),
    ThemePalettePreview(
        name = ThemePalette.OCEAN,
        seed = Color(0xFF3F7EA6),
        chipA = Color(0xFF214E69),
        chipB = Color(0xFF3F7EA6),
        chipC = Color(0xFFC0D2DE),
        chipD = Color(0xFFD9E6EF),
    ),
    ThemePalettePreview(
        name = ThemePalette.RUBY,
        seed = Color(0xFFB74A67),
        chipA = Color(0xFF6F1E34),
        chipB = Color(0xFFB74A67),
        chipC = Color(0xFFE0C1CB),
        chipD = Color(0xFFF0D0A6),
    ),
    ThemePalettePreview(
        name = ThemePalette.FOREST,
        seed = Color(0xFF4F8F6B),
        chipA = Color(0xFF244A38),
        chipB = Color(0xFF4F8F6B),
        chipC = Color(0xFFBFDCCD),
        chipD = Color(0xFFDDEADA),
    ),
    ThemePalettePreview(
        name = ThemePalette.GOLDEN_HOUR,
        seed = Color(0xFFD39A52),
        chipA = Color(0xFF6A3E1F),
        chipB = Color(0xFFD39A52),
        chipC = Color(0xFFE7CFB2),
        chipD = Color(0xFFF4E2B8),
    ),
    ThemePalettePreview(
        name = ThemePalette.MIDNIGHT,
        seed = Color(0xFF5468B2),
        chipA = Color(0xFF222A52),
        chipB = Color(0xFF5468B2),
        chipC = Color(0xFFC4CDE8),
        chipD = Color(0xFFDADAF2),
    ),
    ThemePalettePreview(
        name = ThemePalette.SKY,
        seed = Color(0xFF62A5D8),
        chipA = Color(0xFF2B5D81),
        chipB = Color(0xFF62A5D8),
        chipC = Color(0xFFBFE0F4),
        chipD = Color(0xFFE1F2FD),
    ),
    ThemePalettePreview(
        name = ThemePalette.MINT,
        seed = Color(0xFF66BFA4),
        chipA = Color(0xFF2C6F5E),
        chipB = Color(0xFF66BFA4),
        chipC = Color(0xFFC3EBDD),
        chipD = Color(0xFFE1F8F1),
    ),
)

fun ThemePalette.seedColor(): Color =
    ThemePalettePreviews.firstOrNull { it.name == this }?.seed
        ?: ThemePalettePreviews.firstOrNull { it.name == ThemePalette.LAVENDER }?.seed
        ?: ThemePalettePreviews.first().seed
