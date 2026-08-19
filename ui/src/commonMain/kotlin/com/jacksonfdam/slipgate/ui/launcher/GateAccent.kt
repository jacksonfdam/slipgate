package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.ui.graphics.Color
import com.jacksonfdam.slipgate.host.runtime.AccentSource

/** What a card is tinted with when the game's own palette cannot say. */
public const val FALLBACK_ACCENT_ARGB: Int = 0xFF8A8F98.toInt()

/**
 * Resolves the colour a gate is drawn in.
 *
 * A gate may name a fixed colour or an entry in its own palette. The palette route is the one that
 * matters — it is what makes the menu look like the game it is about to launch — but it needs the
 * game's data, so a gate with nothing installed falls back to something neutral. Degraded, not
 * broken: a card in grey still reads as a card.
 */
public fun accentArgbOf(
    source: AccentSource,
    palette: IntArray?,
): Int =
    when (source) {
        is AccentSource.Fixed -> source.argb
        is AccentSource.PaletteEntry -> palette?.getOrNull(source.index) ?: FALLBACK_ACCENT_ARGB
    }

/** The card's accent as a Compose colour. */
public fun GateCard.accent(): Color = Color(accentArgb)
