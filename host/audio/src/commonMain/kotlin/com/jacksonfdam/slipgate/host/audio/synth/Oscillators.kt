// The polynomial coefficients below are the algorithms themselves (Bhaskara I, PolyBLEP).
@file:Suppress("MagicNumber")

package com.jacksonfdam.slipgate.host.audio.synth

/*
 * Every oscillator here is arithmetic only: +, -, *, / on floats, which IEEE 754 defines
 * exactly. No platform math call whose last bit may differ between targets is allowed in
 * the render path — that determinism is what lets CI pin one golden hash of the rendered
 * audio for every platform.
 */

/** Sine lookup table built once from a polynomial approximation; linear interpolation. */
internal object SineTable {
    private const val SIZE = 2048
    private const val PI_F = 3.1415927f
    private val table =
        FloatArray(SIZE + 1) { i ->
            val turns = i.toFloat() / SIZE
            if (turns <= 0.5f) approx(turns * 2f * PI_F) else -approx((turns - 0.5f) * 2f * PI_F)
        }

    /** Bhaskara I's approximation on [0, pi]: pure arithmetic, within 0.2% of sine. */
    private fun approx(x: Float): Float {
        val spread = x * (PI_F - x)
        return 16f * spread / (5f * PI_F * PI_F - 4f * spread)
    }

    /** [phase] in turns, 0..1. */
    fun sample(phase: Float): Float {
        val position = phase * SIZE
        val index = position.toInt()
        val fraction = position - index
        val a = table[index]
        return a + (table[index + 1] - a) * fraction
    }
}

/**
 * PolyBLEP oscillator: a naive sawtooth or square with the step discontinuities smoothed
 * by a two-sample polynomial band-limiting kernel.
 */
internal class PolyBlepOscillator {
    private var phase = 0f

    fun reset() {
        phase = 0f
    }

    fun saw(phaseIncrement: Float): Float {
        val value = 2f * phase - 1f - blep(phase, phaseIncrement)
        advance(phaseIncrement)
        return value
    }

    fun square(phaseIncrement: Float): Float {
        val naive = if (phase < 0.5f) 1f else -1f
        var value = naive + blep(phase, phaseIncrement)
        var shifted = phase + 0.5f
        if (shifted >= 1f) shifted -= 1f
        value -= blep(shifted, phaseIncrement)
        advance(phaseIncrement)
        return value
    }

    fun sine(phaseIncrement: Float): Float {
        val value = SineTable.sample(phase)
        advance(phaseIncrement)
        return value
    }

    private fun advance(phaseIncrement: Float) {
        phase += phaseIncrement
        if (phase >= 1f) phase -= 1f
    }

    private fun blep(
        t: Float,
        dt: Float,
    ): Float =
        when {
            dt <= 0f -> {
                0f
            }

            t < dt -> {
                val x = t / dt
                x + x - x * x - 1f
            }

            t > 1f - dt -> {
                val x = (t - 1f) / dt
                x * x + x + x + 1f
            }

            else -> {
                0f
            }
        }
}
