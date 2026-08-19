package com.jacksonfdam.slipgate.host.graphics.core

/**
 * Collects frame times for the benchmark and answers with their median. The first few
 * frames are discarded — they carry shader compilation and cache warmup, not the device.
 */
public class FrameTimeSampler(
    private val discardFirst: Int = DEFAULT_DISCARD,
    capacity: Int = DEFAULT_CAPACITY,
) {
    private val samples = LongArray(capacity)
    private val scratch = LongArray(capacity)
    private var seen = 0
    private var kept = 0

    /** Samples kept so far, after the warmup discard. */
    public val sampleCount: Int
        get() = kept

    public fun add(frameMicros: Long) {
        seen += 1
        if (seen <= discardFirst || kept == samples.size) return
        samples[kept] = frameMicros
        kept += 1
    }

    /** The median of the kept samples, or null while there is nothing to answer with. */
    public fun median(): Long? {
        if (kept == 0) return null
        samples.copyInto(scratch, endIndex = kept)
        scratch.sort(fromIndex = 0, toIndex = kept)
        return scratch[kept / 2]
    }

    public fun reset() {
        seen = 0
        kept = 0
    }

    private companion object {
        const val DEFAULT_DISCARD = 3
        const val DEFAULT_CAPACITY = 512
    }
}
