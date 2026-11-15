package com.anitail.shared.utils

import kotlin.time.Clock

object AnitailClock {
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
