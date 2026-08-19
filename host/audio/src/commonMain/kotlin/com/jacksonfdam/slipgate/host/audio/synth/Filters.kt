package com.jacksonfdam.slipgate.host.audio.synth

internal const val TWO_PI = 6.2831855f

/** One-pole smoother usable as a cheap lowpass or, by subtraction, a highpass. */
internal class OnePoleFilter {
    private var memory = 0f

    fun reset() {
        memory = 0f
    }

    fun lowpass(
        input: Float,
        coefficient: Float,
    ): Float {
        memory += coefficient * (input - memory)
        return memory
    }

    fun highpass(
        input: Float,
        coefficient: Float,
    ): Float = input - lowpass(input, coefficient)

    companion object {
        /** Arithmetic-only coefficient: k / (k + 1) tracks the exact 1 - e^-k closely. */
        fun coefficient(
            cutoffHz: Float,
            sampleRate: Int,
        ): Float {
            val k = TWO_PI * cutoffHz / sampleRate
            return k / (k + 1f)
        }
    }
}

/** What a voice takes from its state-variable filter. */
internal enum class FilterMode {
    None,
    Low,
    Band,
    High,
}

/** Chamberlin state-variable filter; drive with a per-sample frequency coefficient. */
internal class StateVariableFilter {
    private var low = 0f
    private var band = 0f

    fun reset() {
        low = 0f
        band = 0f
    }

    fun process(
        input: Float,
        frequency: Float,
        damping: Float,
        mode: FilterMode,
    ): Float {
        val high = input - low - damping * band
        band += frequency * high
        low += frequency * band
        return when (mode) {
            FilterMode.None -> input
            FilterMode.Low -> low
            FilterMode.Band -> band
            FilterMode.High -> high
        }
    }

    companion object {
        private const val MAX_FREQUENCY = 0.9f

        /** Arithmetic-only tuning: 2*pi*fc/fs, clamped where the integrator stays stable. */
        fun frequency(
            cutoffHz: Float,
            sampleRate: Int,
        ): Float {
            val f = TWO_PI * cutoffHz / sampleRate
            return if (f > MAX_FREQUENCY) MAX_FREQUENCY else f
        }
    }
}
