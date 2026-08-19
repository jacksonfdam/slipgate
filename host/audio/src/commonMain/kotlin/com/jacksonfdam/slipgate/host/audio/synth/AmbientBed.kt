// The constants here are the sound: intervals, detunings, filter sweeps and step lengths.
@file:Suppress("MagicNumber")

package com.jacksonfdam.slipgate.host.audio.synth

/**
 * The bed the launcher sits on: two detuned saws under a slow filter, with a sparse arpeggio over
 * them, in the key the focused gate's palette produced.
 *
 * Generative rather than looped, because a two-second loop is audible within a minute and this plays
 * for as long as somebody is deciding what to play. The periods are long and mutually prime enough
 * that the sweep and the arpeggio never line up the same way twice in a sitting.
 *
 * Zero allocation after construction, like everything else in the render path: a garbage collection
 * inside an audio callback is an audible failure rather than a slow frame.
 */
public class AmbientBed(
    private val sampleRate: Int = InterfaceSynth.DEFAULT_SAMPLE_RATE,
    seed: Long = DEFAULT_SEED,
) {
    private val padA = PolyBlepOscillator()
    private val padB = PolyBlepOscillator()
    private val lead = PolyBlepOscillator()
    private val filter = StateVariableFilter()
    private val random = WhiteNoise(seed.toInt())

    private var key = AmbientKey(rootSemitone = 0, mode = AmbientMode.Aeolian)
    private var voices = 2
    private var sweepPhase = 0f
    private var stepPhase = 0f
    private var leadDegree = 0
    private var leadLevel = 0f
    private var leadSemitone = 0

    init {
        // The bed opens on a note rather than on a bar and a half of pad: whoever just launched the
        // app should hear that it is alive.
        startNote()
    }

    /** Voices the tier allows: zero silences the bed entirely, which is what the lowest tier wants. */
    public fun setVoices(count: Int) {
        voices = count.coerceIn(0, MAX_VOICES)
    }

    /**
     * Changes key. The oscillators keep their phase, so a change of gate slides rather than clicks —
     * the pads are already sounding and only their pitch moves.
     */
    public fun setKey(key: AmbientKey) {
        this.key = key
        // The note that is sounding moves with the key rather than finishing in the old one: a change
        // of gate should be heard as a modulation, not as a note that argues with the pads under it.
        leadSemitone = key.rootSemitone + key.mode.semitoneAt(leadDegree)
    }

    /** Adds one block of the bed into [left] and [right], which already hold the cues. */
    public fun render(
        left: FloatArray,
        right: FloatArray,
    ) {
        if (voices == 0) {
            return
        }
        val root = pitch(key.rootSemitone) * PAD_OCTAVE
        val padIncrement = root / sampleRate
        val detune = padIncrement * DETUNE
        val sweepIncrement = 1f / (SWEEP_SECONDS * sampleRate)
        val stepIncrement = 1f / (STEP_SECONDS * sampleRate)

        for (index in left.indices) {
            sweepPhase = advance(sweepPhase, sweepIncrement)
            stepPhase = advance(stepPhase, stepIncrement)
            if (stepPhase < stepIncrement) {
                startNote()
            }

            val cutoff = CUTOFF_FLOOR + CUTOFF_RANGE * (0.5f + 0.5f * SineTable.sample(sweepPhase))
            val pad = padA.saw(padIncrement) + padB.saw(padIncrement + detune)
            var voice = filter.process(pad * PAD_LEVEL, cutoff, DAMPING, FilterMode.Low)

            if (voices > 2 && leadLevel > 0f) {
                val leadIncrement = pitch(leadSemitone) * LEAD_OCTAVE / sampleRate
                voice += lead.sine(leadIncrement) * leadLevel * LEAD_LEVEL
                leadLevel -= leadLevel * LEAD_DECAY
            }

            // Slightly wider on the right, so the bed has a place rather than sitting in the middle.
            left[index] += voice
            right[index] += voice * WIDTH
        }
    }

    /** Picks the next arpeggio note: mostly stepwise, occasionally a leap, never the same figure. */
    private fun startNote() {
        val roll = random.next()
        leadDegree =
            when {
                roll > 0.6f -> leadDegree + 1
                roll < -0.6f -> leadDegree - 2
                else -> leadDegree + 2
            }
        if (leadDegree > MAX_DEGREE) {
            leadDegree -= key.mode.semitones.size
        }
        if (leadDegree < -MAX_DEGREE) {
            leadDegree += key.mode.semitones.size
        }
        leadSemitone = key.rootSemitone + key.mode.semitoneAt(leadDegree)
        leadLevel = 1f
    }

    /** Equal temperament from the reference pitch, by table rather than by a power function. */
    private fun pitch(semitone: Int): Float {
        val octave = if (semitone >= 0) semitone / 12 else (semitone - 11) / 12
        val step = semitone - octave * 12
        var frequency = REFERENCE_HZ * SEMITONE_RATIOS[step]
        var remaining = octave
        while (remaining > 0) {
            frequency *= 2f
            remaining--
        }
        while (remaining < 0) {
            frequency *= 0.5f
            remaining++
        }
        return frequency
    }

    private fun advance(
        phase: Float,
        increment: Float,
    ): Float {
        val next = phase + increment
        return if (next >= 1f) next - 1f else next
    }

    private companion object {
        const val DEFAULT_SEED = 0x51_9A7EL
        const val MAX_VOICES = 6

        /** A2 is low enough to sit under an interface without muddying the cues. */
        const val REFERENCE_HZ = 110f
        const val PAD_OCTAVE = 1f
        const val LEAD_OCTAVE = 4f

        const val DETUNE = 0.006f
        const val PAD_LEVEL = 0.18f
        const val LEAD_LEVEL = 0.1f
        const val LEAD_DECAY = 0.0004f
        const val WIDTH = 0.85f

        const val CUTOFF_FLOOR = 0.02f
        const val CUTOFF_RANGE = 0.05f
        const val DAMPING = 0.9f

        /** Slow enough that the sweep is felt rather than heard, and never a two-second loop. */
        const val SWEEP_SECONDS = 37f
        const val STEP_SECONDS = 1.7f
        const val MAX_DEGREE = 14

        /** Twelve equal-tempered ratios, so pitch needs no power function in the render path. */
        val SEMITONE_RATIOS =
            floatArrayOf(
                1f,
                1.0594631f,
                1.1224620f,
                1.1892071f,
                1.2599210f,
                1.3348399f,
                1.4142136f,
                1.4983071f,
                1.5874011f,
                1.6817928f,
                1.7817974f,
                1.8877486f,
            )
    }
}
