package com.jacksonfdam.slipgate.host.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

private const val BITS_PER_SAMPLE = 16
private const val BUFFER_MILLIS = 120
private const val MILLIS_PER_SECOND = 1000
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8

/**
 * Plays through a data line, which is what a desktop test run and a headless build both have.
 *
 * The JVM target exists so the ring buffer and the drain can be exercised where the audio device is
 * real but the platform is not a phone.
 */
@Suppress("SwallowedException") // A machine with no usable output is a fact, not an error to report.
public actual fun openAudioOutput(
    sampleRate: Int,
    channels: Int,
): AudioOutput {
    val format =
        AudioFormat(
            sampleRate.toFloat(),
            BITS_PER_SAMPLE,
            channels,
            true,
            false,
        )
    return try {
        val line = AudioSystem.getSourceDataLine(format)
        val bufferBytes = sampleRate * channels * Short.SIZE_BYTES * BUFFER_MILLIS / MILLIS_PER_SECOND
        line.open(format, bufferBytes)
        line.start()
        SourceDataLineSink(sampleRate, channels, line)
    } catch (unavailable: LineUnavailableException) {
        SilentAudioSink(sampleRate, channels)
    } catch (unsupported: IllegalArgumentException) {
        // Thrown when no mixer offers the format at all, which is the headless case.
        SilentAudioSink(sampleRate, channels)
    }
}

private class SourceDataLineSink(
    override val sampleRate: Int,
    override val channels: Int,
    private val line: SourceDataLine,
) : AudioOutput {
    private var bytes = ByteArray(0)

    /**
     * Writes only what the line can take right now. A blocking write would stall the frame the
     * audio was rendered by, which shows up as a dropped frame rather than as smoother sound.
     */
    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        val frameBytes = channels * Short.SIZE_BYTES
        val acceptedFrames = minOf(frameCount, line.available() / frameBytes)
        if (acceptedFrames <= 0) {
            return 0
        }
        val byteCount = acceptedFrames * frameBytes
        if (bytes.size < byteCount) {
            bytes = ByteArray(byteCount)
        }
        for (index in 0 until acceptedFrames * channels) {
            val sample = samples[index].toInt()
            bytes[index * Short.SIZE_BYTES] = (sample and BYTE_MASK).toByte()
            bytes[index * Short.SIZE_BYTES + 1] = (sample shr BYTE_BITS and BYTE_MASK).toByte()
        }
        return line.write(bytes, 0, byteCount) / frameBytes
    }

    override fun close() {
        line.stop()
        line.flush()
        line.close()
    }
}
