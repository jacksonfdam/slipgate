package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.ui.design.IconGlyph

/** The three launcher destinations, in rail order. Four rail items total with the status dot. */
public enum class LauncherSection(
    internal val glyph: IconGlyph,
    internal val label: String,
) {
    Gates(IconGlyph.Gates, "Gates"),
    Settings(IconGlyph.Settings, "Settings"),
    Credits(IconGlyph.Credits, "Credits"),
}
