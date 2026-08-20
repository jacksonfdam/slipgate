package com.jacksonfdam.slipgate.ui.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

internal const val GRID: Float = 24f
internal const val STROKE: Float = 2f

/**
 * One icon's control points on the [GRID]-unit grid: open polylines to stroke, closed
 * polygons to fill, and dots stored as x, y, radius triples.
 */
internal class IconSpec(
    val strokes: List<FloatArray> = emptyList(),
    val polygons: List<FloatArray> = emptyList(),
    val dots: List<FloatArray> = emptyList(),
)

/** Exposed for the test that checks the vocabulary and the geometry agree. */
internal val iconSpecs: Map<IconGlyph, IconSpec> =
    mapOf(
        IconGlyph.Gates to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(6f, 21f, 6f, 9f, 12f, 3f, 18f, 9f, 18f, 21f),
                        floatArrayOf(10f, 21f, 10f, 11f, 12f, 9f, 14f, 11f, 14f, 21f),
                    ),
            ),
        IconGlyph.Settings to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 6f, 20f, 6f),
                        floatArrayOf(4f, 12f, 20f, 12f),
                        floatArrayOf(4f, 18f, 20f, 18f),
                    ),
                dots =
                    listOf(
                        floatArrayOf(15f, 6f, 2.4f),
                        floatArrayOf(9f, 12f, 2.4f),
                        floatArrayOf(13f, 18f, 2.4f),
                    ),
            ),
        IconGlyph.Credits to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 6f, 20f, 6f),
                        floatArrayOf(4f, 12f, 16f, 12f),
                        floatArrayOf(4f, 18f, 18f, 18f),
                    ),
            ),
        IconGlyph.Add to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(12f, 5f, 12f, 19f),
                        floatArrayOf(5f, 12f, 19f, 12f),
                    ),
            ),
        IconGlyph.Play to
            IconSpec(
                polygons =
                    listOf(
                        floatArrayOf(8f, 5f, 19f, 12f, 8f, 19f),
                    ),
            ),
        IconGlyph.Back to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(14f, 6f, 8f, 12f, 14f, 18f),
                    ),
            ),
        IconGlyph.Close to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(7f, 7f, 17f, 17f),
                        floatArrayOf(17f, 7f, 7f, 17f),
                    ),
            ),
        // A crosshair, not a gun: the action is aiming and shooting, and every weapon in these games
        // does it from the same place on the screen.
        IconGlyph.Fire to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(12f, 3f, 12f, 8f),
                        floatArrayOf(12f, 16f, 12f, 21f),
                        floatArrayOf(3f, 12f, 8f, 12f),
                        floatArrayOf(16f, 12f, 21f, 12f),
                    ),
                dots = listOf(floatArrayOf(12f, 12f, 2.2f)),
            ),
        // A hand at a door: use is what opens things.
        IconGlyph.Use to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(6f, 20f, 6f, 6f, 15f, 4f, 15f, 20f),
                        floatArrayOf(15f, 12f, 20f, 12f),
                    ),
                dots = listOf(floatArrayOf(12f, 12f, 1.4f)),
            ),
        IconGlyph.Jump to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(12f, 19f, 12f, 6f),
                        floatArrayOf(7f, 11f, 12f, 6f, 17f, 11f),
                        floatArrayOf(6f, 21f, 18f, 21f),
                    ),
            ),
        IconGlyph.Crouch to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(12f, 5f, 12f, 18f),
                        floatArrayOf(7f, 13f, 12f, 18f, 17f, 13f),
                        floatArrayOf(6f, 3f, 18f, 3f),
                    ),
            ),
        IconGlyph.WeaponNext to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(5f, 12f, 17f, 12f),
                        floatArrayOf(12f, 7f, 17f, 12f, 12f, 17f),
                        floatArrayOf(20f, 6f, 20f, 18f),
                    ),
            ),
        IconGlyph.WeaponPrevious to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(19f, 12f, 7f, 12f),
                        floatArrayOf(12f, 7f, 7f, 12f, 12f, 17f),
                        floatArrayOf(4f, 6f, 4f, 18f),
                    ),
            ),
        // A folded map: two panels and the crease between them.
        IconGlyph.Map to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 7f, 10f, 5f, 14f, 7f, 20f, 5f, 20f, 17f, 14f, 19f, 10f, 17f, 4f, 19f, 4f, 7f),
                        floatArrayOf(10f, 5f, 10f, 17f),
                        floatArrayOf(14f, 7f, 14f, 19f),
                    ),
            ),
        IconGlyph.Menu to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(5f, 8f, 19f, 8f),
                        floatArrayOf(5f, 12f, 19f, 12f),
                        floatArrayOf(5f, 16f, 19f, 16f),
                    ),
            ),
        // The return arrow every keyboard has had since the typewriter.
        IconGlyph.Enter to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(20f, 6f, 20f, 14f, 6f, 14f),
                        floatArrayOf(11f, 9f, 6f, 14f, 11f, 19f),
                    ),
            ),
        // The inventory: a bar of items with the arrow pointing at the one being reached for.
        IconGlyph.ItemNext to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 17f, 20f, 17f),
                        floatArrayOf(9f, 5f, 14f, 10f, 9f, 15f),
                    ),
                dots = listOf(floatArrayOf(18f, 12f, 1.6f)),
            ),
        IconGlyph.ItemPrevious to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 17f, 20f, 17f),
                        floatArrayOf(15f, 5f, 10f, 10f, 15f, 15f),
                    ),
                dots = listOf(floatArrayOf(6f, 12f, 1.6f)),
            ),
        IconGlyph.ItemUse to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(4f, 17f, 20f, 17f),
                        floatArrayOf(12f, 13f, 12f, 4f),
                        floatArrayOf(8f, 8f, 12f, 4f, 16f, 8f),
                    ),
            ),
        IconGlyph.FlyUp to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(6f, 13f, 12f, 6f, 18f, 13f),
                        floatArrayOf(6f, 19f, 12f, 12f, 18f, 19f),
                    ),
            ),
        IconGlyph.FlyDown to
            IconSpec(
                strokes =
                    listOf(
                        floatArrayOf(6f, 5f, 12f, 12f, 18f, 5f),
                        floatArrayOf(6f, 11f, 12f, 18f, 18f, 11f),
                    ),
            ),
    )

/** Geometry for one glyph on the [GRID]-unit grid; exposed for tests. */
internal fun iconPaths(glyph: IconGlyph): IconPaths {
    val spec = iconSpecs.getValue(glyph)
    val stroke = Path()
    spec.strokes.forEach { stroke.polyline(it, close = false) }
    val fill = Path()
    spec.polygons.forEach { fill.polyline(it, close = true) }
    spec.dots.forEach { dot ->
        val radius = dot[2]
        fill.addOval(Rect(dot[0] - radius, dot[1] - radius, dot[0] + radius, dot[1] + radius))
    }
    return IconPaths(stroke, fill)
}

private fun Path.polyline(
    points: FloatArray,
    close: Boolean,
) {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) {
        lineTo(points[i], points[i + 1])
    }
    if (close) close()
}
