package com.jacksonfdam.slipgate.ui.design

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordmarkGlyphsTest {
    @Test
    fun spellsSlipgate() {
        assertEquals("SLIPGATE", Wordmark.glyphs.map { it.char }.joinToString(""))
    }

    @Test
    fun everyPolygonIsAClosedRingOfPairs() {
        Wordmark.glyphs.forEach { glyph ->
            assertTrue(glyph.polygons.isNotEmpty(), "${glyph.char} has no polygons")
            glyph.polygons.forEach { points ->
                assertEquals(0, points.size % 2, "${glyph.char} has an odd coordinate count")
                assertTrue(points.size >= 6, "${glyph.char} has a polygon with fewer than 3 points")
            }
        }
    }

    @Test
    fun everyPointSitsOnTheGlyphGrid() {
        Wordmark.glyphs.forEach { glyph ->
            glyph.polygons.forEach { points ->
                for (i in points.indices step 2) {
                    val x = points[i]
                    val y = points[i + 1]
                    assertTrue(
                        x in 0f..Wordmark.GLYPH_WIDTH && y in 0f..Wordmark.CAP_HEIGHT,
                        "${glyph.char} point ($x, $y) leaves the glyph grid",
                    )
                }
            }
        }
    }

    @Test
    fun everyPolygonHasArea() {
        Wordmark.glyphs.forEach { glyph ->
            glyph.polygons.forEach { points ->
                var doubled = 0f
                var i = 0
                while (i < points.size) {
                    val x0 = points[i]
                    val y0 = points[i + 1]
                    val x1 = points[(i + 2) % points.size]
                    val y1 = points[(i + 3) % points.size]
                    doubled += x0 * y1 - x1 * y0
                    i += 2
                }
                assertTrue(abs(doubled) > 1f, "${glyph.char} has a degenerate polygon")
            }
        }
    }

    @Test
    fun wordmarkWidthCoversAllAdvances() {
        val expected = Wordmark.ADVANCE * Wordmark.glyphs.size - Wordmark.TRACKING
        assertEquals(expected, Wordmark.width)
    }
}
