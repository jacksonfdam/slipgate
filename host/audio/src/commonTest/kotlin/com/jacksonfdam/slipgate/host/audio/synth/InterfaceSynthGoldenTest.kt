package com.jacksonfdam.slipgate.host.audio.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden audio: render one second of every cue at a fixed seed and hash the PCM. The
 * synthesiser's render path is arithmetic-only, so one hash holds on every platform;
 * a changed hash means a DSP regression that review alone would never see.
 */
class InterfaceSynthGoldenTest {
    @Test
    fun everyCueMatchesItsGoldenHash() {
        val rendered = InterfaceCue.entries.associateWith { cue -> hashOneSecond(cue) }
        val mismatches =
            rendered.entries
                .filter { (cue, hash) -> GOLDEN[cue] != hash }
                .joinToString("\n") { (cue, hash) -> "$cue -> 0x${hash.toString(16)}L," }
        assertTrue(mismatches.isEmpty(), "golden hash mismatch; rendered:\n$mismatches")
    }

    @Test
    fun renderingIsDeterministicForOneSeed() {
        assertEquals(hashOneSecond(InterfaceCue.Boot), hashOneSecond(InterfaceCue.Boot))
    }

    @Test
    fun everyCueActuallyMakesSound() {
        InterfaceCue.entries.forEach { cue ->
            val synth = InterfaceSynth()
            synth.trigger(cue)
            val block = ShortArray(InterfaceSynth.BLOCK_FRAMES * InterfaceSynth.CHANNELS)
            var energy = 0L
            repeat(BLOCKS_PER_SECOND) {
                synth.render(block)
                block.forEach { sample -> energy += sample * sample }
            }
            assertTrue(energy > 0, "$cue rendered a second of silence")
        }
    }

    private fun hashOneSecond(cue: InterfaceCue): Long {
        val synth = InterfaceSynth()
        synth.trigger(cue, direction = 1f)
        val block = ShortArray(InterfaceSynth.BLOCK_FRAMES * InterfaceSynth.CHANNELS)
        var hash = FNV_OFFSET
        repeat(BLOCKS_PER_SECOND) {
            synth.render(block)
            block.forEach { sample ->
                hash = (hash xor (sample.toInt() and 0xFFFF).toLong()) * FNV_PRIME
            }
        }
        return hash
    }

    private companion object {
        const val BLOCKS_PER_SECOND =
            InterfaceSynth.DEFAULT_SAMPLE_RATE / InterfaceSynth.BLOCK_FRAMES + 1
        const val FNV_OFFSET = -0x340D631B7BDDDCDBL
        const val FNV_PRIME = 0x100000001B3L

        val GOLDEN =
            mapOf(
                InterfaceCue.Boot to 0x7527fa48a4c9bf3dL,
                InterfaceCue.Navigate to -0x56d2cbc192464d61L,
                InterfaceCue.Confirm to -0x12fd2b74b2dd7a21L,
                InterfaceCue.Back to -0x7c54d294276348dfL,
                InterfaceCue.Blocked to 0x558bec88c80ab8bdL,
                InterfaceCue.FocusChange to -0x676554ad3fbafa9dL,
                InterfaceCue.Launch to -0x34a4b14582cd49dL,
            )
    }
}
