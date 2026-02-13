package com.anitail.desktop.player

data class SleepTimerState(
    val isActive: Boolean,
    val endAtMs: Long,
    val durationMs: Long,
)

fun inactiveSleepTimerState(): SleepTimerState = SleepTimerState(
    isActive = false,
    endAtMs = 0L,
    durationMs = 0L,
)

fun startSleepTimerState(nowMs: Long, durationMs: Long): SleepTimerState {
    if (durationMs <= 0L) return inactiveSleepTimerState()
    return SleepTimerState(
        isActive = true,
        endAtMs = nowMs + durationMs,
        durationMs = durationMs,
    )
}

fun cancelSleepTimerState(): SleepTimerState = inactiveSleepTimerState()

fun computeSleepTimerTimeLeftMs(state: SleepTimerState, nowMs: Long): Long {
    if (!state.isActive) return 0L
    return (state.endAtMs - nowMs).coerceAtLeast(0L)
}

fun isSleepTimerExpired(state: SleepTimerState, nowMs: Long): Boolean {
    return state.isActive && nowMs >= state.endAtMs
}
