package com.anitail.music.playback.queues

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueInsertionPolicyTest {
    @Test
    fun resolvePreloadedAnchorIndex_returnsRequestedIndexWhenMatchingEvenWithDuplicates() {
        val ids = listOf("songA", "songB", "songA", "songC")
        val resolved = resolvePreloadedAnchorIndex(
            itemIds = ids,
            requestedIndex = 2,
            preloadedMediaId = "songA",
        )
        assertEquals(2, resolved)
    }

    @Test
    fun resolvePreloadedAnchorIndex_findsShiftedIndexAfterExplicitFiltering() {
        val idsAfterFilter = listOf("songB", "songTarget", "songD")
        val resolved = resolvePreloadedAnchorIndex(
            itemIds = idsAfterFilter,
            requestedIndex = 3,
            preloadedMediaId = "songTarget",
        )
        assertEquals(1, resolved)
    }

    @Test
    fun resolvePreloadedAnchorIndex_returnsMinusOneWhenEmptyOrMissing() {
        assertEquals(-1, resolvePreloadedAnchorIndex(emptyList(), 0, "songA"))
        assertEquals(-1, resolvePreloadedAnchorIndex(listOf("songB", "songC"), 0, "songA"))
    }
}

