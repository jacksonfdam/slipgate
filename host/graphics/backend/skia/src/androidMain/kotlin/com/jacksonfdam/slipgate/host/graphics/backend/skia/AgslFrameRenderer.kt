package com.jacksonfdam.slipgate.host.graphics.backend.skia

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.graphics.core.ViewportRect
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import java.nio.ByteBuffer

private const val INDEXED_INPUT = "indexedFrame"
private const val PALETTE_INPUT = "palette"
private const val SOURCE_INPUT = "source"

/** Presents an indexed frame through an AGSL runtime shader on Android 13 and later. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AgslFrameRenderer(
    private val format: DisplayFormat,
    private val crt: CrtSettings,
) : ComposeFrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.Skia

    private val paletteRuntimeShader = RuntimeShader(paletteShaderSource())
    private val crtRuntimeShader =
        if (crt.enabled) RuntimeShader(crtShaderSource()) else null

    // Compiled on first use and kept: a player who chose smooth edges keeps them all session.
    private var sharpRuntimeShader: RuntimeShader? = null
    private val indexedBitmap =
        Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ALPHA_8)
    private val paletteBitmap =
        Bitmap.createBitmap(PALETTE_ENTRIES, PALETTE_HEIGHT, Bitmap.Config.ARGB_8888)
    private val paletteRow = IntArray(PALETTE_ENTRIES)
    private val paint = Paint()
    private val frameMatrix = Matrix()
    private var uploadedPalette: IntArray? = null
    private var presented = false

    init {
        require(format.pixelFormat == PixelFormat.Indexed8) {
            "the runtime shader presents indexed frames; got ${format.pixelFormat}"
        }
    }

    override fun present(
        frame: PresentedFrame,
        viewport: Viewport,
    ) {
        require(frame.format == format) {
            "renderer was created for $format but was given ${frame.format}"
        }
        indexedBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.pixels))
        paletteRuntimeShader.setInputShader(INDEXED_INPUT, nearestShader(indexedBitmap))
        uploadPaletteIfChanged(frame)
        presented = true
    }

    override fun draw(
        scope: DrawScope,
        viewport: Viewport,
    ) {
        if (!presented) {
            return
        }
        val destination = viewport.destination()
        if (destination.width == 0 || destination.height == 0) {
            return
        }
        val tube = crtRuntimeShader
        val sharp = if (viewport.mode == ScalingMode.SharpUpscale) sharpShader(destination) else null
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.translate(destination.x.toFloat(), destination.y.toFloat())
            if (tube == null && sharp == null) {
                native.scale(
                    destination.width.toFloat() / format.width,
                    destination.height.toFloat() / format.height,
                )
                paletteRuntimeShader.setLocalMatrix(null)
                paint.shader = paletteRuntimeShader
                native.drawRect(0f, 0f, format.width.toFloat(), format.height.toFloat(), paint)
            } else {
                // Both passes work in destination pixels. The upscaler samples in source space
                // itself, so it takes the frame unscaled; the tube takes whatever came before it,
                // already in destination pixels.
                val scaled =
                    if (sharp == null) {
                        scaleFrameToDestination(destination)
                        paletteRuntimeShader
                    } else {
                        paletteRuntimeShader.setLocalMatrix(null)
                        sharp
                    }
                if (tube == null) {
                    paint.shader = scaled
                } else {
                    tube.setInputShader(SOURCE_INPUT, scaled)
                    applyCrtUniforms(tube, destination)
                    paint.shader = tube
                }
                native.drawRect(
                    0f,
                    0f,
                    destination.width.toFloat(),
                    destination.height.toFloat(),
                    paint,
                )
            }
            native.restoreToCount(checkpoint)
        }
    }

    override fun close() {
        presented = false
        indexedBitmap.recycle()
        paletteBitmap.recycle()
    }

    private fun uploadPaletteIfChanged(frame: PresentedFrame) {
        val palette = requireNotNull(frame.palette) { "an indexed frame must carry a palette" }
        if (uploadedPalette?.contentEquals(palette) == true) {
            return
        }
        palette.copyInto(paletteRow, endIndex = PALETTE_ENTRIES)
        paletteBitmap.setPixels(paletteRow, 0, PALETTE_ENTRIES, 0, 0, PALETTE_ENTRIES, PALETTE_HEIGHT)
        paletteRuntimeShader.setInputShader(PALETTE_INPUT, nearestShader(paletteBitmap))
        uploadedPalette = palette.copyOf()
    }

    /** The upscaler, wired to the palette shader and told both sizes it needs. */
    private fun sharpShader(destination: ViewportRect): RuntimeShader {
        val shader =
            sharpRuntimeShader ?: RuntimeShader(sharpUpscaleShaderSource()).also { sharpRuntimeShader = it }
        shader.setInputShader(SOURCE_INPUT, paletteRuntimeShader)
        shader.setFloatUniform("widthPixels", destination.width.toFloat())
        shader.setFloatUniform("heightPixels", destination.height.toFloat())
        shader.setFloatUniform("sourceWidth", format.width.toFloat())
        shader.setFloatUniform("sourceHeight", format.height.toFloat())
        return shader
    }

    private fun scaleFrameToDestination(destination: ViewportRect) {
        frameMatrix.reset()
        frameMatrix.setScale(
            destination.width.toFloat() / format.width,
            destination.height.toFloat() / format.height,
        )
        paletteRuntimeShader.setLocalMatrix(frameMatrix)
    }

    private fun applyCrtUniforms(
        tube: RuntimeShader,
        destination: ViewportRect,
    ) {
        tube.setFloatUniform("widthPixels", destination.width.toFloat())
        tube.setFloatUniform("heightPixels", destination.height.toFloat())
        tube.setFloatUniform("sourceLines", format.height.toFloat())
        tube.setFloatUniform("curvature", crt.curvature)
        tube.setFloatUniform("scanlineStrength", crt.scanlines)
        tube.setFloatUniform("grilleStrength", crt.grille)
        tube.setFloatUniform("bloomStrength", crt.bloom)
        tube.setFloatUniform("vignetteStrength", crt.vignette)
    }

    /** Nearest sampling, clamped: pixels must stay pixels and the palette must not wrap. */
    private fun nearestShader(bitmap: Bitmap): BitmapShader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
        }
}
