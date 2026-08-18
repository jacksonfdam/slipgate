package com.jacksonfdam.slipgate.host.graphics.backend.classic

import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.runtime.PixelFormat

private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val RED_BYTE = 0
private const val GREEN_BYTE = 1
private const val BLUE_BYTE = 2
private const val ALPHA_BYTE = 3

/**
 * Resolves a frame to 0xAARRGGBB pixels on the CPU, reusing one output array.
 *
 * Doing this per pixel per frame is the cost of the classic path. The shader backends upload
 * the indexed frame and the palette as two textures instead, which is why palette effects are
 * free there and expensive here.
 */
internal class PaletteResolver(
    pixelCount: Int,
) {
    val output: IntArray = IntArray(pixelCount)

    fun resolve(frame: PresentedFrame) {
        when (frame.format.pixelFormat) {
            PixelFormat.Indexed8 -> resolveIndexed(frame)
            PixelFormat.Rgba8888 -> resolveRgba(frame)
        }
    }

    private fun resolveIndexed(frame: PresentedFrame) {
        val palette = requireNotNull(frame.palette) { "an indexed frame must carry a palette" }
        val pixels = frame.pixels
        for (index in output.indices) {
            output[index] = palette[pixels[index].toInt() and BYTE_MASK]
        }
    }

    private fun resolveRgba(frame: PresentedFrame) {
        val pixels = frame.pixels
        val stride = PixelFormat.Rgba8888.bytesPerPixel
        for (index in output.indices) {
            val offset = index * stride
            val red = pixels[offset + RED_BYTE].toInt() and BYTE_MASK
            val green = pixels[offset + GREEN_BYTE].toInt() and BYTE_MASK
            val blue = pixels[offset + BLUE_BYTE].toInt() and BYTE_MASK
            val alpha = pixels[offset + ALPHA_BYTE].toInt() and BYTE_MASK
            output[index] =
                (alpha shl ALPHA_SHIFT) or
                (red shl RED_SHIFT) or
                (green shl GREEN_SHIFT) or
                blue
        }
    }
}
