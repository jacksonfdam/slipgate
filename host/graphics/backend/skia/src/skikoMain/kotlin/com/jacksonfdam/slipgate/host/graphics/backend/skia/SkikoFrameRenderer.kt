package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader

private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTES_PER_PALETTE_ENTRY = 4
private const val PALETTE_BLUE_BYTE = 0
private const val PALETTE_GREEN_BYTE = 1
private const val PALETTE_RED_BYTE = 2
private const val PALETTE_ALPHA_BYTE = 3

/** Presents an indexed frame through a Skia runtime effect on iOS and web. */
internal class SkikoFrameRenderer(
    private val format: DisplayFormat,
) : ComposeFrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.Skia

    private val effect = RuntimeEffect.makeForShader(paletteShaderSource())
    private val indexedBitmap =
        Bitmap().apply {
            allocPixels(
                ImageInfo(
                    width = format.width,
                    height = format.height,
                    colorType = ColorType.ALPHA_8,
                    alphaType = ColorAlphaType.UNPREMUL,
                ),
            )
        }
    private val paletteBitmap =
        Bitmap().apply {
            allocPixels(
                ImageInfo.makeN32(PALETTE_ENTRIES, PALETTE_HEIGHT, ColorAlphaType.UNPREMUL),
            )
        }
    private val paletteBytes = ByteArray(PALETTE_ENTRIES * BYTES_PER_PALETTE_ENTRY)
    private val paint = Paint()
    private var uploadedPalette: IntArray? = null
    private var presented = false

    init {
        require(format.pixelFormat == PixelFormat.Indexed8) {
            "the runtime effect presents indexed frames; got ${format.pixelFormat}"
        }
    }

    override fun present(
        frame: PresentedFrame,
        viewport: Viewport,
    ) {
        require(frame.format == format) {
            "renderer was created for $format but was given ${frame.format}"
        }
        indexedBitmap.installPixels(frame.pixels)
        uploadPaletteIfChanged(frame)
        paint.shader =
            effect.makeShader(
                uniforms = null,
                children = arrayOf(nearestShader(indexedBitmap), nearestShader(paletteBitmap)),
                localMatrix = null,
            )
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
            val native = canvas.skiaCanvas
            native.save()
            native.translate(destination.x.toFloat(), destination.y.toFloat())
            native.scale(
                destination.width.toFloat() / format.width,
                destination.height.toFloat() / format.height,
            )
            native.drawRect(
                org.jetbrains.skia.Rect
                    .makeWH(format.width.toFloat(), format.height.toFloat()),
                paint,
            )
            native.restore()
        }
    }

    override fun close() {
        presented = false
        indexedBitmap.close()
        paletteBitmap.close()
    }

    private fun uploadPaletteIfChanged(frame: PresentedFrame) {
        val palette = requireNotNull(frame.palette) { "an indexed frame must carry a palette" }
        if (uploadedPalette?.contentEquals(palette) == true) {
            return
        }
        for (entry in 0 until PALETTE_ENTRIES) {
            val colour = palette[entry]
            val offset = entry * BYTES_PER_PALETTE_ENTRY
            paletteBytes[offset + PALETTE_BLUE_BYTE] = (colour and BYTE_MASK).toByte()
            paletteBytes[offset + PALETTE_GREEN_BYTE] =
                (colour shr GREEN_SHIFT and BYTE_MASK).toByte()
            paletteBytes[offset + PALETTE_RED_BYTE] = (colour shr RED_SHIFT and BYTE_MASK).toByte()
            paletteBytes[offset + PALETTE_ALPHA_BYTE] =
                (colour shr ALPHA_SHIFT and BYTE_MASK).toByte()
        }
        paletteBitmap.installPixels(paletteBytes)
        uploadedPalette = palette.copyOf()
    }

    /** Nearest sampling, clamped: pixels must stay pixels and the palette must not wrap. */
    private fun nearestShader(bitmap: Bitmap): Shader =
        Image.makeFromBitmap(bitmap).makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE),
            null,
        )
}
