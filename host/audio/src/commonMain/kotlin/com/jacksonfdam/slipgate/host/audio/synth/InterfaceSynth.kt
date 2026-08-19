package com.jacksonfdam.slipgate.host.audio.synth

/**
 * The interface synthesiser: renders every launcher sound from recipes, deterministically
 * from a seed, into caller-supplied buffers. There is no audio file anywhere behind it.
 *
 * The render path allocates nothing: voices, mix buses and the reverb are built once at
 * construction, and [trigger] only assigns fields on a stolen voice. A GC pause in the
 * audio callback is an audible failure, so this is a hard property, not a preference.
 */
public class InterfaceSynth(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val seed: Long = DEFAULT_SEED,
) {
    private val programs = cuePrograms()
    private val voices = Array(VOICE_COUNT) { Voice(sampleRate) }
    private val mixLeft = FloatArray(BLOCK_FRAMES)
    private val mixRight = FloatArray(BLOCK_FRAMES)
    private val reverbBus = FloatArray(BLOCK_FRAMES)
    private val reverb = AllpassReverb(sampleRate)
    private var triggerCount = 0L

    /** Number of voices currently sounding; visible so tests can watch stealing. */
    public val activeVoiceCount: Int
        get() = voices.count { it.active }

    /**
     * Starts a cue. [direction] only matters for cues that track scroll direction, where
     * its sign flips the sweep. Voices are a fixed pool of [VOICE_COUNT]; when all are
     * busy the oldest is stolen.
     */
    public fun trigger(
        cue: InterfaceCue,
        direction: Float = 0f,
    ) {
        val recipe = programs.getValue(cue)
        for (index in recipe.indices) {
            triggerCount += 1
            val voiceSeed = (seed xor (triggerCount * SEED_STRIDE)).toInt()
            stealVoice().start(recipe[index], voiceSeed, triggerCount, direction)
        }
    }

    /**
     * Renders one block of [BLOCK_FRAMES] frames of interleaved stereo 16-bit PCM into
     * [out], which must hold at least [BLOCK_FRAMES] * 2 values. Returns the frame count.
     */
    public fun render(
        out: ShortArray,
        bed: AmbientBed? = null,
    ): Int {
        mixLeft.fill(0f)
        mixRight.fill(0f)
        // The bed goes in before the voices so both share the clipper: an interface that clips when a
        // cue lands over the pads is worse than one that ducks slightly.
        bed?.render(mixLeft, mixRight)
        reverbBus.fill(0f)
        for (index in voices.indices) {
            voices[index].render(mixLeft, mixRight, reverbBus, BLOCK_FRAMES)
        }
        for (i in 0 until BLOCK_FRAMES) {
            val wet = reverb.process(reverbBus[i])
            out[i * 2] = pcm(mixLeft[i] + wet)
            out[i * 2 + 1] = pcm(mixRight[i] + wet)
        }
        return BLOCK_FRAMES
    }

    private fun stealVoice(): Voice {
        var chosen = voices[0]
        for (index in voices.indices) {
            val voice = voices[index]
            if (!voice.active) return voice
            if (voice.age < chosen.age) chosen = voice
        }
        return chosen
    }

    private fun pcm(sample: Float): Short {
        val shaped = softClip(sample) * PCM_SCALE
        val clamped =
            if (shaped > PCM_MAX) {
                PCM_MAX
            } else if (shaped < PCM_MIN) {
                PCM_MIN
            } else {
                shaped
            }
        return clamped.toInt().toShort()
    }

    public companion object {
        public const val DEFAULT_SAMPLE_RATE: Int = 48_000
        public const val CHANNELS: Int = 2
        public const val BLOCK_FRAMES: Int = 256
        public const val VOICE_COUNT: Int = 8
        private const val DEFAULT_SEED = 0x5119A7EL

        // The golden-ratio increment 0x9E3779B97F4A7C15 as a signed literal.
        private const val SEED_STRIDE = -0x61C8864680B583EBL
        private const val PCM_SCALE = 32767f
        private const val PCM_MAX = 32767f
        private const val PCM_MIN = -32768f
    }
}
