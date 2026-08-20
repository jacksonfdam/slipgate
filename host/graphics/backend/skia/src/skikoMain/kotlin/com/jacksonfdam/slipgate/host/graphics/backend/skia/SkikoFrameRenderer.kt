package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.graphics.core.ViewportRect
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
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
    private val crt: CrtSettings,
) : ComposeFrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.Skia

    private val paletteEffect = RuntimeEffect.makeForShader(paletteShaderSource())
    private val crtEffect =
        if (crt.enabled) RuntimeEffect.makeForShader(crtShaderSource()) else null

    // Built on demand and kept: compiling a runtime effect is not free, and a player who chose
    // smooth edges keeps them for the whole session.
    private var sharpEffect: RuntimeEffect? = null
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
    private var paletteShader: Shader? = null
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
        paletteShader =
            paletteEffect.makeShader(
                uniforms = null,
                children = arrayOf(nearestShader(indexedBitmap), nearestShader(paletteBitmap)),
                localMatrix = null,
            )
        presented = true
    }

    /**
     * The paint that draws the last presented frame into [destination].
     *
     * Separated from [draw] so a test can render it into an offscreen Skia surface without a
     * Compose canvas, which is what makes the golden image comparison possible at all.
     */
    internal fun paintFor(
        destination: ViewportRect,
        mode: ScalingMode = ScalingMode.Fit,
    ): Paint? {
        val frameShader = paletteShader?.takeIf { presented } ?: return null
        val scaled =
            if (mode == ScalingMode.SharpUpscale) {
                sharpUpscaled(frameShader, destination)
            } else {
                scaledToDestination(frameShader, destination)
            }
        val tube = crtEffect
        paint.shader =
            if (tube == null) {
                scaled
            } else {
                tube.makeShader(
                    uniforms = crtUniforms(destination.width, destination.height),
                    children = arrayOf(scaled),
                    localMatrix = null,
                )
            }
        return paint
    }

    /**
     * The frame upscaled by the edge-adaptive shader, in destination pixels.
     *
     * The child goes in unscaled: this shader samples in source space itself, because choosing
     * where to sample is the whole of what it does.
     */
    private fun sharpUpscaled(
        frameShader: Shader,
        destination: ViewportRect,
    ): Shader {
        val effect = sharpEffect ?: RuntimeEffect.makeForShader(sharpUpscaleShaderSource()).also { sharpEffect = it }
        return effect.makeShader(
            uniforms =
                Data.makeFromBytes(
                    floatBytes(
                        destination.width.toFloat(),
                        destination.height.toFloat(),
                        format.width.toFloat(),
                        format.height.toFloat(),
                    ),
                ),
            children = arrayOf(frameShader),
            localMatrix = null,
        )
    }

    override fun draw(
        scope: DrawScope,
        viewport: Viewport,
    ) {
        val destination = viewport.destination()
        if (destination.width == 0 || destination.height == 0) {
            return
        }
        // Both paths work in destination pixels: the frame is scaled by a local matrix on the
        // shader rather than by the canvas, so the tube pass sees real display pixels.
        val framePaint = paintFor(destination, viewport.mode) ?: return
        scope.drawIntoCanvas { canvas ->
            val native = canvas.skiaCanvas
            native.save()
            native.translate(destination.x.toFloat(), destination.y.toFloat())
            native.drawRect(
                Rect.makeWH(destination.width.toFloat(), destination.height.toFloat()),
                framePaint,
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

    private fun scaledToDestination(
        frameShader: Shader,
        destination: ViewportRect,
    ): Shader =
        // A local matrix maps shader space into the parent's space, so the child is sampled at
        // the inverse: scaling by destination over source is what turns a destination pixel into
        // the source pixel it shows.
        frameShader.makeWithLocalMatrix(
            Matrix33.makeScale(
                destination.width.toFloat() / format.width,
                destination.height.toFloat() / format.height,
            ),
        )

    /** Uniform order must match the shader's declarations exactly; all of them are scalars. */
    private fun crtUniforms(
        width: Int,
        height: Int,
    ): Data =
        Data.makeFromBytes(
            floatBytes(
                width.toFloat(),
                height.toFloat(),
                format.height.toFloat(),
                crt.curvature,
                crt.scanlines,
                crt.grille,
                crt.bloom,
                crt.vignette,
            ),
        )

    /** Nearest sampling, clamped: pixels must stay pixels and the palette must not wrap. */
    private fun nearestShader(bitmap: Bitmap): Shader =
        Image.makeFromBitmap(bitmap).makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE),
            null,
        )
}

/** Little-endian float32 packing, which is what Skia expects for runtime effect uniforms. */
private fun floatBytes(vararg values: Float): ByteArray {
    val bytes = ByteArray(values.size * Float.SIZE_BYTES)
    values.forEachIndexed { index, value ->
        val bits = value.toRawBits()
        val offset = index * Float.SIZE_BYTES
        for (byteIndex in 0 until Float.SIZE_BYTES) {
            bytes[offset + byteIndex] =
                (bits shr (byteIndex * Byte.SIZE_BITS) and BYTE_MASK).toByte()
        }
    }
    return bytes
}
