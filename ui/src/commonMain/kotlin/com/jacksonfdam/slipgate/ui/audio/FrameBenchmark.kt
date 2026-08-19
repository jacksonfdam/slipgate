package com.jacksonfdam.slipgate.ui.audio

import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.TierDecision
import com.jacksonfdam.slipgate.host.graphics.core.TierDetection
import com.jacksonfdam.slipgate.host.graphics.core.TierSignals

private const val WARMUP_FRAMES = 3
private const val SAMPLE_FRAMES = 120
private const val MICROS_PER_NANO = 1000L

/**
 * Measures what this device actually does, rather than guessing from its name.
 *
 * The sample is the launcher's own frames while the portraits are running: that is the workload the
 * tier decides for, so measuring it directly beats measuring something else offscreen. The first few
 * frames are discarded because they include shader compilation, which happens once and would drag a
 * fast device down a tier.
 */
public class FrameBenchmark(
    private val backend: GraphicsBackendId? = null,
) {
    private val samples = LongArray(SAMPLE_FRAMES)
    private var taken = 0
    private var warmedUp = 0

    public val isFinished: Boolean
        get() = taken == SAMPLE_FRAMES

    /** Feeds one frame's duration in nanoseconds. Ignores anything after the sample is complete. */
    public fun record(frameNanos: Long) {
        if (frameNanos <= 0) {
            return
        }
        if (warmedUp < WARMUP_FRAMES) {
            warmedUp++
            return
        }
        if (taken < SAMPLE_FRAMES) {
            samples[taken] = frameNanos / MICROS_PER_NANO
            taken++
        }
    }

    /**
     * The tier the measurement implies, or null while the sample is incomplete.
     *
     * The median rather than the mean: one long frame — a garbage collection, another app waking —
     * should not decide what a device is capable of.
     */
    public fun decide(previous: QualityTier? = null): TierDecision? {
        if (!isFinished) {
            return null
        }
        val median = samples.sorted()[SAMPLE_FRAMES / 2]
        return TierDecision(
            tier = TierDetection.detect(medianFrameMicros = median, previous = previous),
            medianFrameMicros = median,
            signals = TierSignals(backend = backend),
        )
    }
}
