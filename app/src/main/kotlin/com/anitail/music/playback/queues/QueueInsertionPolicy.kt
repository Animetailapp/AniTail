package com.anitail.music.playback.queues

internal const val QUEUE_INSERTION_CHUNK_SIZE = 250

internal fun resolvePreloadedAnchorIndex(
    itemIds: List<String>,
    requestedIndex: Int,
    preloadedMediaId: String,
): Int {
    if (itemIds.isEmpty()) return -1
    val clampedRequested = requestedIndex.coerceIn(0, itemIds.lastIndex)
    if (itemIds[clampedRequested] == preloadedMediaId) {
        return clampedRequested
    }
    return itemIds.indexOf(preloadedMediaId)
}

