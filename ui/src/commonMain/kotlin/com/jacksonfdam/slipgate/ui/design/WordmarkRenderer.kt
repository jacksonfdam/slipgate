package com.jacksonfdam.slipgate.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Draws the SLIPGATE wordmark from its control-point letterforms. The caller sets the
 * height; width follows from the wordmark's own aspect ratio.
 */
@Composable
public fun SlipgateWordmark(
    modifier: Modifier = Modifier,
    color: Color = ColorTokens.Text,
) {
    val path = remember { wordmarkPath() }
    Canvas(modifier = modifier.aspectRatio(Wordmark.width / Wordmark.CAP_HEIGHT)) {
        val factor = size.height / Wordmark.CAP_HEIGHT
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

/** The whole wordmark as a single filled path on its authoring grid. */
internal fun wordmarkPath(): Path {
    val path = Path()
    Wordmark.glyphs.forEachIndexed { index, glyph ->
        val offsetX = index * Wordmark.ADVANCE
        glyph.polygons.forEach { points ->
            path.moveTo(offsetX + points[0], points[1])
            for (i in 2 until points.size step 2) {
                path.lineTo(offsetX + points[i], points[i + 1])
            }
            path.close()
        }
    }
    return path
}

/** Single-glyph path, used by tests and by surfaces that animate letters separately. */
internal fun glyphPath(glyph: WordmarkGlyph): Path {
    val path = Path()
    glyph.polygons.forEach { points ->
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) {
            path.lineTo(points[i], points[i + 1])
        }
        path.close()
    }
    return path
}
