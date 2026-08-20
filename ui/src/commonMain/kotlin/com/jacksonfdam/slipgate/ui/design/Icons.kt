package com.jacksonfdam.slipgate.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The interface icon vocabulary. Every glyph is a vector path authored here — no files. */
public enum class IconGlyph {
    Gates,
    Settings,
    Credits,
    Add,
    Play,
    Back,
    Close,

    // The pad's own vocabulary. Drawn on the same grid as the rail's icons, because a button over a
    // game and a button in the chrome are the same interface and a player should not have to learn
    // two alphabets.
    Fire,
    Use,
    Jump,
    Crouch,
    WeaponNext,
    WeaponPrevious,
    Map,
    Menu,
    Enter,
    ItemNext,
    ItemPrevious,
    ItemUse,
    FlyUp,
    FlyDown,
}

/** Stroke and fill geometry for one icon on the 24-unit grid. */
public class IconPaths(
    public val stroke: Path,
    public val fill: Path,
)

/** Draws one icon glyph. Stroked at 2 grid units with flat caps to match the wordmark. */
@Composable
public fun SlipgateIcon(
    glyph: IconGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = ColorTokens.Muted,
) {
    val paths = remember(glyph) { iconPaths(glyph) }
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / GRID
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(paths.stroke, tint, style = Stroke(width = STROKE))
            drawPath(paths.fill, tint)
        }
    }
}
