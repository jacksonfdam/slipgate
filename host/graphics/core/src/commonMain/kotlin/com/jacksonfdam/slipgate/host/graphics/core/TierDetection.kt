package com.jacksonfdam.slipgate.host.graphics.core

/** Everything the detector looked at, kept so Settings can show its working. */
public data class TierDecision(
    val tier: QualityTier,
    val medianFrameMicros: Long,
    val signals: TierSignals,
)

/** Static device signals recorded alongside the measurement, all optional. */
public data class TierSignals(
    val backend: GraphicsBackendId? = null,
    val displayRefreshHz: Float? = null,
    val deviceRamMb: Int? = null,
    val coreCount: Int? = null,
)

/**
 * Maps the benchmark's median frame time onto a tier. The measurement is of the attract
 * shader offscreen at 1280x720 during the splash, so the boundaries are budgets for that
 * workload, not for a whole frame of interface.
 *
 * Boundaries carry hysteresis: a device sitting on one keeps its previous tier instead of
 * flapping between two on every re-run.
 */
public object TierDetection {
    private const val MAXIMUM_BELOW_MICROS = 4_000L
    private const val ENHANCED_BELOW_MICROS = 8_500L
    private const val STANDARD_BELOW_MICROS = 17_000L

    /** Fraction of a boundary the median must cross before an existing tier changes. */
    private const val HYSTERESIS = 0.15f

    public fun detect(
        medianFrameMicros: Long,
        previous: QualityTier? = null,
    ): QualityTier {
        val fresh = tierFor(medianFrameMicros)
        if (previous == null || fresh == previous) return fresh
        return if (withinHysteresis(medianFrameMicros, previous)) previous else fresh
    }

    private fun tierFor(medianFrameMicros: Long): QualityTier =
        when {
            medianFrameMicros < MAXIMUM_BELOW_MICROS -> QualityTier.Maximum
            medianFrameMicros < ENHANCED_BELOW_MICROS -> QualityTier.Enhanced
            medianFrameMicros < STANDARD_BELOW_MICROS -> QualityTier.Standard
            else -> QualityTier.Minimal
        }

    private fun withinHysteresis(
        medianFrameMicros: Long,
        previous: QualityTier,
    ): Boolean {
        val lower = boundaryBelow(previous)
        val upper = boundaryAbove(previous)
        val lowerGuard = lower?.let { it - (it * HYSTERESIS).toLong() }
        val upperGuard = upper?.let { it + (it * HYSTERESIS).toLong() }
        val aboveLower = lowerGuard == null || medianFrameMicros >= lowerGuard
        val belowUpper = upperGuard == null || medianFrameMicros < upperGuard
        return aboveLower && belowUpper
    }

    /** The boundary a faster median would have to cross to leave [tier] upward. */
    private fun boundaryBelow(tier: QualityTier): Long? =
        when (tier) {
            QualityTier.Maximum -> null
            QualityTier.Enhanced -> MAXIMUM_BELOW_MICROS
            QualityTier.Standard -> ENHANCED_BELOW_MICROS
            QualityTier.Minimal -> STANDARD_BELOW_MICROS
        }

    /** The boundary a slower median would have to cross to leave [tier] downward. */
    private fun boundaryAbove(tier: QualityTier): Long? =
        when (tier) {
            QualityTier.Maximum -> MAXIMUM_BELOW_MICROS
            QualityTier.Enhanced -> ENHANCED_BELOW_MICROS
            QualityTier.Standard -> STANDARD_BELOW_MICROS
            QualityTier.Minimal -> null
        }
}
