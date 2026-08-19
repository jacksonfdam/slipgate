package com.jacksonfdam.slipgate.host.graphics.backend.skia

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/** AGSL arrived in Android 13; below it there is no runtime shader and the caller draws its fallback. */
public actual fun scenePainter(shaderName: String): ScenePainter? {
    val source =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) sceneShaderSource(shaderName) else null
    return source?.let(::AgslScenePainter)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslScenePainter(
    source: String,
) : ScenePainter {
    private val shader = RuntimeShader(source)
    private val paint = Paint()

    override fun draw(
        scope: DrawScope,
        uniforms: SceneUniforms,
    ) {
        // Uniforms are set by name here rather than as a packed block: AGSL takes them individually,
        // and the names are the shader's own contract either way.
        shader.setFloatUniform("widthPixels", uniforms.widthPixels)
        shader.setFloatUniform("heightPixels", uniforms.heightPixels)
        shader.setFloatUniform("timeSeconds", uniforms.timeSeconds)
        setColour(prefix = "accentDim", argb = uniforms.accentDim)
        setColour(prefix = "accentBase", argb = uniforms.accentBase)
        setColour(prefix = "accentHot", argb = uniforms.accentHot)
        shader.setFloatUniform("focusAmount", uniforms.focusAmount)
        shader.setFloatUniform("audioLevel", uniforms.audioLevel)
        shader.setFloatUniform("octaves", uniforms.octaves)

        paint.shader = shader
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, uniforms.widthPixels, uniforms.heightPixels, paint)
        }
    }

    private fun setColour(
        prefix: String,
        argb: Int,
    ) {
        shader.setFloatUniform("${prefix}R", channel(argb, RED_SHIFT))
        shader.setFloatUniform("${prefix}G", channel(argb, GREEN_SHIFT))
        shader.setFloatUniform("${prefix}B", channel(argb, BLUE_SHIFT))
    }

    private fun channel(
        argb: Int,
        shift: Int,
    ): Float = (argb shr shift and CHANNEL_MASK) / CHANNEL_SCALE

    private companion object {
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BLUE_SHIFT = 0
        const val CHANNEL_MASK = 0xFF
        const val CHANNEL_SCALE = 255f
    }
}
