package com.jacksonfdam.slipgate.host.runtime

import kotlin.test.Test
import kotlin.test.assertTrue

class MonotonicClockTest {
    @Test
    fun timeStartsNearZeroAndNeverGoesBackwards() {
        val clock = MonotonicClock()

        val first = clock.elapsedMillis()
        val second = clock.elapsedMillis()

        assertTrue(first in 0..1_000, "a new clock reads near zero, not $first")
        assertTrue(second >= first, "$second is before $first")
    }
}
