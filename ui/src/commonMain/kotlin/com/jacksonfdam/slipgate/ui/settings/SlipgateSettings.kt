package com.jacksonfdam.slipgate.ui.settings

import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode

/**
 * Everything a player can change, and nothing they cannot.
 *
 * Every field here reaches something: the tier drives the portrait shaders, the tube settings drive
 * the frame the gate renders through, the scaling mode decides how that frame meets the screen, and
 * reduced motion freezes ambient movement. A control that changed nothing would be worse than no
 * control at all, so the sections that have nothing to drive yet say so instead of offering a switch.
 */
public data class SlipgateSettings(
    /**
     * The tier the player chose, or null to keep the measured one.
     *
     * An override persists and is honoured as given: the point of showing someone the measured figure
     * is that they may disagree with it.
     */
    val qualityOverride: QualityTier? = null,
    val crt: CrtSettings = CrtSettings.Default,
    val scaling: ScalingMode = ScalingMode.Fit,
    /** Freezes ambient shader motion and shortens transitions, without breaking the composition. */
    val reducedMotion: Boolean = false,
    /** How loud the interface's own sounds are, from silence to full. */
    val interfaceVolume: Float = 0.7f,
    /**
     * Where this device can reach the player's own game data, or null when it cannot.
     *
     * A beacon address or the library itself; the launcher decides which by what answers. It is a
     * setting rather than a build constant because it is one player's home server, and it is the
     * only field here that reaches the network.
     */
    val libraryAddress: String? = null,
) {
    public companion object {
        public val Default: SlipgateSettings = SlipgateSettings()
    }
}
