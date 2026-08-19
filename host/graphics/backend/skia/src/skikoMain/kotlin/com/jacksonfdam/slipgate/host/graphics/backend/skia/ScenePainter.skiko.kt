package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect

/** iOS and web both draw through Skia, so one painter serves them. */
public actual fun scenePainter(shaderName: String): ScenePainter? {
    val source = sceneShaderSource(shaderName) ?: return null
    return SkikoScenePainter(source)
}

private class SkikoScenePainter(
    source: String,
) : ScenePainter {
    private val effect = RuntimeEffect.makeForShader(source)
    private val paint = Paint()
    private val floats = FloatArray(SceneUniforms.FLOAT_COUNT)

    override fun draw(
        scope: DrawScope,
        uniforms: SceneUniforms,
    ) {
        // One packed block, in the order every portrait declares its uniforms in. Skia takes the
        // whole buffer at once, so the order is the contract rather than the names.
        uniforms.pack(floats)
        paint.shader =
            effect.makeShader(
                uniforms = Data.makeFromBytes(littleEndianBytes(floats)),
                children = null,
                localMatrix = null,
            )
        scope.drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawRect(
                Rect.makeWH(uniforms.widthPixels, uniforms.heightPixels),
                paint,
            )
        }
    }
}

/** Little-endian float32, which is what Skia expects for runtime effect uniforms. */
private fun littleEndianBytes(values: FloatArray): ByteArray {
    val bytes = ByteArray(values.size * Float.SIZE_BYTES)
    values.forEachIndexed { index, value ->
        val bits = value.toRawBits()
        val offset = index * Float.SIZE_BYTES
        for (byteIndex in 0 until Float.SIZE_BYTES) {
            bytes[offset + byteIndex] = (bits shr (byteIndex * Byte.SIZE_BITS) and BYTE_MASK).toByte()
        }
    }
    return bytes
}

private const val BYTE_MASK = 0xFF
