package com.jacksonfdam.slipgate.ui.audio

import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_FRAMES = 120
private const val WARMUP_FRAMES = 3
private const val MILLIS = 1_000_000L

class FrameBenchmarkTest {
    @Test
    fun nothingIsDecidedBeforeTheSampleIsComplete() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES + SAMPLE_FRAMES - 1) { benchmark.record(2 * MILLIS) }

        assertFalse(benchmark.isFinished)
        assertNull(benchmark.decide())
    }

    @Test
    fun aFastDeviceLandsOnTheTopTier() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES + SAMPLE_FRAMES) { benchmark.record(2 * MILLIS) }

        val decision = assertNotNull(benchmark.decide())
        assertEquals(QualityTier.Maximum, decision.tier)
        assertEquals(2_000, decision.medianFrameMicros)
    }

    @Test
    fun aSlowDeviceLandsOnTheBottomTier() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES + SAMPLE_FRAMES) { benchmark.record(40 * MILLIS) }

        assertEquals(QualityTier.Minimal, assertNotNull(benchmark.decide()).tier)
    }

    /** One long frame is a hiccup, not a verdict: the median is what decides. */
    @Test
    fun oneStallDoesNotDecideTheTier() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES) { benchmark.record(2 * MILLIS) }
        benchmark.record(500 * MILLIS)
        repeat(SAMPLE_FRAMES - 1) { benchmark.record(2 * MILLIS) }

        assertEquals(QualityTier.Maximum, assertNotNull(benchmark.decide()).tier)
    }

    /** Shader compilation happens in the first frames and would drag a fast device down a tier. */
    @Test
    fun theFirstFramesAreDiscarded() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES) { benchmark.record(200 * MILLIS) }
        repeat(SAMPLE_FRAMES) { benchmark.record(2 * MILLIS) }

        assertEquals(QualityTier.Maximum, assertNotNull(benchmark.decide()).tier)
    }

    @Test
    fun aFrameWithNoDurationIsIgnored() {
        val benchmark = FrameBenchmark()

        repeat(WARMUP_FRAMES + SAMPLE_FRAMES) { benchmark.record(0) }

        assertTrue(!benchmark.isFinished, "zero-length frames were counted")
    }
}
