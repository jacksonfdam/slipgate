package com.jacksonfdam.slipgate.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The chrome palette: near-monochrome and cool, deliberately quiet. The accent is not
 * part of this set because it is not ours to choose — it is sampled at runtime from the
 * selected gate's own palette and provided through [LocalAccentRamp].
 */
public object ColorTokens {
    /** App background. */
    public val Void: Color = Color(0xFF07080B)

    /** Inset wells and the rack background. */
    public val Recess: Color = Color(0xFF0D0F14)

    /** Cards, panels and sheets. */
    public val Surface: Color = Color(0xFF14171F)

    /** Hairlines, one-pixel dividers and card borders. */
    public val Edge: Color = Color(0xFF232833)

    /** Primary text. */
    public val Text: Color = Color(0xFFE8EAF0)

    /** Secondary text and inactive rail icons. */
    public val Muted: Color = Color(0xFF868D9E)
}

/**
 * Three-stop accent ramp. Derived from the mounted gate's palette once game data exists;
 * [Steel] is the only accent the project itself authors, used before any data is mounted.
 */
public data class AccentRamp(
    val dim: Color,
    val base: Color,
    val hot: Color,
) {
    public companion object {
        /** Neutral pre-data fallback. Never themed per game — real palettes override it. */
        public val Steel: AccentRamp =
            AccentRamp(
                dim = Color(0xFF39414F),
                base = Color(0xFF7E8CA3),
                hot = Color(0xFFBCC8DC),
            )
    }
}

/** Accent ramp for the currently focused gate; defaults to the steel fallback. */
public val LocalAccentRamp: ProvidableCompositionLocal<AccentRamp> =
    compositionLocalOf { AccentRamp.Steel }

/** Convenience accessor for the current accent ramp. */
public val accentRamp: AccentRamp
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentRamp.current
