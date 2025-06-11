package com.anitail.music.lyrics

data class LyricsEntry(
    val time: Long,
    val text: String,
    val romanizedText: String? = null
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}