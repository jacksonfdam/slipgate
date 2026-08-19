package com.jacksonfdam.slipgate.host.graphics.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierDetectionTest {
    @Test
    fun mapsTheMedianOntoTiers() {
        assertEquals(QualityTier.Maximum, TierDetection.detect(medianFrameMicros = 2_000))
        assertEquals(QualityTier.Enhanced, TierDetection.detect(medianFrameMicros = 6_000))
        assertEquals(QualityTier.Standard, TierDetection.detect(medianFrameMicros = 12_000))
        assertEquals(QualityTier.Minimal, TierDetection.detect(medianFrameMicros = 30_000))
    }

    @Test
    fun aMedianOnTheBoundaryKeepsThePreviousTier() {
        // 8_600 is just past the Enhanced/Standard boundary; without a previous tier it
        // reads Standard, but a device already on Enhanced stays there.
        assertEquals(QualityTier.Standard, TierDetection.detect(medianFrameMicros = 8_600))
        assertEquals(
            QualityTier.Enhanced,
            TierDetection.detect(medianFrameMicros = 8_600, previous = QualityTier.Enhanced),
        )
    }

    @Test
    fun aClearCrossingOverridesThePreviousTier() {
        assertEquals(
            QualityTier.Minimal,
            TierDetection.detect(medianFrameMicros = 40_000, previous = QualityTier.Enhanced),
        )
        assertEquals(
            QualityTier.Maximum,
            TierDetection.detect(medianFrameMicros = 1_500, previous = QualityTier.Standard),
        )
    }

    @Test
    fun hysteresisWorksInBothDirections() {
        // Just under the Maximum boundary: fresh reading says Maximum, but a device on
        // Enhanced stays put until the crossing is clear.
        assertEquals(
            QualityTier.Enhanced,
            TierDetection.detect(medianFrameMicros = 3_900, previous = QualityTier.Enhanced),
        )
        assertEquals(
            QualityTier.Maximum,
            TierDetection.detect(medianFrameMicros = 3_000, previous = QualityTier.Enhanced),
        )
    }

    @Test
    fun samplerDiscardsWarmupAndAnswersWithTheMedian() {
        val sampler = FrameTimeSampler(discardFirst = 3, capacity = 16)
        listOf(99_000L, 99_000L, 99_000L, 10L, 30L, 20L).forEach(sampler::add)
        assertEquals(3, sampler.sampleCount)
        assertEquals(20L, sampler.median())
    }

    @Test
    fun samplerIsSilentUntilItHasData() {
        val sampler = FrameTimeSampler()
        assertNull(sampler.median())
        sampler.add(5_000)
        sampler.add(5_000)
        sampler.add(5_000)
        assertNull(sampler.median())
    }
}
