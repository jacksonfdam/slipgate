package com.jacksonfdam.slipgate.host.audio.synth

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BLOCK = 256
private const val DOOM_RED = 0xFFFF0000.toInt()
private const val HEXEN_BLUE = 0xFF3050C0.toInt()

private fun renderSeconds(
    bed: AmbientBed,
    seconds: Float,
    sampleRate: Int = InterfaceSynth.DEFAULT_SAMPLE_RATE,
): Pair<FloatArray, Float> {
    val left = FloatArray(BLOCK)
    val right = FloatArray(BLOCK)
    var peak = 0f
    val blocks = (seconds * sampleRate / BLOCK).toInt()
    repeat(blocks) {
        left.fill(0f)
        right.fill(0f)
        bed.render(left, right)
        for (index in left.indices) {
            peak = maxOf(peak, abs(left[index]))
        }
    }
    return left to peak
}

class AmbientBedTest {
    @Test
    fun theBedSoundsAtTheDefaultVoiceCount() {
        val (_, peak) = renderSeconds(AmbientBed(), seconds = 1f)

        assertTrue(peak > 0.001f, "the bed rendered near silence: peak $peak")
    }

    /** The lowest tier asks for no ambient voices at all, and gets exactly that. */
    @Test
    fun zeroVoicesIsSilence() {
        val bed = AmbientBed().apply { setVoices(0) }

        val (_, peak) = renderSeconds(bed, seconds = 1f)

        assertEquals(0f, peak)
    }

    @Test
    fun theBedAddsToWhatIsAlreadyInTheBuffer() {
        val bed = AmbientBed()
        val left = FloatArray(BLOCK) { 0.5f }
        val right = FloatArray(BLOCK) { 0.5f }

        bed.render(left, right)

        // The filter starts from rest, so early samples barely move; what matters is that the buffer
        // was added to rather than overwritten.
        assertTrue(left.any { it != 0.5f }, "the bed contributed nothing")
        assertTrue(left.all { it > 0f }, "the bed swamped what was already there")
    }

    /** Same seed, same sound: the golden audio test depends on this being true of the whole synth. */
    @Test
    fun twoBedsWithTheSameSeedAgree() {
        val first = renderSeconds(AmbientBed(seed = 42), seconds = 0.5f).first
        val second = renderSeconds(AmbientBed(seed = 42), seconds = 0.5f).first

        assertTrue(first.contentEquals(second), "the bed is not deterministic")
    }

    /**
     * The mode is the mood: a gate in Phrygian dominant should not sound like one in Locrian, which is
     * the whole reason each gate names its own.
     */
    @Test
    fun aDifferentModeIsADifferentSound() {
        val mars =
            AmbientBed(seed = 7).apply {
                setVoices(4)
                setKey(AmbientKey(rootSemitone = 3, mode = AmbientMode.PhrygianDominant))
            }
        val chthon =
            AmbientBed(seed = 7).apply {
                setVoices(4)
                setKey(AmbientKey(rootSemitone = 3, mode = AmbientMode.Locrian))
            }

        val first = renderSeconds(mars, seconds = 1f).first
        val second = renderSeconds(chthon, seconds = 1f).first

        assertTrue(!first.contentEquals(second), "two modes produced the same sound")
    }

    /** Two voices is pads only, which is what the middle tier asks for; the arpeggio needs a third. */
    @Test
    fun theArpeggioNeedsMoreThanTwoVoices() {
        val pads = renderSeconds(AmbientBed(seed = 7).apply { setVoices(2) }, seconds = 1f).first
        val withLead = renderSeconds(AmbientBed(seed = 7).apply { setVoices(4) }, seconds = 1f).first

        assertTrue(!pads.contentEquals(withLead), "the arpeggio added nothing")
    }

    /** A palette that recolours the interface also transposes it. */
    @Test
    fun theKeyFollowsTheAccentHue() {
        val doom = ambientKeyFor(DOOM_RED, AmbientMode.PhrygianDominant)
        val hexen = ambientKeyFor(HEXEN_BLUE, AmbientMode.Aeolian)

        assertEquals(0, doom.rootSemitone, "red should sit at the root of the wheel")
        assertTrue(hexen.rootSemitone > doom.rootSemitone, "blue did not transpose away from red")
    }

    @Test
    fun aGreyAccentStillHasAKey() {
        assertEquals(0, ambientKeyFor(0xFF808080.toInt(), AmbientMode.Dorian).rootSemitone)
    }

    @Test
    fun aModeWalksIntoTheOctaveAbove() {
        val mode = AmbientMode.Aeolian

        assertEquals(0, mode.semitoneAt(0))
        assertEquals(12, mode.semitoneAt(mode.semitones.size))
        assertEquals(-12, mode.semitoneAt(-mode.semitones.size))
    }

    @Test
    fun changingKeyDoesNotSilenceTheBed() {
        val bed = AmbientBed()
        renderSeconds(bed, seconds = 0.5f)

        bed.setKey(ambientKeyFor(HEXEN_BLUE, AmbientMode.Aeolian))
        val (_, peak) = renderSeconds(bed, seconds = 0.5f)

        assertTrue(peak > 0.001f, "the bed went quiet after changing key")
    }
}
