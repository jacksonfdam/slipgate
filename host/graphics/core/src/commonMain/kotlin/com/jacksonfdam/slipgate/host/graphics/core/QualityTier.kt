package com.jacksonfdam.slipgate.host.graphics.core

/**
 * How much the interface is allowed to spend. One tier drives everything — shader
 * parameters through the shared uniform block, post-processing, ambient voice count —
 * so there is one dial, never a matrix of toggles fighting each other.
 */
public enum class QualityTier(
    /** Fraction of the surface the gate renders at before upscaling. */
    public val renderScale: Float,
    /** Noise octaves the portrait shaders may evaluate; zero freezes them to a still. */
    public val portraitOctaves: Int,
    /** Whether portraits may raymarch at all, and the interface may bloom. */
    public val raymarch: Boolean,
    /** Voices the ambient bed may hold. */
    public val ambientVoices: Int,
    /** The frame budget the tier promises to stay inside, in microseconds. */
    public val frameBudgetMicros: Long,
) {
    /** Static composed portraits, no post chain, 30 fps floor. */
    Minimal(
        renderScale = 0.6f,
        portraitOctaves = 0,
        raymarch = false,
        ambientVoices = 0,
        frameBudgetMicros = 33_333,
    ),

    /** Two noise octaves, scanlines only, 60 fps. */
    Standard(
        renderScale = 0.8f,
        portraitOctaves = 2,
        raymarch = false,
        ambientVoices = 2,
        frameBudgetMicros = 16_667,
    ),

    /** Four octaves, short raymarch, full CRT and upscaler, 60 fps. */
    Enhanced(
        renderScale = 1f,
        portraitOctaves = 4,
        raymarch = true,
        ambientVoices = 4,
        frameBudgetMicros = 16_667,
    ),

    /** Six octaves, full raymarch and grain; budget follows the display's refresh. */
    Maximum(
        renderScale = 1f,
        portraitOctaves = 6,
        raymarch = true,
        ambientVoices = 6,
        frameBudgetMicros = 16_667,
    ),
}
