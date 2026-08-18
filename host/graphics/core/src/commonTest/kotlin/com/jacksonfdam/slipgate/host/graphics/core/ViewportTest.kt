package com.jacksonfdam.slipgate.host.graphics.core

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewportTest {
    private val doom =
        DisplayFormat(width = 320, height = 200, pixelFormat = PixelFormat.Indexed8)

    @Test
    fun fitCentresWithinAWiderSurface() {
        val rect =
            Viewport(source = doom, surface = SurfaceSize(1280, 400), mode = ScalingMode.Fit)
                .destination()

        assertEquals(640, rect.width)
        assertEquals(400, rect.height)
        assertEquals(320, rect.x)
        assertEquals(0, rect.y)
    }

    @Test
    fun fitCentresWithinATallerSurface() {
        val rect =
            Viewport(source = doom, surface = SurfaceSize(640, 800), mode = ScalingMode.Fit)
                .destination()

        assertEquals(640, rect.width)
        assertEquals(400, rect.height)
        assertEquals(0, rect.x)
        assertEquals(200, rect.y)
    }

    @Test
    fun pixelAspectCorrectsTheDisplayedShape() {
        val square =
            Viewport(source = doom, surface = SurfaceSize(1920, 1080), pixelAspect = 1f)
                .destination()
        val corrected =
            Viewport(
                source = doom,
                surface = SurfaceSize(1920, 1080),
                pixelAspect = ID_TECH_1_PIXEL_ASPECT,
            ).destination()

        assertEquals(1080, square.height)
        assertEquals(1080, corrected.height)
        assertTrue(corrected.width < square.width)
        assertEquals(
            4f / 3f,
            corrected.width.toFloat() / corrected.height,
            absoluteTolerance = 0.01f,
        )
    }

    @Test
    fun integerScaleUsesWholeMultiples() {
        val rect =
            Viewport(
                source = doom,
                surface = SurfaceSize(1000, 700),
                mode = ScalingMode.IntegerScale,
            ).destination()

        assertEquals(960, rect.width)
        assertEquals(600, rect.height)
    }

    @Test
    fun integerScaleNeverGoesBelowOne() {
        val rect =
            Viewport(
                source = doom,
                surface = SurfaceSize(160, 100),
                mode = ScalingMode.IntegerScale,
            ).destination()

        assertEquals(320, rect.width)
        assertEquals(200, rect.height)
    }

    @Test
    fun stretchFillsTheSurface() {
        val rect =
            Viewport(source = doom, surface = SurfaceSize(500, 900), mode = ScalingMode.Stretch)
                .destination()

        assertEquals(ViewportRect(x = 0, y = 0, width = 500, height = 900), rect)
    }

    @Test
    fun anEmptySurfaceProducesAnEmptyRect() {
        val rect = Viewport(source = doom, surface = SurfaceSize(0, 0)).destination()

        assertEquals(ViewportRect(x = 0, y = 0, width = 0, height = 0), rect)
    }
}
