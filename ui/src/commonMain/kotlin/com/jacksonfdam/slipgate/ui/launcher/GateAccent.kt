package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.ui.graphics.Color
import com.jacksonfdam.slipgate.host.runtime.AccentSource

/** What the rack tints a card with before the game's own palette is available. */
public val FALLBACK_ACCENT: Color = Color(0xFF8A8F98)

/**
 * The colour a card is drawn in.
 *
 * A palette-derived accent needs the game's own data, which a card without data does not have and a
 * card with data has not booted. Reading it from the stored IWAD is the attract background's job;
 * until then a palette accent falls back to something neutral, which is the difference between
 * degraded and broken.
 */
public fun accentOf(source: AccentSource): Color =
    when (source) {
        is AccentSource.Fixed -> Color(source.argb)
        is AccentSource.PaletteEntry -> FALLBACK_ACCENT
    }
