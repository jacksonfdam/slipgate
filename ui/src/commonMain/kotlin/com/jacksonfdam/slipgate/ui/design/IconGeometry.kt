package com.jacksonfdam.slipgate.ui.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

internal const val GRID: Float = 24f
internal const val STROKE: Float = 2f

/**
 * One icon's control points on the [GRID]-unit grid: open polylines to stroke, closed
 * polygons to fill, and dots stored as x, y, radius triples.
 */
private class IconSpec(
    val strokes: List<FloatArray> = emptyList(),
    val polygons: List<FloatArray> = emptyList(),
    val dots: List<FloatArray> = emptyList(),
)

private val iconSpecs: Map<IconGlyph, IconSpec> =
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
