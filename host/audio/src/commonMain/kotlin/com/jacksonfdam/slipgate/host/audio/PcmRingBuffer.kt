package com.jacksonfdam.slipgate.host.audio

import kotlin.concurrent.Volatile

/**
 * A fixed-size buffer between the thread that renders audio and the one that plays it.
 *
 * One producer and one consumer only. That restriction is what lets the indices be plain counters
 * with no lock: a producer that only advances [written] and a consumer that only advances [read]
 * never need to agree on anything but the value each publishes, and marking both volatile is
 * enough for that. A lock here would be worse than a glitch, because the consumer is a real-time
 * audio callback that must never wait for anyone.
 *
 * Counters grow instead of wrapping so that a full buffer is distinguishable from an empty one
 * without keeping a third field the two threads would both have to write.
 */
public class PcmRingBuffer(
    public val channels: Int,
    public val capacityFrames: Int,
) {
    init {
        require(channels > 0) { "a buffer needs at least one channel; got $channels" }
        require(capacityFrames > 0) { "a buffer needs room for a frame; got $capacityFrames" }
    }

    private val samples = ShortArray(capacityFrames * channels)

    @Volatile
    private var written = 0L

    @Volatile
    private var read = 0L

    /** Frames waiting to be played. */
    public val availableFrames: Int
        get() = ((written - read) / channels).toInt()

    /** Frames the buffer can still accept. */
    public val freeFrames: Int
        get() = capacityFrames - availableFrames

    /**
     * Copies up to [frameCount] interleaved frames out of [source] and returns how many were
     * taken. A short result means the consumer is behind, which the caller should treat as
     * back pressure rather than as an error.
     */
    public fun write(
        source: ShortArray,
        frameCount: Int,
    ): Int {
        require(frameCount >= 0) { "frame count must not be negative; got $frameCount" }
        require(frameCount * channels <= source.size) {
            "asked to write $frameCount frames from a buffer holding ${source.size / channels}"
        }
        val accepted = minOf(frameCount, freeFrames)
        copy(source = source, destination = samples, count = accepted * channels, cursor = written)
        written += accepted.toLong() * channels
        return accepted
    }

    /**
     * Fills up to [frameCount] frames of [destination] and returns how many arrived. Anything
     * beyond the returned count is left untouched, so a caller that must produce silence writes
     * it itself and knows it did.
     */
    public fun read(
        destination: ShortArray,
        frameCount: Int,
    ): Int {
        require(frameCount >= 0) { "frame count must not be negative; got $frameCount" }
        require(frameCount * channels <= destination.size) {
            "asked to read $frameCount frames into a buffer holding ${destination.size / channels}"
        }
        val served = minOf(frameCount, availableFrames)
        copy(source = samples, destination = destination, count = served * channels, cursor = read)
        read += served.toLong() * channels
        return served
    }

    /** Drops everything buffered. Called when playback stops, so stale audio never resumes. */
    public fun clear() {
        read = written
    }

    /**
     * Moves [count] samples between a linear buffer and the ring, wrapping at the end of the ring.
     * Which side is the ring is decided by which array is [samples].
     */
    private fun copy(
        source: ShortArray,
        destination: ShortArray,
        count: Int,
        cursor: Long,
    ) {
        val start = (cursor % samples.size).toInt()
        val firstPart = minOf(count, samples.size - start)
        if (source === samples) {
            source.copyInto(destination, destinationOffset = 0, startIndex = start, endIndex = start + firstPart)
            source.copyInto(destination, destinationOffset = firstPart, startIndex = 0, endIndex = count - firstPart)
        } else {
            source.copyInto(destination, destinationOffset = start, startIndex = 0, endIndex = firstPart)
            source.copyInto(destination, destinationOffset = 0, startIndex = firstPart, endIndex = count)
        }
    }
}
