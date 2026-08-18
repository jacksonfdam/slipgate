package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTES_PER_PIXEL = 4
private const val BLUE_BYTE = 0
private const val GREEN_BYTE = 1
private const val RED_BYTE = 2
private const val ALPHA_BYTE = 3

/**
 * Skia's N32 layout is BGRA on every platform Slipgate targets, so the ARGB integers are
 * written out byte by byte rather than reinterpreted.
 */
internal actual fun argbToImageBitmap(
    pixels: IntArray,
    width: Int,
    height: Int,
): ImageBitmap {
    val bytes = ByteArray(pixels.size * BYTES_PER_PIXEL)
    for (index in pixels.indices) {
        val colour = pixels[index]
        val offset = index * BYTES_PER_PIXEL
        bytes[offset + BLUE_BYTE] = (colour and BYTE_MASK).toByte()
        bytes[offset + GREEN_BYTE] = (colour shr GREEN_SHIFT and BYTE_MASK).toByte()
        bytes[offset + RED_BYTE] = (colour shr RED_SHIFT and BYTE_MASK).toByte()
        bytes[offset + ALPHA_BYTE] = (colour shr ALPHA_SHIFT and BYTE_MASK).toByte()
    }
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}
