@file:OptIn(ExperimentalWasmJsInterop::class)

package com.jacksonfdam.slipgate.host.audio

import kotlin.js.ExperimentalWasmJsInterop

private const val SHORT_SCALE = 32768f
private const val SCHEDULING_HORIZON_SECONDS = 0.4

private external class AudioContext : JsAny {
    val currentTime: Double
    val destination: AudioNode
    val state: String

    fun createBuffer(
        channels: Int,
        frames: Int,
        sampleRate: Int,
    ): AudioBuffer

    fun createBufferSource(): AudioBufferSourceNode

    fun resume()
}

private external class AudioNode : JsAny

private external class AudioBuffer : JsAny {
    val duration: Double

    fun getChannelData(channel: Int): Float32Array
}

private external class Float32Array : JsAny

private external class AudioBufferSourceNode : JsAny {
    var buffer: AudioBuffer?

    fun connect(destination: AudioNode)

    fun start(at: Double)
}

// The bodies of these two are JavaScript, which static analysis cannot see reads the parameters.
@Suppress("UnusedParameter")
private fun newAudioContext(sampleRate: Int): AudioContext? =
    js("typeof AudioContext === 'undefined' ? null : new AudioContext({ sampleRate: sampleRate })")

@Suppress("UnusedParameter")
private fun writeSample(
    channelData: Float32Array,
    index: Int,
    value: Float,
) {
    js("channelData[index] = value")
}

/**
 * Plays through Web Audio by scheduling one buffer per submitted batch.
 *
 * A browser gives no pull callback without an audio worklet, so batches are queued back to back
 * against the context's own clock. That is accurate enough for 28 ms slices and keeps the whole
 * path in one file; a worklet becomes worth its plumbing when the browser drives an engine itself.
 */
public actual fun openAudioOutput(
    sampleRate: Int,
    channels: Int,
): AudioOutput {
    val context = newAudioContext(sampleRate) ?: return SilentAudioSink(sampleRate, channels)
    return WebAudioSink(sampleRate, channels, context)
}

private class WebAudioSink(
    override val sampleRate: Int,
    override val channels: Int,
    private val context: AudioContext,
) : AudioOutput {
    private var cursorSeconds = 0.0

    /**
     * Refuses frames once the queue reaches the horizon, so a host that steps faster than real time
     * feels back pressure instead of scheduling minutes of audio into the future.
     */
    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        // A context starts suspended until the page has been interacted with; resuming a running
        // one is harmless, and asking every time is cheaper than tracking the gesture ourselves.
        if (context.state != "running") {
            context.resume()
        }
        val now = context.currentTime
        val startAt = maxOf(now, cursorSeconds)
        if (frameCount <= 0 || startAt - now > SCHEDULING_HORIZON_SECONDS) {
            return 0
        }
        val buffer = context.createBuffer(channels, frameCount, sampleRate)
        for (channel in 0 until channels) {
            val channelData = buffer.getChannelData(channel)
            for (frame in 0 until frameCount) {
                writeSample(channelData, frame, samples[frame * channels + channel] / SHORT_SCALE)
            }
        }
        val source = context.createBufferSource()
        source.buffer = buffer
        source.connect(context.destination)
        source.start(startAt)
        cursorSeconds = startAt + buffer.duration
        return frameCount
    }

    override fun close() {
        cursorSeconds = 0.0
    }
}
