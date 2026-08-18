package com.jacksonfdam.slipgate.host.graphics.core

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Size of a drawable surface, in physical pixels. */
public data class SurfaceSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width >= 0 && height >= 0) { "a surface cannot have a negative size" }
    }

    public val isEmpty: Boolean
        get() = width == 0 || height == 0
}

/** Destination rectangle a frame is drawn into, in physical pixels. */
public data class ViewportRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** How a frame is fitted to the surface. */
public enum class ScalingMode {
    /** Largest size that fits while preserving the frame's aspect ratio. */
    Fit,

    /** Largest whole-number multiple of the frame that fits. Ignores pixel aspect. */
    IntegerScale,

    /** Fill the surface, aspect ratio be damned. */
    Stretch,
}

/**
 * Ratio of a source pixel's width to its height in the id Tech 1 games. They render 320x200
 * for a 4:3 display, so each pixel is 1.2 times taller than it is wide; presenting them as
 * squares makes everything look squashed.
 */
public const val ID_TECH_1_PIXEL_ASPECT: Float = 5f / 6f

/**
 * Geometry of a presented frame.
 *
 * [pixelAspect] is the width-to-height ratio of one source pixel: 1 for square pixels, and
 * [ID_TECH_1_PIXEL_ASPECT] for the Doom-family engines.
 */
public data class Viewport(
    val source: DisplayFormat,
    val surface: SurfaceSize,
    val mode: ScalingMode = ScalingMode.Fit,
    val pixelAspect: Float = 1f,
) {
    init {
        require(pixelAspect > 0f) { "pixel aspect must be positive" }
    }

    /** Where the frame lands on the surface, centred, or an empty rect for an empty surface. */
    public fun destination(): ViewportRect {
        if (surface.isEmpty) {
            return ViewportRect(x = 0, y = 0, width = 0, height = 0)
        }
        val (width, height) =
            when (mode) {
                ScalingMode.Stretch -> surface.width to surface.height
                ScalingMode.Fit -> fitted()
                ScalingMode.IntegerScale -> integerScaled()
            }
        return ViewportRect(
            x = (surface.width - width) / 2,
            y = (surface.height - height) / 2,
            width = width,
            height = height,
        )
    }

    private fun fitted(): Pair<Int, Int> {
        val correctedWidth = source.width * pixelAspect
        val scale = min(surface.width / correctedWidth, surface.height.toFloat() / source.height)
        return (correctedWidth * scale).roundToInt() to (source.height * scale).roundToInt()
    }

    private fun integerScaled(): Pair<Int, Int> {
        val scale =
            floor(
                min(
                    surface.width.toFloat() / source.width,
                    surface.height.toFloat() / source.height,
                ),
            ).toInt().coerceAtLeast(1)
        return source.width * scale to source.height * scale
    }
}
