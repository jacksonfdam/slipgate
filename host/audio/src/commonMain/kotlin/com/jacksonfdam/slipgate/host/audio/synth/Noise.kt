package com.jacksonfdam.slipgate.host.audio.synth

/** Deterministic xorshift32 noise source; the same seed renders the same audio anywhere. */
internal class WhiteNoise(
    seed: Int,
) {
    private var state = if (seed == 0) 1 else seed

    fun reseed(seed: Int) {
        state = if (seed == 0) 1 else seed
    }

    /** Uniform in -1..1. */
    @Suppress("MagicNumber") // xorshift32's published shift triple.
    fun next(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x * INV_INT_MAX
    }

    private companion object {
        const val INV_INT_MAX = 1f / Int.MAX_VALUE
    }
}

/**
 * Voss–McCartney pink noise: one white row updates per sample, chosen by the trailing
 * zeroes of a counter, plus a per-sample white component.
 */
internal class PinkNoise(
    private val white: WhiteNoise,
) {
    private val rows = FloatArray(ROWS)
    private var runningSum = 0f
    private var counter = 0

    fun next(): Float {
        counter = (counter + 1) and MASK
        if (counter != 0) {
            val row = counter.countTrailingZeroBits()
            runningSum -= rows[row]
            rows[row] = white.next()
            runningSum += rows[row]
        }
        return (runningSum + white.next()) * SCALE
    }

    private companion object {
        const val ROWS = 12
        const val MASK = (1 shl ROWS) - 1
        const val SCALE = 1f / (ROWS + 1)
    }
}
