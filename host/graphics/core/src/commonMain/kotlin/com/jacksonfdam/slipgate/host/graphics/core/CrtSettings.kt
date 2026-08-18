package com.jacksonfdam.slipgate.host.graphics.core

/**
 * Strength of each cathode-ray effect, from 0 for off to 1 for as much as the shader offers.
 *
 * Every field is independent, so a backend needs one shader rather than one per combination, and a
 * user who wants scanlines without curvature gets exactly that.
 */
public data class CrtSettings(
    val enabled: Boolean = true,
    val curvature: Float = 0.06f,
    val scanlines: Float = 0.35f,
    val grille: Float = 0.25f,
    val bloom: Float = 0.18f,
    val vignette: Float = 0.35f,
) {
    init {
        require(curvature in 0f..1f) { "curvature must be within 0..1" }
        require(scanlines in 0f..1f) { "scanline strength must be within 0..1" }
        require(grille in 0f..1f) { "grille strength must be within 0..1" }
        require(bloom in 0f..1f) { "bloom strength must be within 0..1" }
        require(vignette in 0f..1f) { "vignette strength must be within 0..1" }
    }

    public companion object {
        /** The tube is on, gently. Strong enough to read as a CRT, mild enough to play through. */
        public val Default: CrtSettings = CrtSettings()

        /** Every effect off, for a straight blit. */
        public val Off: CrtSettings = CrtSettings(enabled = false)
    }
}
