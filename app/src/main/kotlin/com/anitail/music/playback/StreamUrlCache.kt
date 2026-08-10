/**
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anitail.music.playback

data class CachedStreamUrl(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val clientName: String = "unknown",
)

object StreamUrlCache {
    private const val MAX_ENTRIES = 500

    private data class Entry(
        val stream: CachedStreamUrl,
        val expiresAtMillis: Long,
        val itag: Int,
    )

    private val entries =
        object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
                size > MAX_ENTRIES
        }

    fun get(mediaId: String, targetItag: Int = 0): CachedStreamUrl? =
        synchronized(entries) {
            val entry = entries[mediaId] ?: return null
            if (entry.expiresAtMillis <= System.currentTimeMillis() || (targetItag != 0 && entry.itag != targetItag)) {
                entries.remove(mediaId)
                null
            } else {
                entry.stream
            }
        }

    fun clientName(mediaId: String): String? =
        synchronized(entries) { entries[mediaId]?.stream?.clientName }

    fun put(
        mediaId: String,
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        clientName: String = "unknown",
        expiresInSeconds: Int = 21600,
        itag: Int = 0,
    ): Boolean {
        val now = System.currentTimeMillis()
        val ttlMillis = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        val expiresAtMillis =
            runCatching { Math.addExact(now, ttlMillis) }
                .getOrDefault(Long.MAX_VALUE)

        synchronized(entries) {
            entries[mediaId] =
                Entry(
                    stream = CachedStreamUrl(url, requestHeaders.toMap(), clientName),
                    expiresAtMillis = expiresAtMillis,
                    itag = itag,
                )
            return true
        }
    }

    fun invalidate(mediaId: String) {
        synchronized(entries) {
            entries.remove(mediaId)
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }
}
