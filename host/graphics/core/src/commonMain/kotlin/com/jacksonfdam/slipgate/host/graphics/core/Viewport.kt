package com.jacksonfdam.slipgate.host.graphics.core

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
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

    /**
     * [Fit]'s rectangle, upscaled through the edge-adaptive shader rather than by interpolation.
     *
     * A separate mode rather than a switch beside the others because it is a choice a player makes
     * once and sees everywhere: whole pixels, honest pixels, or smooth edges. A backend with no
     * runtime effect draws it as [Fit], which is the same rectangle and a softer picture.
     */
    SharpUpscale,
}

/**
 * Geometry of a presented frame.
 *
 * [pixelAspect] defaults to the source format's own ratio and can be overridden for a
 * correction the gate does not declare. [ID_TECH_1_PIXEL_ASPECT] is the Doom-family value.
 */
public data class Viewport(
    val source: DisplayFormat,
    val surface: SurfaceSize,
    val mode: ScalingMode = ScalingMode.Fit,
    val pixelAspect: Float = source.pixelAspect,
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

                // The same rectangle as Fit: the difference is in how the frame is sampled into it.
                ScalingMode.SharpUpscale -> fitted()
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
