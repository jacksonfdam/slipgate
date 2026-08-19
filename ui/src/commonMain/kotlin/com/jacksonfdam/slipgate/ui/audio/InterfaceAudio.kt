package com.jacksonfdam.slipgate.ui.audio

import com.jacksonfdam.slipgate.host.audio.AudioOutput
import com.jacksonfdam.slipgate.host.audio.synth.InterfaceCue
import com.jacksonfdam.slipgate.host.audio.synth.InterfaceSynth

private const val MILLIS_PER_SECOND = 1000
private const val MAX_BLOCKS_PER_PUMP = 8
private const val SHORT_MAX = 32767
private const val SHORT_MIN = -32768

/**
 * The interface's own voice: the synthesiser, the device it plays through, and the clock that decides
 * how much to render.
 *
 * Rendering is paid for by elapsed time rather than by frames, the same way the engine's mixer earns
 * its frames: a slow frame owes more audio, a fast one owes less, and neither runs the device dry or
 * overfills it. A ceiling stops a stall from banking a second of sound it would then rush.
 *
 * The synthesiser runs at the device's rate rather than its own default, so the interface and a
 * running gate share one output instead of fighting over two.
 */
public class InterfaceAudio(
    private val output: AudioOutput,
    private val synth: InterfaceSynth = InterfaceSynth(sampleRate = output.sampleRate),
) {
    private val block = ShortArray(InterfaceSynth.BLOCK_FRAMES * InterfaceSynth.CHANNELS)
    private var owedFrames = 0
    private var muted = false

    /** Scales what reaches the device, from silence to full. */
    public var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** Plays [cue]. [direction] flips the sweep on cues that track which way a selection moved. */
    public fun play(
        cue: InterfaceCue,
        direction: Float = 0f,
    ) {
        if (!muted && volume > 0f) {
            synth.trigger(cue, direction)
        }
    }

    /** Stops feeding the device, for while a gate owns the audio. */
    public fun silence() {
        muted = true
        owedFrames = 0
    }

    public fun resume() {
        muted = false
    }

    /**
     * Renders whatever [elapsedMillis] of interface time is worth. Called once per frame; does nothing
     * while silenced, so a running gate has the device to itself.
     */
    public fun pump(elapsedMillis: Long) {
        if (muted) {
            return
        }
        owedFrames += (elapsedMillis * output.sampleRate / MILLIS_PER_SECOND).toInt()
        var blocks = 0
        while (owedFrames >= InterfaceSynth.BLOCK_FRAMES && blocks < MAX_BLOCKS_PER_PUMP) {
            val frames = synth.render(block)
            scale(frames)
            val accepted = output.submit(block, frames)
            if (accepted <= 0) {
                // The device is full: keep what is owed and try again next frame rather than dropping
                // audio, which is what a click sounds like.
                return
            }
            owedFrames -= accepted
            blocks++
        }
        if (blocks == MAX_BLOCKS_PER_PUMP) {
            owedFrames = 0
        }
    }

    /** Applies the volume in place, so a quiet interface costs no extra buffer. */
    private fun scale(frames: Int) {
        if (volume >= 1f) {
            return
        }
        for (index in 0 until frames * InterfaceSynth.CHANNELS) {
            val scaled = (block[index] * volume).toInt().coerceIn(SHORT_MIN, SHORT_MAX)
            block[index] = scaled.toShort()
        }
    }
}
