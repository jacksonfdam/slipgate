package com.jacksonfdam.slipgate.host.graphics.backend.skia

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH = 64
private const val HEIGHT = 32
private const val STEEL_DIM = 0xFF39414F.toInt()
private const val STEEL_BASE = 0xFF7E8CA3.toInt()
private const val STEEL_HOT = 0xFFBCC8DC.toInt()

/**
 * Compiles the mars portrait out of the embedded sources and renders one frame. Lives in
 * the iOS source set for the same reason as GoldenImageTest: a Skia surface exists here
 * without a device. The point is that the shader source itself is valid — a typo in the
 * SkSL would otherwise only surface at runtime on someone's phone.
 */
class PortraitShaderTest {
    @Test
    fun theUniformBlockPacksInDeclarationOrder() {
        val packed =
            SceneUniforms(
                widthPixels = 64f,
                heightPixels = 32f,
                timeSeconds = 2f,
                accentDim = STEEL_DIM,
                accentBase = STEEL_BASE,
                accentHot = STEEL_HOT,
                focusAmount = 1f,
                audioLevel = 0.5f,
                octaves = 4f,
            ).pack()

        assertEquals(SceneUniforms.FLOAT_COUNT, packed.size)
        assertEquals(64f, packed[0])
        assertEquals(32f, packed[1])
        assertEquals(2f, packed[2])
        // The base colour's red channel: 0x7E / 255.
        assertEquals(0x7E / 255f, packed[6])
        assertEquals(1f, packed[12])
        assertEquals(0.5f, packed[13])
        assertEquals(4f, packed[14])
    }

    @Test
    fun unknownGatesHaveNoPortrait() {
        assertNull(sceneShaderSource("portrait_no-such-gate"))
    }

    @Test
    fun theMarsPortraitCompilesAndRendersAComposedFrame() {
        assertTrue(renderedColours(octaves = 4f, timeSeconds = 2f).size > 1)
    }

    @Test
    fun zeroOctavesStillComposesAStill() {
        // Minimal tier and reduced motion freeze the portrait; it must stay composed,
        // not collapse to a single colour.
        assertTrue(renderedColours(octaves = 0f, timeSeconds = 0f).size > 1)
    }

    private fun renderedColours(
        octaves: Float,
        timeSeconds: Float,
    ): Set<Int> {
        val source = assertNotNull(sceneShaderSource("portrait_mars"))
        val effect = RuntimeEffect.makeForShader(source)
        val uniforms =
            SceneUniforms(
                widthPixels = WIDTH.toFloat(),
                heightPixels = HEIGHT.toFloat(),
                timeSeconds = timeSeconds,
                accentDim = STEEL_DIM,
                accentBase = STEEL_BASE,
                accentHot = STEEL_HOT,
                focusAmount = 1f,
                audioLevel = 0f,
                octaves = octaves,
            ).pack()
        val shader =
            effect.makeShader(
                uniforms = Data.makeFromBytes(floatBytes(uniforms)),
                children = null,
                localMatrix = null,
            )
        val info = ImageInfo.makeN32(WIDTH, HEIGHT, ColorAlphaType.UNPREMUL)
        val surface = Surface.makeRaster(info)
        val paint = Paint().apply { this.shader = shader }
        surface.canvas.drawRect(Rect.makeWH(WIDTH.toFloat(), HEIGHT.toFloat()), paint)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the rendered surface back")
        val bytes = assertNotNull(bitmap.readPixels(), "the surface produced no pixels")
        val colours = mutableSetOf<Int>()
        for (index in bytes.indices step 4) {
            colours +=
                (bytes[index].toInt() and 0xFF) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                ((bytes[index + 2].toInt() and 0xFF) shl 16)
        }
        return colours
    }

    private fun floatBytes(values: FloatArray): ByteArray {
        val bytes = ByteArray(values.size * Float.SIZE_BYTES)
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until Float.SIZE_BYTES) {
                bytes[index * Float.SIZE_BYTES + byte] = (bits shr (byte * 8) and 0xFF).toByte()
            }
        }
        return bytes
    }
}
