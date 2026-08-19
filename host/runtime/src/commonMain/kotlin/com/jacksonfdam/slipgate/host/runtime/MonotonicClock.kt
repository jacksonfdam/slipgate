package com.jacksonfdam.slipgate.host.runtime

import kotlin.time.TimeSource

/**
 * Elapsed time since the clock was made, from the platform's monotonic source.
 *
 * Monotonic rather than wall-clock: a session times its own frames against this, and a clock that
 * can be moved backwards by a time zone or a network sync is a clock that can make a frame owe
 * negative milliseconds. Each session gets its own, so what it reads starts near zero and cannot
 * overflow the engine's own millisecond counter on a device that has been awake for weeks.
 */
public class MonotonicClock : Clock {
    private val start = TimeSource.Monotonic.markNow()

    override fun elapsedMillis(): Long = start.elapsedNow().inWholeMilliseconds
}
