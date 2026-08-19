package com.jacksonfdam.slipgate.host.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

private const val BUFFER_MILLIS = 120
private const val MILLIS_PER_SECOND = 1000

/** Plays through an [AudioTrack] in streaming mode, which is the phone's lowest-ceremony output. */
public actual fun openAudioOutput(
    sampleRate: Int,
    channels: Int,
): AudioOutput {
    val channelMask =
        if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    val format =
        AudioFormat
            .Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
    val requested = sampleRate * channels * Short.SIZE_BYTES * BUFFER_MILLIS / MILLIS_PER_SECOND
    val minimum = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
    val track =
        AudioTrack
            .Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(requested, minimum))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
        track.release()
        return SilentAudioSink(sampleRate, channels)
    }
    return AudioTrackSink(sampleRate, channels, track)
}

private class AudioTrackSink(
    override val sampleRate: Int,
    override val channels: Int,
    private val track: AudioTrack,
) : AudioOutput {
    private var playing = false

    /**
     * Starts the track on the first submitted frame. Starting it at construction would have it
     * underrun while the engine boots, and an underrun on some devices is audible as a click.
     */
    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        if (!playing) {
            track.play()
            playing = true
        }
        val written =
            track.write(samples, 0, frameCount * channels, AudioTrack.WRITE_NON_BLOCKING)
        // Negative results are error codes, not counts, and mean the track can take nothing now.
        return if (written > 0) written / channels else 0
    }

    override fun close() {
        if (playing) {
            track.pause()
            track.flush()
        }
        track.release()
    }
}
