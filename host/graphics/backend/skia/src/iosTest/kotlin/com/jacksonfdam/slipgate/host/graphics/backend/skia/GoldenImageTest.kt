package com.jacksonfdam.slipgate.host.graphics.backend.skia

import com.jacksonfdam.slipgate.host.graphics.backend.classic.ClassicBackend
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 16
private const val HEIGHT = 8
private const val PALETTE_SIZE = 256
private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_TOLERANCE = 2
private const val BYTES_PER_PIXEL = 4

/**
 * Renders the same frame through the CPU path and the shader path and requires them to agree.
 *
 * This is the test that stops the two from drifting: they share a palette definition and a source
 * frame, so any difference is a bug in one of them rather than a property of the dialect. Only the
 * palette pass is compared — the cathode ray pass is deliberately not a straight blit, so there is
 * nothing for it to agree with.
 *
 * It lives in the iOS source set because that is where a Skia surface can be created in a plain
 * unit test. The AGSL path needs an instrumented test on a device to be compared the same way.
 */
class GoldenImageTest {
    private val format =
        DisplayFormat(width = WIDTH, height = HEIGHT, pixelFormat = PixelFormat.Indexed8)
    private val destination = ViewportRect(x = 0, y = 0, width = WIDTH, height = HEIGHT)
    private val viewport = Viewport(source = format, surface = SurfaceSize(WIDTH, HEIGHT))

    @Test
    fun theShaderPathMatchesTheCpuPath() {
        val frame = testFrame()

        val cpuPixels = renderThroughClassic(frame)
        val shaderPixels = renderThroughSkia(frame)

        assertEquals(cpuPixels.size, shaderPixels.size)
        val worst = cpuPixels.indices.maxOf { channelDistance(cpuPixels[it], shaderPixels[it]) }
        assertTrue(
            worst <= CHANNEL_TOLERANCE,
            "backends disagree by $worst per channel, tolerance is $CHANNEL_TOLERANCE",
        )
    }

    @Test
    fun theTestFrameActuallyExercisesThePalette() {
        val frame = testFrame()

        val distinct = renderThroughClassic(frame).toSet()

        assertTrue(distinct.size > 1, "a single-colour frame would pass any comparison")
    }

    private fun testFrame(): PresentedFrame {
        val palette =
            IntArray(PALETTE_SIZE) { entry ->
                val red = entry
                val green = (entry * 2) % PALETTE_SIZE
                val blue = PALETTE_SIZE - 1 - entry
                (BYTE_MASK shl ALPHA_SHIFT) or
                    (red shl RED_SHIFT) or
                    (green shl GREEN_SHIFT) or
                    blue
            }
        val pixels = ByteArray(format.frameSizeBytes) { index -> (index * 7 % PALETTE_SIZE).toByte() }
        return PresentedFrame(format = format, pixels = pixels, palette = palette)
    }

    private fun renderThroughClassic(frame: PresentedFrame): IntArray {
        val renderer = ClassicBackend().createRenderer(format)
        renderer.present(frame, viewport)
        return renderer.image().pixels.copyOf()
    }

    private fun renderThroughSkia(frame: PresentedFrame): IntArray {
        val renderer = SkikoBackend(CrtSettings.Off).createRenderer(format) as SkikoFrameRenderer
        renderer.present(frame, viewport)
        val paint = assertNotNull(renderer.paintFor(destination), "no paint was produced")

        val info = ImageInfo.makeN32(WIDTH, HEIGHT, ColorAlphaType.UNPREMUL)
        val surface = Surface.makeRaster(info)
        surface.canvas.drawRect(Rect.makeWH(WIDTH.toFloat(), HEIGHT.toFloat()), paint)

        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the rendered surface back")
        val bytes = assertNotNull(bitmap.readPixels(), "the surface produced no pixels")
        return argbFromBgra(bytes)
    }

    /** Skia's N32 layout is BGRA on the platforms Slipgate targets. */
    private fun argbFromBgra(bytes: ByteArray): IntArray =
        IntArray(WIDTH * HEIGHT) { index ->
            val offset = index * BYTES_PER_PIXEL
            val blue = bytes[offset].toInt() and BYTE_MASK
            val green = bytes[offset + 1].toInt() and BYTE_MASK
            val red = bytes[offset + 2].toInt() and BYTE_MASK
            val alpha = bytes[offset + 3].toInt() and BYTE_MASK
            (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }

    private fun channelDistance(
        expected: Int,
        actual: Int,
    ): Int =
        maxOf(
            abs((expected shr ALPHA_SHIFT and BYTE_MASK) - (actual shr ALPHA_SHIFT and BYTE_MASK)),
            abs((expected shr RED_SHIFT and BYTE_MASK) - (actual shr RED_SHIFT and BYTE_MASK)),
            abs((expected shr GREEN_SHIFT and BYTE_MASK) - (actual shr GREEN_SHIFT and BYTE_MASK)),
            abs((expected and BYTE_MASK) - (actual and BYTE_MASK)),
        )
}
