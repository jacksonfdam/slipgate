package com.jacksonfdam.slipgate.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Motion primitives. One easing curve throughout; the launch transition is the single
 * sanctioned exception to the duration ceiling. Ambient shader motion is not timed here —
 * it runs on periods of 8 to 40 seconds so it never reads as a loop.
 */
public object Motion {
    /** The one easing curve used for every interface transition. */
    public val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Ceiling for input feedback — pressed states, ripples, thumb tracking. */
    public const val INPUT_FEEDBACK_MS: Int = 90

    /** Chromatic aberration pulse on selection change. */
    public const val FOCUS_PULSE_MS: Int = 120

    /** Panel and focus transitions sit between these two bounds. */
    public const val PANEL_MS: Int = 240

    /** Upper bound for any non-launch transition. */
    public const val TRANSITION_MAX_MS: Int = 320

    /** The launch transition: the one long move, menu to game. */
    public const val LAUNCH_MS: Int = 900

    /** Cross-fade length used for everything when reduced motion is on. */
    public const val REDUCED_FADE_MS: Int = 120

    /**
     * Effective duration for a transition: the requested time normally, a short
     * cross-fade under reduced motion.
     */
    public fun duration(
        requestedMs: Int,
        reducedMotion: Boolean,
    ): Int = if (reducedMotion) minOf(requestedMs, REDUCED_FADE_MS) else requestedMs
}

/**
 * True when the user asked for reduced motion: ambient shaders freeze to a static composed
 * frame, transitions become 120 ms cross-fades, and parallax is disabled.
 */
public val LocalReducedMotion: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/** Convenience accessor for the reduced-motion setting. */
public val reducedMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalReducedMotion.current
