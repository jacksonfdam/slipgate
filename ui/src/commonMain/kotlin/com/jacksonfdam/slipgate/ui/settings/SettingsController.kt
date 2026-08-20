package com.jacksonfdam.slipgate.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.TierDecision
import com.jacksonfdam.slipgate.ui.design.Motion

/**
 * The one place a setting changes.
 *
 * Reads once at construction and writes on every change, because there are seven values and a player
 * who toggled something expects it to be true after a crash. The measured tier lives here too: the
 * launcher shows both what was measured and what is being used, since the two differ exactly when the
 * player has overridden it.
 */
public class SettingsController(
    private val store: SettingsStore,
) {
    public var settings: SlipgateSettings by mutableStateOf(store.read())
        private set

    /** What the benchmark decided, or null before it has run. */
    public var measured: TierDecision? by mutableStateOf(null)
        private set

    /** The tier the interface actually draws at: the player's choice, else the measurement. */
    public val activeTier: QualityTier
        get() = settings.qualityOverride ?: measured?.tier ?: QualityTier.Standard

    public fun update(transform: (SlipgateSettings) -> SlipgateSettings) {
        val next = transform(settings)
        if (next != settings) {
            settings = next
            store.write(next)
        }
    }

    /**
     * How long the launch transition runs for, in milliseconds.
     *
     * Here rather than at the call site because it is the one place that knows whether the player asked
     * for reduced motion, and the shell has to wait exactly as long as the warp is on screen.
     */
    public val launchDurationMillis: Int
        get() = Motion.duration(Motion.LAUNCH_MS, settings.reducedMotion)

    /** Records what the benchmark measured. Never changes a choice the player already made. */
    public fun recordMeasurement(decision: TierDecision) {
        measured = decision
    }
}
