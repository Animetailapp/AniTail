package com.my.kizzy.rpc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lightweight LRU cache that memoises Discord asset ids for artwork we rehost.
 * Maintains an in-memory map plus a persisted index so we do not re-upload the
 * same image between app launches. Entries expire after 24 hours and the cache
 * is capped at roughly 25 MB.
 */
internal object ArtworkCache {
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private const val MAX_BYTES = 25L * 1024L * 1024L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cacheDir: File by lazy {
        val base = System.getProperty("java.io.tmpdir") ?: System.getProperty("user.dir")
        File(base, "anitail-discord-art").apply { mkdirs() }
    }
    private val indexFile: File by lazy { File(cacheDir, "artwork-index.json") }

    private val mutex = Mutex()
    private val memory = object : LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {}
    private var currentSize = 0L
    @Volatile
    private var loadedFromDisk = false

    suspend fun getOrFetch(url: String, fetcher: suspend () -> String?): String? {
        mutex.withLock {
            ensureLoadedLocked()
            getValidLocked(url)?.let { return it }
        }

        val fetched = fetcher() ?: return null

        mutex.withLock {
            ensureLoadedLocked()
            getValidLocked(url)?.let { return it }
            putLocked(url, fetched)
            persistLocked()
            return fetched
        }
    }

    suspend fun clear() {
        mutex.withLock {
            memory.clear()
            currentSize = 0
            loadedFromDisk = true
            if (indexFile.exists()) {
                runCatching { indexFile.delete() }
            }
        }
    }

    private fun ensureLoadedLocked() {
        if (loadedFromDisk) return
        if (!indexFile.exists()) {
            loadedFromDisk = true
            return
        }
        val text = runCatching { indexFile.readText() }.getOrNull()
        if (text.isNullOrBlank()) {
            loadedFromDisk = true
            return
        }
        runCatching {
            val persisted = json.decodeFromString<PersistedIndex>(text)
            val now = System.currentTimeMillis()
            persisted.entries.forEach { entry ->
                if (now - entry.storedAt <= TTL_MILLIS) {
                    val cacheEntry = CacheEntry(entry.asset, entry.storedAt, entry.sizeBytes)
                    memory[entry.url] = cacheEntry
                    currentSize += entry.sizeBytes
                }
            }
            trimToSizeLocked()
        }
        loadedFromDisk = true
    }

    private fun getValidLocked(url: String): String? {
        val entry = memory[url] ?: return null
        val now = System.currentTimeMillis()
        return if (now - entry.storedAt <= TTL_MILLIS) {
            // Touch entry to keep access order
            memory.remove(url)
            memory[url] = entry
            entry.asset
        } else {
            memory.remove(url)
            currentSize -= entry.sizeBytes
            null
        }
    }

    private fun putLocked(url: String, asset: String) {
        val size = computeSize(url, asset)
        memory.remove(url)?.let { currentSize -= it.sizeBytes }
        val entry =
            CacheEntry(asset = asset, storedAt = System.currentTimeMillis(), sizeBytes = size)
        memory[url] = entry
        currentSize += size
        trimToSizeLocked()
    }

    private fun trimToSizeLocked() {
        if (currentSize <= MAX_BYTES) return
        val iterator = memory.entries.iterator()
        while (currentSize > MAX_BYTES && iterator.hasNext()) {
            val removed = iterator.next()
            iterator.remove()
            currentSize -= removed.value.sizeBytes
        }
    }

    private fun persistLocked() {
        val entries = memory.map { (url, entry) ->
            PersistedEntry(
                url = url,
                asset = entry.asset,
                storedAt = entry.storedAt,
                sizeBytes = entry.sizeBytes
            )
        }
        val payload = json.encodeToString(PersistedIndex(entries))
        runCatching { indexFile.writeText(payload) }
    }

    private fun computeSize(url: String, asset: String): Int {
        val urlBytes = url.toByteArray(Charsets.UTF_8).size
        val assetBytes = asset.toByteArray(Charsets.UTF_8).size
        return urlBytes + assetBytes
    }

    private data class CacheEntry(
        val asset: String,
        val storedAt: Long,
        val sizeBytes: Int,
    )

    @Serializable
    private data class PersistedEntry(
        val url: String,
        val asset: String,
        val storedAt: Long,
        val sizeBytes: Int,
    )

    @Serializable
    private data class PersistedIndex(
        val entries: List<PersistedEntry> = emptyList(),
    )
}
