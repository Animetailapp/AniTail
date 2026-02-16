package com.anitail.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemePreviewState {
    var isPaletteCustomizationActive by mutableStateOf(false)
        private set

    var customSeedPreviewColorInt by mutableStateOf<Int?>(null)
        private set

    fun beginCustomPalettePreview(initialColorInt: Int) {
        isPaletteCustomizationActive = true
        customSeedPreviewColorInt = initialColorInt
    }

    fun updateCustomPalettePreview(colorInt: Int) {
        if (isPaletteCustomizationActive) {
            customSeedPreviewColorInt = colorInt
        }
    }

    fun endCustomPalettePreview() {
        isPaletteCustomizationActive = false
        customSeedPreviewColorInt = null
    }
}
