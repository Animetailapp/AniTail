package com.anitail.music.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FabScrollTrackerTest {

    @Test
    fun `fab collapses on scroll down, hides near end, and expands on scroll up or top`() {
        val tracker = FabScrollTracker(threshold = 48f)

        assertEquals(
            ScrollingFabState.Expanded,
            tracker.update(FabScrollPosition(index = 0, offset = 0, atStart = true, nearEnd = false)),
        )
        assertEquals(
            ScrollingFabState.Collapsed,
            tracker.update(FabScrollPosition(index = 0, offset = 60, atStart = false, nearEnd = false)),
        )
        assertEquals(
            ScrollingFabState.Hidden,
            tracker.update(FabScrollPosition(index = 1, offset = 100, atStart = false, nearEnd = true)),
        )
        assertEquals(
            ScrollingFabState.Expanded,
            tracker.update(FabScrollPosition(index = 1, offset = 40, atStart = false, nearEnd = false)),
        )
        assertEquals(
            ScrollingFabState.Collapsed,
            tracker.update(FabScrollPosition(index = 1, offset = 96, atStart = false, nearEnd = false)),
        )
        assertEquals(
            ScrollingFabState.Expanded,
            tracker.update(FabScrollPosition(index = 0, offset = 0, atStart = true, nearEnd = false)),
        )
    }
}

