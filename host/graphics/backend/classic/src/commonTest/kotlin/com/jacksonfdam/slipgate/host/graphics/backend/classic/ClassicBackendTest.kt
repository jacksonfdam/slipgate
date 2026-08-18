package com.jacksonfdam.slipgate.host.graphics.backend.classic

import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.SurfaceSize
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val OPAQUE_RED = 0xFFFF0000.toInt()
private const val OPAQUE_BLUE = 0xFF0000FF.toInt()

class ClassicBackendTest {
    private val indexed = DisplayFormat(width = 4, height = 2, pixelFormat = PixelFormat.Indexed8)
    private val viewport = Viewport(source = indexed, surface = SurfaceSize(400, 200))

    @Test
    fun theClassicBackendIsAlwaysAvailable() {
        assertTrue(ClassicBackend().isAvailable())
        assertEquals(GraphicsBackendId.Classic, ClassicBackend().id)
    }

    @Test
    fun indexedPixelsResolveThroughThePalette() {
        val renderer = ClassicBackend().createRenderer(indexed)
        val palette = IntArray(256) { OPAQUE_BLUE }
        palette[7] = OPAQUE_RED

        renderer.present(
            PresentedFrame(
                format = indexed,
                pixels = ByteArray(indexed.frameSizeBytes) { if (it == 0) 7 else 0 },
                palette = palette,
            ),
            viewport,
        )

        val image = renderer.image()
        assertEquals(indexed.width, image.width)
        assertEquals(indexed.height, image.height)
        assertEquals(OPAQUE_RED, image.pixels[0])
        assertEquals(OPAQUE_BLUE, image.pixels[1])
    }

    @Test
    fun rgbaPixelsAreRepackedWithoutAPalette() {
        val format = DisplayFormat(width = 2, height = 1, pixelFormat = PixelFormat.Rgba8888)
        val renderer = ClassicBackend().createRenderer(format)
        val pixels =
            byteArrayOf(
                0xFF.toByte(),
                0x00,
                0x00,
                0xFF.toByte(),
                0x00,
                0x00,
                0xFF.toByte(),
                0xFF.toByte(),
            )

        renderer.present(
            PresentedFrame(format = format, pixels = pixels, palette = null),
            Viewport(source = format, surface = SurfaceSize(200, 100)),
        )

        assertEquals(OPAQUE_RED, renderer.image().pixels[0])
        assertEquals(OPAQUE_BLUE, renderer.image().pixels[1])
    }

    @Test
    fun anIndexedFrameWithoutAPaletteIsRejected() {
        val renderer = ClassicBackend().createRenderer(indexed)

        assertFailsWith<IllegalArgumentException> {
            renderer.present(
                PresentedFrame(
                    format = indexed,
                    pixels = ByteArray(indexed.frameSizeBytes),
                    palette = null,
                ),
                viewport,
            )
        }
    }

    @Test
    fun aFrameOfTheWrongFormatIsRejected() {
        val renderer = ClassicBackend().createRenderer(indexed)
        val other = DisplayFormat(width = 8, height = 8, pixelFormat = PixelFormat.Indexed8)

        assertFailsWith<IllegalArgumentException> {
            renderer.present(
                PresentedFrame(
                    format = other,
                    pixels = ByteArray(other.frameSizeBytes),
                    palette = IntArray(256),
                ),
                viewport,
            )
        }
    }

    @Test
    fun readingBeforePresentingIsAnError() {
        val renderer = ClassicBackend().createRenderer(indexed)

        assertFailsWith<IllegalStateException> { renderer.image() }
    }

    @Test
    fun theOutputArrayIsReusedAcrossFrames() {
        val renderer = ClassicBackend().createRenderer(indexed)
        val frame =
            PresentedFrame(
                format = indexed,
                pixels = ByteArray(indexed.frameSizeBytes),
                palette = IntArray(256) { OPAQUE_BLUE },
            )

        renderer.present(frame, viewport)
        val first = renderer.image().pixels
        renderer.present(frame, viewport)

        assertTrue(first === renderer.image().pixels)
    }
}
