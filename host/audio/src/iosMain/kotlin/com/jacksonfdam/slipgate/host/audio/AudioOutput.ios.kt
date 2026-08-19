package com.jacksonfdam.slipgate.host.audio

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSourceNode
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.AudioBufferList
import platform.darwin.OSStatus
import platform.darwin.noErr

private const val BUFFER_MILLIS = 200
private const val MILLIS_PER_SECOND = 1000
private const val SHORT_SCALE = 32768f

/**
 * Plays through [AVAudioEngine], pulling from a ring buffer.
 *
 * The engine asks for audio on a real-time thread, so the render block only reads what is already
 * buffered and fills the rest with silence. It never allocates and never waits: missing audio is a
 * moment of quiet, while a late render block is a glitch across every sound at once.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun openAudioOutput(
    sampleRate: Int,
    channels: Int,
): AudioOutput {
    val format = AVAudioFormat(sampleRate.toDouble(), channels.toUInt())
    // Ambient, because a game should not silence the music the player already had running.
    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
    AVAudioSession.sharedInstance().setActive(true, null)
    val sink = AvAudioEngineSink(sampleRate, channels, format)
    return if (sink.start()) sink else SilentAudioSink(sampleRate, channels)
}

@OptIn(ExperimentalForeignApi::class)
private class AvAudioEngineSink(
    override val sampleRate: Int,
    override val channels: Int,
    format: AVAudioFormat,
) : AudioOutput {
    private val ring =
        PcmRingBuffer(
            channels = channels,
            capacityFrames = sampleRate * BUFFER_MILLIS / MILLIS_PER_SECOND,
        )
    private val scratch = ShortArray(ring.capacityFrames * channels)
    private val engine = AVAudioEngine()
    private val source =
        AVAudioSourceNode(format = format) { silence, _, frameCount, audioBufferList ->
            render(silence, frameCount.toInt(), audioBufferList)
        }

    fun start(): Boolean {
        engine.attachNode(source)
        engine.connect(source, engine.mainMixerNode, source.outputFormatForBus(0u))
        return engine.startAndReturnError(null)
    }

    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int = ring.write(samples, frameCount)

    override fun close() {
        engine.stop()
        engine.detachNode(source)
        ring.clear()
    }

    /**
     * Fills the engine's float buffers from the ring. The buffers are deinterleaved, one per
     * channel, which is why the interleaved frames are unpacked here rather than copied wholesale.
     */
    private fun render(
        silence: CPointer<BooleanVar>?,
        frameCount: Int,
        audioBufferList: CPointer<AudioBufferList>?,
    ): OSStatus {
        val list = audioBufferList?.pointed ?: return noErr.toInt()
        val served = ring.read(scratch, minOf(frameCount, ring.capacityFrames))
        silence?.pointed?.value = served == 0
        val buffers = list.mBuffers
        for (channel in 0 until list.mNumberBuffers.toInt()) {
            val target = buffers[channel].mData?.reinterpret<FloatVar>() ?: continue
            for (frame in 0 until frameCount) {
                val sample =
                    if (frame < served) {
                        scratch[frame * channels + minOf(channel, channels - 1)] / SHORT_SCALE
                    } else {
                        0f
                    }
                target[frame] = sample
            }
        }
        return noErr.toInt()
    }
}
