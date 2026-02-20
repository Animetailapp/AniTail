package com.anitail.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerLogicTest {
    @Test
    fun startStateSetsEndTimeAndActive() {
        val nowMs = 1_000L
        val durationMs = 30 * 60 * 1_000L

        val state = startSleepTimerState(nowMs, durationMs)

        assertTrue(state.isActive)
        assertEquals(nowMs + durationMs, state.endAtMs)
        assertEquals(durationMs, state.durationMs)
    }

    @Test
    fun startStateInactiveWhenDurationIsZeroOrNegative() {
        val stateZero = startSleepTimerState(1_000L, 0L)
        val stateNegative = startSleepTimerState(1_000L, -10L)

        assertFalse(stateZero.isActive)
        assertEquals(0L, stateZero.endAtMs)
        assertEquals(0L, stateZero.durationMs)

        assertFalse(stateNegative.isActive)
        assertEquals(0L, stateNegative.endAtMs)
        assertEquals(0L, stateNegative.durationMs)
    }

    @Test
    fun computeTimeLeftClampsAtZero() {
        val nowMs = 5_000L
        val durationMs = 2_000L
        val state = startSleepTimerState(nowMs, durationMs)

        assertEquals(2_000L, computeSleepTimerTimeLeftMs(state, nowMs))
        assertEquals(500L, computeSleepTimerTimeLeftMs(state, nowMs + 1_500L))
        assertEquals(0L, computeSleepTimerTimeLeftMs(state, nowMs + 5_000L))
    }

    @Test
    fun expiredWhenNowPastEndTime() {
        val nowMs = 10_000L
        val durationMs = 3_000L
        val state = startSleepTimerState(nowMs, durationMs)

        assertFalse(isSleepTimerExpired(state, nowMs + 2_999L))
        assertTrue(isSleepTimerExpired(state, nowMs + 3_000L))
    }

    @Test
    fun cancelResetsToInactive() {
        val canceled = cancelSleepTimerState()

        assertFalse(canceled.isActive)
        assertEquals(0L, canceled.endAtMs)
        assertEquals(0L, canceled.durationMs)
    }
}
