package com.jacksonfdam.slipgate.ui.design

/**
 * One wordmark letterform: a set of filled polygons on the wordmark grid, each stored as
 * a flat array of x,y pairs. Pure data so the geometry can be tested without a renderer.
 */
public class WordmarkGlyph(
    public val char: Char,
    public val polygons: List<FloatArray>,
)

/**
 * The SLIPGATE wordmark, authored once as control points. Condensed, flat-sided, sheared
 * terminals, no bevel and no chrome. Every open stroke end is cut on the same forward
 * slant. This is Slipgate's own letterform set — it imitates no existing game logotype.
 *
 * Grid: y runs down, cap height [CAP_HEIGHT], glyph width [GLYPH_WIDTH], stroke 16,
 * shear 6. Horizontal thirds sit at y 0..16, 42..58 and 84..100.
 */
public object Wordmark {
    public const val CAP_HEIGHT: Float = 100f
    public const val GLYPH_WIDTH: Float = 52f
    public const val TRACKING: Float = 14f
    public const val ADVANCE: Float = GLYPH_WIDTH + TRACKING

    private val glyphS =
        WordmarkGlyph(
            'S',
            listOf(
                floatArrayOf(0f, 0f, 46f, 0f, 52f, 16f, 0f, 16f),
                floatArrayOf(0f, 16f, 16f, 16f, 16f, 42f, 0f, 42f),
                floatArrayOf(0f, 42f, 52f, 42f, 52f, 58f, 0f, 58f),
                floatArrayOf(36f, 58f, 52f, 58f, 52f, 84f, 36f, 84f),
                floatArrayOf(0f, 84f, 52f, 84f, 52f, 100f, 6f, 100f),
            ),
        )

    private val glyphL =
        WordmarkGlyph(
            'L',
            listOf(
                floatArrayOf(0f, 6f, 16f, 0f, 16f, 84f, 0f, 84f),
                floatArrayOf(0f, 84f, 46f, 84f, 52f, 100f, 0f, 100f),
            ),
        )

    private val glyphI =
        WordmarkGlyph(
            'I',
            listOf(
                floatArrayOf(18f, 6f, 34f, 0f, 34f, 94f, 18f, 100f),
            ),
        )

    private val glyphP =
        WordmarkGlyph(
            'P',
            listOf(
                floatArrayOf(0f, 0f, 16f, 0f, 16f, 94f, 0f, 100f),
                floatArrayOf(16f, 0f, 52f, 0f, 52f, 16f, 16f, 16f),
                floatArrayOf(36f, 16f, 52f, 16f, 52f, 42f, 36f, 42f),
                floatArrayOf(16f, 42f, 52f, 42f, 52f, 58f, 16f, 58f),
            ),
        )

    private val glyphG =
        WordmarkGlyph(
            'G',
            listOf(
                floatArrayOf(0f, 0f, 46f, 0f, 52f, 16f, 0f, 16f),
                floatArrayOf(0f, 16f, 16f, 16f, 16f, 84f, 0f, 84f),
                floatArrayOf(0f, 84f, 52f, 84f, 52f, 100f, 0f, 100f),
                floatArrayOf(36f, 58f, 52f, 58f, 52f, 84f, 36f, 84f),
                floatArrayOf(20f, 42f, 52f, 42f, 52f, 58f, 26f, 58f),
            ),
        )

    private val glyphA =
        WordmarkGlyph(
            'A',
            listOf(
                floatArrayOf(0f, 0f, 52f, 0f, 52f, 16f, 0f, 16f),
                floatArrayOf(0f, 16f, 16f, 16f, 16f, 94f, 0f, 100f),
                floatArrayOf(36f, 16f, 52f, 16f, 52f, 94f, 36f, 100f),
                floatArrayOf(16f, 58f, 36f, 58f, 36f, 74f, 16f, 74f),
            ),
        )

    private val glyphT =
        WordmarkGlyph(
            'T',
            listOf(
                floatArrayOf(0f, 0f, 46f, 0f, 52f, 16f, 6f, 16f),
                floatArrayOf(18f, 16f, 34f, 16f, 34f, 94f, 18f, 100f),
            ),
        )

    private val glyphE =
        WordmarkGlyph(
            'E',
            listOf(
                floatArrayOf(0f, 0f, 16f, 0f, 16f, 100f, 0f, 100f),
                floatArrayOf(16f, 0f, 46f, 0f, 52f, 16f, 16f, 16f),
                floatArrayOf(16f, 42f, 42f, 42f, 48f, 58f, 16f, 58f),
                floatArrayOf(16f, 84f, 46f, 84f, 52f, 100f, 16f, 100f),
            ),
        )

    /** The eight letterforms, in wordmark order. */
    public val glyphs: List<WordmarkGlyph> =
        listOf(glyphS, glyphL, glyphI, glyphP, glyphG, glyphA, glyphT, glyphE)

    /** Total width of the wordmark on its own grid. */
    public val width: Float = ADVANCE * glyphs.size - TRACKING
}
