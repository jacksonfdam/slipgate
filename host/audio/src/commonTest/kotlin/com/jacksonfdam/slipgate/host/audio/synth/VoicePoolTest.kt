package com.jacksonfdam.slipgate.host.audio.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoicePoolTest {
    @Test
    fun thePoolNeverExceedsItsFixedSize() {
        val synth = InterfaceSynth()
        repeat(20) { synth.trigger(InterfaceCue.Launch) }
        assertTrue(synth.activeVoiceCount <= InterfaceSynth.VOICE_COUNT)
        assertEquals(InterfaceSynth.VOICE_COUNT, synth.activeVoiceCount)
    }

    @Test
    fun voicesFreeThemselvesWhenTheirCueEnds() {
        val synth = InterfaceSynth()
        synth.trigger(InterfaceCue.Navigate)
        assertTrue(synth.activeVoiceCount > 0)
        val block = ShortArray(InterfaceSynth.BLOCK_FRAMES * InterfaceSynth.CHANNELS)
        val oneSecond = InterfaceSynth.DEFAULT_SAMPLE_RATE / InterfaceSynth.BLOCK_FRAMES + 1
        repeat(oneSecond) { synth.render(block) }
        assertEquals(0, synth.activeVoiceCount)
    }

    @Test
    fun stealingKeepsTheNewestVoices() {
        val synth = InterfaceSynth()
        repeat(InterfaceSynth.VOICE_COUNT) { synth.trigger(InterfaceCue.Navigate) }
        assertEquals(InterfaceSynth.VOICE_COUNT, synth.activeVoiceCount)
        // One more trigger steals the oldest voice instead of failing or growing the pool.
        synth.trigger(InterfaceCue.Navigate)
        assertEquals(InterfaceSynth.VOICE_COUNT, synth.activeVoiceCount)
    }
}
