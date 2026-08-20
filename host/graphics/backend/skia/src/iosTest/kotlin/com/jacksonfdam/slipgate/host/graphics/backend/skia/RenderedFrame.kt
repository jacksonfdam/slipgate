package com.jacksonfdam.slipgate.host.graphics.backend.skia

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Surface
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compiles a scene shader out of the embedded sources and renders one frame of it.
 *
 * Shared by every scene shader's test, because what they all need is the same: a surface without a
 * device, the shader compiled from the source that ships, and the pixels back. A typo in the SkSL
 * would otherwise only surface at runtime on someone's phone.
 */
internal fun renderScene(
    shaderName: String,
    uniforms: SceneUniforms,
): RenderedFrame {
    val source = assertNotNull(sceneShaderSource(shaderName), "$shaderName is not embedded")
    val effect = RuntimeEffect.makeForShader(source)
    val width = uniforms.widthPixels.toInt()
    val height = uniforms.heightPixels.toInt()
    val shader =
        effect.makeShader(
            uniforms = Data.makeFromBytes(floatBytes(uniforms.pack())),
            children = null,
            localMatrix = null,
        )
    val info = ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL)
    val surface = Surface.makeRaster(info)
    val paint = Paint().apply { this.shader = shader }
    surface.canvas.drawRect(Rect.makeWH(uniforms.widthPixels, uniforms.heightPixels), paint)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the rendered surface back")
    val bytes = assertNotNull(bitmap.readPixels(), "the surface produced no pixels")
    return RenderedFrame(width = width, height = height, bytes = bytes)
}

/** One rendered frame, as the bytes came back: four channels a pixel, blue first. */
internal class RenderedFrame(
    private val width: Int,
    private val height: Int,
    private val bytes: ByteArray,
) {
    /** Every distinct colour in the frame, which is how a test says "this composed something". */
    fun colours(): Set<Int> {
        val colours = mutableSetOf<Int>()
        for (index in bytes.indices step CHANNELS) {
            colours += colourAt(index)
        }
        return colours
    }

    /** Mean brightness of one row, for a test that cares which way round a gradient runs. */
    fun rowBrightness(row: Int): Float {
        var sum = 0f
        for (column in 0 until width) {
            val index = (row * width + column) * CHANNELS
            val colour = colourAt(index)
            sum += ((colour shr RED) and CHANNEL) + ((colour shr GREEN) and CHANNEL) + (colour and CHANNEL)
        }
        return sum / (width * CHANNELS_PER_COLOUR)
    }

    /** Mean brightness of one column, for a test that cares which way round a shape runs. */
    fun columnBrightness(column: Int): Float {
        var sum = 0f
        for (row in 0 until height) {
            val colour = colourAt((row * width + column) * CHANNELS)
            sum += ((colour shr RED) and CHANNEL) + ((colour shr GREEN) and CHANNEL) + (colour and CHANNEL)
        }
        return sum / (height * CHANNELS_PER_COLOUR)
    }

    val lastRow: Int get() = height - 1

    val lastColumn: Int get() = width - 1

    private fun colourAt(index: Int): Int =
        (bytes[index].toInt() and CHANNEL) or
            ((bytes[index + 1].toInt() and CHANNEL) shl GREEN) or
            ((bytes[index + 2].toInt() and CHANNEL) shl RED)

    private companion object {
        const val CHANNELS = 4
        const val CHANNELS_PER_COLOUR = 3
        const val CHANNEL = 0xFF
        const val GREEN = 8
        const val RED = 16
    }
}

private fun floatBytes(values: FloatArray): ByteArray {
    val bytes = ByteArray(values.size * Float.SIZE_BYTES)
    values.forEachIndexed { index, value ->
        val bits = value.toRawBits()
        for (byte in 0 until Float.SIZE_BYTES) {
            bytes[index * Float.SIZE_BYTES + byte] = (bits shr (byte * Byte.SIZE_BITS) and 0xFF).toByte()
        }
    }
    return bytes
}
