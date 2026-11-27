package com.anitail.shared.model

data class LyricsTranslationProgress(
    val lines: Set<Int> = emptySet(),
    val backgroundLines: Set<Int> = emptySet(),
) {
    val isTranslating: Boolean get() = lines.isNotEmpty() || backgroundLines.isNotEmpty()
}
