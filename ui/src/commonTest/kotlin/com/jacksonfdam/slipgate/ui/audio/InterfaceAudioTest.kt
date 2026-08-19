package com.jacksonfdam.slipgate.ui.audio

import com.jacksonfdam.slipgate.host.audio.AudioOutput
import com.jacksonfdam.slipgate.host.audio.synth.InterfaceCue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SAMPLE_RATE = 44_100
private const val FRAME_MILLIS = 16L

/** Records what reached the device, and how loud it was. */
private class RecordingOutput(
    private val accept: Boolean = true,
) : AudioOutput {
    override val sampleRate: Int = SAMPLE_RATE
    override val channels: Int = 2

    var frames: Int = 0
        private set

    var peak: Int = 0
        private set

    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        if (!accept) {
            return 0
        }
        frames += frameCount
        for (index in 0 until frameCount * channels) {
            peak = maxOf(peak, abs(samples[index].toInt()))
        }
        return frameCount
    }

    override fun close() = Unit
}

class InterfaceAudioTest {
    @Test
    fun elapsedTimeDecidesHowMuchIsRendered() {
        val output = RecordingOutput()
        val audio = InterfaceAudio(output)

        audio.play(InterfaceCue.Confirm)
        repeat(10) { audio.pump(FRAME_MILLIS) }

        val owed = (10 * FRAME_MILLIS * SAMPLE_RATE / 1000).toInt()
        assertTrue(output.frames > 0, "nothing was rendered")
        assertTrue(output.frames <= owed, "rendered ${output.frames} frames for $owed owed")
        assertTrue(owed - output.frames < 256, "fell ${owed - output.frames} frames behind")
    }

    @Test
    fun aCueIsAudible() {
        val output = RecordingOutput()
        val audio = InterfaceAudio(output)

        audio.play(InterfaceCue.Confirm)
        repeat(20) { audio.pump(FRAME_MILLIS) }

        assertTrue(output.peak > 0, "the cue produced silence")
    }

    @Test
    fun silenceKeepsTheDeviceForAGate() {
        val output = RecordingOutput()
        val audio = InterfaceAudio(output)

        audio.silence()
        audio.play(InterfaceCue.Navigate)
        repeat(10) { audio.pump(FRAME_MILLIS) }

        assertEquals(0, output.frames, "the interface played over a running gate")

        audio.resume()
        audio.play(InterfaceCue.Navigate)
        repeat(10) { audio.pump(FRAME_MILLIS) }
        assertTrue(output.frames > 0, "the interface stayed silent after resuming")
    }

    @Test
    fun volumeScalesWhatReachesTheDevice() {
        val loud = RecordingOutput()
        val quiet = RecordingOutput()

        InterfaceAudio(loud).apply {
            volume = 1f
            play(InterfaceCue.Confirm)
            repeat(20) { pump(FRAME_MILLIS) }
        }
        InterfaceAudio(quiet).apply {
            volume = 0.25f
            play(InterfaceCue.Confirm)
            repeat(20) { pump(FRAME_MILLIS) }
        }

        assertTrue(quiet.peak < loud.peak, "quiet peaked at ${quiet.peak}, loud at ${loud.peak}")
    }

    @Test
    fun atZeroVolumeNothingIsTriggered() {
        val output = RecordingOutput()
        val audio = InterfaceAudio(output)

        audio.volume = 0f
        audio.play(InterfaceCue.Confirm)
        repeat(20) { audio.pump(FRAME_MILLIS) }

        assertEquals(0, output.peak, "silence was not silent")
    }

    /** A full device is back pressure, not a reason to drop audio. */
    @Test
    fun aFullDeviceIsRetriedRatherThanDroppedFrom() {
        val output = RecordingOutput(accept = false)
        val audio = InterfaceAudio(output)

        audio.play(InterfaceCue.Confirm)
        repeat(5) { audio.pump(FRAME_MILLIS) }

        assertEquals(0, output.frames)
    }
}
