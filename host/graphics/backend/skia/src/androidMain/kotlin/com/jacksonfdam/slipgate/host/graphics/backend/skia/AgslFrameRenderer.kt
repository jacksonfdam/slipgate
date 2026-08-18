package com.jacksonfdam.slipgate.host.graphics.backend.skia

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import java.nio.ByteBuffer

private const val INDEXED_INPUT = "indexedFrame"
private const val PALETTE_INPUT = "palette"

/** Presents an indexed frame through an AGSL runtime shader on Android 13 and later. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AgslFrameRenderer(
    private val format: DisplayFormat,
) : ComposeFrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.Skia

    private val shader = RuntimeShader(paletteShaderSource())
    private val indexedBitmap =
        Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ALPHA_8)
    private val paletteBitmap =
        Bitmap.createBitmap(PALETTE_ENTRIES, PALETTE_HEIGHT, Bitmap.Config.ARGB_8888)
    private val paletteRow = IntArray(PALETTE_ENTRIES)
    private val paint = Paint().apply { shader = this@AgslFrameRenderer.shader }
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
        shader.setInputShader(INDEXED_INPUT, nearestShader(indexedBitmap))
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
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.translate(destination.x.toFloat(), destination.y.toFloat())
            native.scale(
                destination.width.toFloat() / format.width,
                destination.height.toFloat() / format.height,
            )
            native.drawRect(0f, 0f, format.width.toFloat(), format.height.toFloat(), paint)
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
        shader.setInputShader(PALETTE_INPUT, nearestShader(paletteBitmap))
        uploadedPalette = palette.copyOf()
    }

    /** Nearest sampling, clamped: pixels must stay pixels and the palette must not wrap. */
    private fun nearestShader(bitmap: Bitmap): BitmapShader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
        }
}
