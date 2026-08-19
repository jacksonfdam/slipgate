package com.jacksonfdam.slipgate.host.audio.synth

/** What a voice oscillates. */
internal enum class Waveform {
    Sine,
    Saw,
    Square,
    Noise,
    PinkNoise,
}

/**
 * Immutable recipe for one triggered voice. All times are in milliseconds; frequency and
 * cutoff sweep linearly from start to end across the voice's whole duration.
 */
@Suppress("LongParameterList") // A voice recipe is one flat set of named knobs, by design.
internal class VoiceProgram(
    val waveform: Waveform,
    val startHz: Float,
    val endHz: Float,
    val amplitude: Float,
    val filterMode: FilterMode = FilterMode.None,
    val cutoffStartHz: Float = 0f,
    val cutoffEndHz: Float = 0f,
    val damping: Float = 1f,
    val attackMs: Float,
    val decayMs: Float,
    val sustain: Float,
    val releaseMs: Float,
    val durationMs: Float,
    val delayMs: Float = 0f,
    val reverbSend: Float = 0f,
    val pan: Float = 0f,
    /** Focus sweeps flip with scroll direction; everything else ignores it. */
    val tracksDirection: Boolean = false,
)

/**
 * One preallocated voice. [start] only assigns fields and [render] only does arithmetic
 * on preallocated state: the audio render path never allocates.
 */
internal class Voice(
    private val sampleRate: Int,
) {
    private val oscillator = PolyBlepOscillator()
    private val white = WhiteNoise(seed = 1)
    private val pink = PinkNoise(white)
    private val filter = StateVariableFilter()
    private val envelope = Adsr()

    private var program: VoiceProgram? = null
    private var frame = 0
    private var durationFrames = 0
    private var delayFrames = 0
    private var inverseDuration = 0f
    private var startHz = 0f
    private var endHz = 0f
    private var cutoffStartHz = 0f
    private var cutoffEndHz = 0f

    var age = 0L
        private set

    val active: Boolean
        get() = program != null

    fun start(
        next: VoiceProgram,
        seed: Int,
        ageCounter: Long,
        direction: Float,
    ) {
        program = next
        age = ageCounter
        frame = 0
        durationFrames = framesFor(next.durationMs)
        delayFrames = framesFor(next.delayMs)
        inverseDuration = if (durationFrames > 0) 1f / durationFrames else 0f
        val flip = next.tracksDirection && direction < 0f
        startHz = if (flip) next.endHz else next.startHz
        endHz = if (flip) next.startHz else next.endHz
        cutoffStartHz = if (flip) next.cutoffEndHz else next.cutoffStartHz
        cutoffEndHz = if (flip) next.cutoffStartHz else next.cutoffEndHz
        oscillator.reset()
        white.reseed(seed)
        filter.reset()
        envelope.start(
            attackFrames = framesFor(next.attackMs).coerceAtLeast(1),
            decayFrames = framesFor(next.decayMs).coerceAtLeast(1),
            sustainLevel = next.sustain,
            releaseFrames = framesFor(next.releaseMs).coerceAtLeast(1),
            totalFrames = durationFrames,
        )
    }

    /** Accumulates [frames] samples into the mix and reverb buses. */
    fun render(
        left: FloatArray,
        right: FloatArray,
        reverbBus: FloatArray,
        frames: Int,
    ) {
        val active = program ?: return
        val leftGain = active.amplitude * (1f - active.pan) * HALF_PLUS
        val rightGain = active.amplitude * (1f + active.pan) * HALF_PLUS
        for (i in 0 until frames) {
            if (delayFrames > 0) {
                delayFrames -= 1
                continue
            }
            if (envelope.finished) {
                program = null
                return
            }
            val progress = frame * inverseDuration
            val sample = oscillate(active, startHz + (endHz - startHz) * progress)
            val cutoff = cutoffStartHz + (cutoffEndHz - cutoffStartHz) * progress
            val filtered =
                filter.process(
                    input = sample,
                    frequency = StateVariableFilter.frequency(cutoff, sampleRate),
                    damping = active.damping,
                    mode = active.filterMode,
                )
            val shaped = filtered * envelope.next()
            left[i] += shaped * leftGain
            right[i] += shaped * rightGain
            reverbBus[i] += shaped * active.reverbSend
            frame += 1
        }
        if (envelope.finished) program = null
    }

    private fun oscillate(
        active: VoiceProgram,
        hz: Float,
    ): Float {
        val increment = hz / sampleRate
        return when (active.waveform) {
            Waveform.Sine -> oscillator.sine(increment)
            Waveform.Saw -> oscillator.saw(increment)
            Waveform.Square -> oscillator.square(increment)
            Waveform.Noise -> white.next()
            Waveform.PinkNoise -> pink.next()
        }
    }

    private fun framesFor(ms: Float): Int = (ms * sampleRate / MS_PER_SECOND).toInt()

    private companion object {
        const val HALF_PLUS = 0.5f
        const val MS_PER_SECOND = 1000f
    }
}
