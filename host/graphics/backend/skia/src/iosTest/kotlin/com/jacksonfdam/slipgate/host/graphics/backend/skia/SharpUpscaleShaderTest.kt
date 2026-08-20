package com.jacksonfdam.slipgate.host.graphics.backend.skia

import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.host.graphics.core.SurfaceSize
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.graphics.core.ViewportRect
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SOURCE_WIDTH = 16
private const val SOURCE_HEIGHT = 16
private const val SCALE = 4
private const val DESTINATION_WIDTH = SOURCE_WIDTH * SCALE
private const val DESTINATION_HEIGHT = SOURCE_HEIGHT * SCALE
private const val PALETTE_SIZE = 256
private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTES_PER_PIXEL = 4
private const val BLACK_INDEX = 0
private const val WHITE_INDEX = 255

/** Anything this far off flat black or flat white counts as part of a transition. */
private const val FLAT_TOLERANCE = 6

/**
 * A hard edge blown up four times may not smear further than this many destination pixels. Bilinear
 * spreads it across the whole four, which is the softness this mode exists to avoid.
 */
private const val MAXIMUM_EDGE_WIDTH = 2

/**
 * What the edge-adaptive upscaler has to do: keep flat colour exactly, and keep an edge narrow.
 *
 * These are the two failures that matter and they pull against each other. A sharpening pass that
 * rings will fail the flat test; a smooth interpolation will fail the edge test. Measuring both from
 * one rendered frame is what says the kernel is doing its job rather than one of the two easy
 * things.
 *
 * In the iOS source set for the reason [GoldenImageTest] gives: this is where a Skia surface exists
 * in a plain unit test.
 */
class SharpUpscaleShaderTest {
    private val format =
        DisplayFormat(width = SOURCE_WIDTH, height = SOURCE_HEIGHT, pixelFormat = PixelFormat.Indexed8)

    @Test
    fun flatColourSurvivesTheUpscale() {
        val pixels = renderUpscaled(verticalEdge())

        // Well inside each half, away from the edge and the frame's own borders.
        val leftInterior = pixels[row(DESTINATION_HEIGHT / 2) + SCALE * 2]
        val rightInterior = pixels[row(DESTINATION_HEIGHT / 2) + DESTINATION_WIDTH - SCALE * 2]

        assertTrue(
            luminance(leftInterior) <= FLAT_TOLERANCE,
            "flat black came out at ${luminance(leftInterior)}",
        )
        assertTrue(
            luminance(rightInterior) >= BYTE_MASK - FLAT_TOLERANCE,
            "flat white came out at ${luminance(rightInterior)}",
        )
    }

    @Test
    fun aHardEdgeStaysNarrow() {
        val pixels = renderUpscaled(verticalEdge())
        val middle = row(DESTINATION_HEIGHT / 2)

        val transition =
            (0 until DESTINATION_WIDTH).count { column ->
                val value = luminance(pixels[middle + column])
                value > FLAT_TOLERANCE && value < BYTE_MASK - FLAT_TOLERANCE
            }

        // Zero is the right answer for an edge that lands on the source grid: there is nothing to
        // reconstruct, and reproducing it exactly is what a sharp mode should do. What must not
        // happen is a smear, which is what bilinear draws across all four destination pixels.
        assertTrue(
            transition <= MAXIMUM_EDGE_WIDTH,
            "the edge is $transition destination pixels wide, which is a smear rather than an edge",
        )
    }

    @Test
    fun aDiagonalIsSmoothedRatherThanStaircased() {
        val pixels = renderUpscaled(diagonal())

        val intermediate =
            pixels.count { pixel ->
                val value = luminance(pixel)
                value > FLAT_TOLERANCE && value < BYTE_MASK - FLAT_TOLERANCE
            }

        // Nearest-neighbour produces none of these: every pixel is one source pixel or the other,
        // which is exactly the staircase.
        assertTrue(intermediate > DESTINATION_HEIGHT, "only $intermediate pixels landed between the two colours")

        // And the smoothing stays local to the diagonal rather than washing over the picture: a
        // bilinear blow-up puts a gradient across every one of the four destination pixels a source
        // pixel becomes.
        val widest =
            (0 until DESTINATION_HEIGHT).maxOf { y ->
                (0 until DESTINATION_WIDTH).count { x ->
                    val value = luminance(pixels[row(y) + x])
                    value > FLAT_TOLERANCE && value < BYTE_MASK - FLAT_TOLERANCE
                }
            }
        assertTrue(widest <= SCALE, "the diagonal is $widest destination pixels thick at its worst")
    }

    /** Black on the left, white on the right, with the edge on a source pixel boundary. */
    private fun verticalEdge(): PresentedFrame =
        frame { x, _ ->
            if (x <
                SOURCE_WIDTH / 2
            ) {
                BLACK_INDEX
            } else {
                WHITE_INDEX
            }
        }

    /** White below the diagonal, black above it: the case a staircase is visible in. */
    private fun diagonal(): PresentedFrame = frame { x, y -> if (y > x) WHITE_INDEX else BLACK_INDEX }

    private fun frame(index: (Int, Int) -> Int): PresentedFrame {
        val palette =
            IntArray(PALETTE_SIZE) { entry ->
                val grey = if (entry == WHITE_INDEX) BYTE_MASK else 0
                (BYTE_MASK shl ALPHA_SHIFT) or (grey shl RED_SHIFT) or (grey shl GREEN_SHIFT) or grey
            }
        val pixels =
            ByteArray(format.frameSizeBytes) { offset ->
                index(offset % SOURCE_WIDTH, offset / SOURCE_WIDTH).toByte()
            }
        return PresentedFrame(format = format, pixels = pixels, palette = palette)
    }

    private fun renderUpscaled(frame: PresentedFrame): IntArray {
        val viewport =
            Viewport(
                source = format,
                surface = SurfaceSize(DESTINATION_WIDTH, DESTINATION_HEIGHT),
                mode = ScalingMode.SharpUpscale,
                // Square pixels: this test is about the kernel, not about Doom's aspect ratio.
                pixelAspect = 1f,
            )
        val renderer = SkikoBackend(CrtSettings.Off).createRenderer(format) as SkikoFrameRenderer
        renderer.present(frame, viewport)
        val destination = viewport.destination()
        val paint =
            assertNotNull(
                renderer.paintFor(destination, ScalingMode.SharpUpscale),
                "no paint was produced",
            )

        val info = ImageInfo.makeN32(DESTINATION_WIDTH, DESTINATION_HEIGHT, ColorAlphaType.UNPREMUL)
        val surface = Surface.makeRaster(info)
        surface.canvas.drawRect(
            Rect.makeWH(DESTINATION_WIDTH.toFloat(), DESTINATION_HEIGHT.toFloat()),
            paint,
        )
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the rendered surface back")
        val bytes = assertNotNull(bitmap.readPixels(), "the surface produced no pixels")
        return IntArray(DESTINATION_WIDTH * DESTINATION_HEIGHT) { pixel ->
            val offset = pixel * BYTES_PER_PIXEL
            val blue = bytes[offset].toInt() and BYTE_MASK
            val green = bytes[offset + 1].toInt() and BYTE_MASK
            val red = bytes[offset + 2].toInt() and BYTE_MASK
            (BYTE_MASK shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }
    }

    private fun row(y: Int): Int = y * DESTINATION_WIDTH

    /** Grey in, grey out: the frame is built from a greyscale palette, so one channel is enough. */
    private fun luminance(pixel: Int): Int = pixel shr RED_SHIFT and BYTE_MASK
}
