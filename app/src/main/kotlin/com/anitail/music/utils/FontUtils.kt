package com.anitail.music.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

object FontUtils {
    private var cachedCustomFont: FontFamily? = null
    private var cachedFontPath: String? = null

    fun loadCustomFont(fontPath: String): FontFamily? {
        return try {
            if (cachedFontPath == fontPath && cachedCustomFont != null) {
                return cachedCustomFont
            }

            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                val fontFamily = FontFamily(
                    Font(fontFile, FontWeight.Normal)
                )

                cachedCustomFont = fontFamily
                cachedFontPath = fontPath
                fontFamily
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cachedCustomFont = null
        cachedFontPath = null
    }

    fun isValidFontFile(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && (file.extension.lowercase() in listOf("ttf", "otf"))
    }
}
