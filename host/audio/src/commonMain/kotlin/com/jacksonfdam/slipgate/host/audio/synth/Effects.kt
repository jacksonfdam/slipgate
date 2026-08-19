package com.jacksonfdam.slipgate.host.audio.synth

/** Cubic-rational soft clipper, a Padé-style tanh stand-in with no transcendental call. */
@Suppress("MagicNumber") // The Padé coefficients are the algorithm.
internal fun softClip(input: Float): Float {
    val x =
        if (input > 3f) {
            3f
        } else if (input < -3f) {
            -3f
        } else {
            input
        }
    val square = x * x
    return x * (27f + square) / (27f + 9f * square)
}

/** Fixed-capacity circular delay; capacity is allocated once at construction. */
internal class DelayLine(
    capacity: Int,
) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0

    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }

    fun read(delaySamples: Int): Float {
        var index = writeIndex - delaySamples
        if (index < 0) index += buffer.size
        return buffer[index]
    }

    fun write(value: Float) {
        buffer[writeIndex] = value
        writeIndex += 1
        if (writeIndex == buffer.size) writeIndex = 0
    }
}

/**
 * Four allpass stages in series with a feedback tail: a small, dark interface reverb.
 * Stage lengths are mutually prime so the tail stays diffuse instead of ringing.
 */
internal class AllpassReverb(
    sampleRate: Int,
) {
    private val stages =
        arrayOf(
            AllpassStage(scaled(STAGE_ONE, sampleRate)),
            AllpassStage(scaled(STAGE_TWO, sampleRate)),
            AllpassStage(scaled(STAGE_THREE, sampleRate)),
            AllpassStage(scaled(STAGE_FOUR, sampleRate)),
        )
    private val tail = DelayLine(scaled(TAIL, sampleRate))
    private var tailDelay = scaled(TAIL, sampleRate) - 1

    fun reset() {
        stages.forEach { it.reset() }
        tail.reset()
    }

    fun process(input: Float): Float {
        var value = input + tail.read(tailDelay) * FEEDBACK
        stages.forEach { value = it.process(value) }
        tail.write(value)
        return value
    }

    private companion object {
        // Reference lengths at 48 kHz, mutually prime.
        const val STAGE_ONE = 347
        const val STAGE_TWO = 683
        const val STAGE_THREE = 1109
        const val STAGE_FOUR = 1789
        const val TAIL = 3079
        const val FEEDBACK = 0.42f

        const val REFERENCE_RATE = 48_000L
        const val MIN_LENGTH = 2

        fun scaled(
            reference: Int,
            sampleRate: Int,
        ): Int = (reference.toLong() * sampleRate / REFERENCE_RATE).toInt().coerceAtLeast(MIN_LENGTH)
    }
}

private class AllpassStage(
    length: Int,
) {
    private val delay = DelayLine(length)
    private val delaySamples = length - 1

    fun reset() {
        delay.reset()
    }

    fun process(input: Float): Float {
        val delayed = delay.read(delaySamples)
        val feed = input + delayed * GAIN
        delay.write(feed)
        return delayed - feed * GAIN
    }

    private companion object {
        const val GAIN = 0.5f
    }
}
