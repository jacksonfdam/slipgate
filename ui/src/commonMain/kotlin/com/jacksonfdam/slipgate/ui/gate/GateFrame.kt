package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.ui.graphics.ImageBitmap
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.PixelFormat

/** Builds a platform image from 0xAARRGGBB pixels. */
internal expect fun argbToImageBitmap(
    pixels: IntArray,
    width: Int,
    height: Int,
): ImageBitmap

/**
 * Converts a session's framebuffer into something Compose can draw, reusing one pixel array
 * across frames.
 *
 * This is deliberately the simplest thing that works: it resolves the palette on the CPU and
 * allocates an image per frame. The graphics backends replace it — the palette lookup belongs
 * in a fragment shader, which is what makes palette effects free.
 */
internal class GateFrame(
    private val display: DisplayFormat,
) {
    private val argb = IntArray(display.width * display.height)

    fun render(session: GateSession): ImageBitmap {
        val source = session.framebuffer()
        when (display.pixelFormat) {
            PixelFormat.Indexed8 -> resolveIndexed(source, session.palette())
            PixelFormat.Rgba8888 -> resolveRgba(source)
        }
        return argbToImageBitmap(argb, display.width, display.height)
    }

    private fun resolveIndexed(
        source: ByteArray,
        palette: IntArray?,
    ) {
        requireNotNull(palette) { "an indexed session must supply a palette" }
        for (index in argb.indices) {
            argb[index] = palette[source[index].toInt() and BYTE_MASK]
        }
    }

    private fun resolveRgba(source: ByteArray) {
        for (index in argb.indices) {
            val offset = index * PixelFormat.Rgba8888.bytesPerPixel
            val red = source[offset + RED_BYTE].toInt() and BYTE_MASK
            val green = source[offset + GREEN_BYTE].toInt() and BYTE_MASK
            val blue = source[offset + BLUE_BYTE].toInt() and BYTE_MASK
            val alpha = source[offset + ALPHA_BYTE].toInt() and BYTE_MASK
            argb[index] =
                (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val RED_BYTE = 0
        const val GREEN_BYTE = 1
        const val BLUE_BYTE = 2
        const val ALPHA_BYTE = 3
    }
}
