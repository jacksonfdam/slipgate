package com.jacksonfdam.slipgate.host.audio

/**
 * Accepts everything and plays nothing.
 *
 * Used where audio is unavailable or unwanted: a device with no output, a test, or a gate the
 * player has muted. It accepts every frame on purpose, so a session never sees back pressure that
 * would make it hold audio for a sink that will never take it.
 */
public class SilentAudioSink(
    override val sampleRate: Int = ID_TECH_1_SAMPLE_RATE,
    override val channels: Int = ID_TECH_1_CHANNELS,
) : AudioOutput {
    /** Frames discarded so far, which is what a test asserts on. */
    public var discardedFrames: Long = 0
        private set

    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        discardedFrames += frameCount.toLong()
        return frameCount
    }

    override fun close() {
        discardedFrames = 0
    }
}
