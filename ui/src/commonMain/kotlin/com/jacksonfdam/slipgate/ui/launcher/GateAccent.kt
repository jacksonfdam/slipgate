package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.ui.graphics.Color
import com.jacksonfdam.slipgate.host.runtime.AccentSource
import com.jacksonfdam.slipgate.ui.design.AccentRamp

/** What the rack tints a card with before the game's own palette is available. */
public val FALLBACK_ACCENT: Color = AccentRamp.Steel.base

private const val DIM_MIX = 0.5f
private const val HOT_MIX = 0.55f

/**
 * The ramp the interface is drawn in while [card] is focused.
 *
 * The game's own palette wins wherever it has been sampled, which is what makes the launcher look
 * like the game it is about to run. A gate whose accent is a fixed colour keeps that colour, and a
 * gate with no data at all falls back to steel: degraded, not broken.
 */
public fun rampFor(card: GateCard?): AccentRamp {
    val sampled = card?.accent
    if (sampled != null) {
        return AccentRamp(
            dim = Color(sampled.dimArgb),
            base = Color(sampled.baseArgb),
            hot = Color(sampled.hotArgb),
        )
    }
    val fixed = (card?.descriptor?.accent as? AccentSource.Fixed)?.argb
    return if (fixed == null) AccentRamp.Steel else rampAround(Color(fixed))
}

/** The colour a card is drawn in when only its own declaration is available. */
public fun accentOf(source: AccentSource): Color =
    when (source) {
        is AccentSource.Fixed -> Color(source.argb)
        is AccentSource.PaletteEntry -> FALLBACK_ACCENT
    }

/**
 * Builds the three stops around one authored colour, so a gate that names its own accent still has
 * the dim and hot ends the interface draws with. Mixed towards black and white by the same fractions
 * the palette sampler uses, so an authored accent and a sampled one behave alike.
 */
private fun rampAround(base: Color): AccentRamp =
    AccentRamp(
        dim = mix(base, Color.Black, DIM_MIX),
        base = base,
        hot = mix(base, Color.White, HOT_MIX),
    )

private fun mix(
    from: Color,
    towards: Color,
    amount: Float,
): Color =
    Color(
        red = from.red + (towards.red - from.red) * amount,
        green = from.green + (towards.green - from.green) * amount,
        blue = from.blue + (towards.blue - from.blue) * amount,
        alpha = from.alpha,
    )
