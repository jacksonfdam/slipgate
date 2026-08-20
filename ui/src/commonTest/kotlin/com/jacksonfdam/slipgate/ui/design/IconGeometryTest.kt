package com.jacksonfdam.slipgate.ui.design

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every glyph in the vocabulary has geometry, and all of it stays on the grid.
 *
 * The vocabulary and the geometry are two lists that have to agree: a glyph added to the enum without
 * a spec throws the moment something draws it, and on a pad over a running game that is a button that
 * crashes rather than a button that is missing.
 *
 * The control points are checked rather than the built paths, because a `Path` is the platform's own
 * type and this has nothing to do with the platform.
 */
class IconGeometryTest {
    @Test
    fun everyGlyphHasSomethingToDraw() {
        IconGlyph.entries.forEach { glyph ->
            val spec = iconSpecs[glyph]

            assertTrue(spec != null, "$glyph has no geometry at all")
            assertTrue(
                spec.strokes.isNotEmpty() || spec.polygons.isNotEmpty() || spec.dots.isNotEmpty(),
                "$glyph has an empty spec",
            )
        }
    }

    @Test
    fun everyLineIsPairsOfPoints() {
        iconSpecs.forEach { (glyph, spec) ->
            (spec.strokes + spec.polygons).forEach { points ->
                assertTrue(points.size % 2 == 0, "$glyph has an odd coordinate count")
                assertTrue(points.size >= 4, "$glyph has a line with fewer than two points")
            }
        }
    }

    @Test
    fun everyPointSitsOnTheGrid() {
        iconSpecs.forEach { (glyph, spec) ->
            (spec.strokes + spec.polygons).forEach { points ->
                points.forEach { value ->
                    assertTrue(value in 0f..GRID, "$glyph has a point at $value, off the grid")
                }
            }
            spec.dots.forEach { dot ->
                assertTrue(dot.size == 3, "$glyph has a dot that is not x, y, radius")
                assertTrue(dot[0] - dot[2] >= 0f && dot[0] + dot[2] <= GRID, "$glyph has a dot off the grid")
                assertTrue(dot[1] - dot[2] >= 0f && dot[1] + dot[2] <= GRID, "$glyph has a dot off the grid")
            }
        }
    }
}
