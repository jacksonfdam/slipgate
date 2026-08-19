package com.jacksonfdam.slipgate.host.audio

import com.jacksonfdam.slipgate.host.runtime.AudioSink

/** The rate id Tech 1 mixes at, and what every platform sink is opened with by default. */
public const val ID_TECH_1_SAMPLE_RATE: Int = 44100

/** Stereo, because the engine pans its sound effects between two channels. */
public const val ID_TECH_1_CHANNELS: Int = 2

/**
 * A sink that owns platform resources.
 *
 * [close] is separate from the contract in `host/runtime` because a session borrows a sink and must
 * not be able to shut down the device it plays through.
 */
public interface AudioOutput : AudioSink {
    public fun close()
}

/**
 * Opens the platform's audio output.
 *
 * The caller owns the result and must [AudioOutput.close] it. Opening can fail on a device with no
 * output at all, and each platform reports that by returning a [SilentAudioSink] rather than
 * throwing: a game that cannot be heard is still a game that can be played.
 */
public expect fun openAudioOutput(
    sampleRate: Int = ID_TECH_1_SAMPLE_RATE,
    channels: Int = ID_TECH_1_CHANNELS,
): AudioOutput
