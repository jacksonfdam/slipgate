package com.jacksonfdam.slipgate.host.graphics.backend.skia

import kotlin.test.Test
import kotlin.test.assertTrue

private const val WIDTH = 64
private const val HEIGHT = 48
private const val STEEL_DIM = 0xFF39414F.toInt()
private const val STEEL_BASE = 0xFF7E8CA3.toInt()
private const val STEEL_HOT = 0xFFBCC8DC.toInt()

/**
 * The attract background's own shader, compiled and rendered the way the portrait's is.
 *
 * What is asserted is what the interface depends on: it composes something, it still composes
 * something with the animation frozen, and the fire has a floor — the bottom of the frame is
 * brighter than the top, because type is read against the top.
 */
class AttractShaderTest {
    @Test
    fun theFireCompilesAndComposesAFrame() {
        assertTrue(frame(octaves = 4f, timeSeconds = 3f).colours().size > 1)
    }

    @Test
    fun zeroOctavesStillComposesAStill() {
        assertTrue(frame(octaves = 0f, timeSeconds = 0f).colours().size > 1)
    }

    @Test
    fun theFireBurnsFromTheBottom() {
        val rendered = frame(octaves = 4f, timeSeconds = 3f)

        val floor = rendered.rowBrightness(rendered.lastRow)
        val ceiling = rendered.rowBrightness(0)

        assertTrue(floor > ceiling, "the floor ($floor) is no brighter than the ceiling ($ceiling)")
    }

    private fun frame(
        octaves: Float,
        timeSeconds: Float,
    ): RenderedFrame =
        renderScene(
            shaderName = "attract_fire",
            uniforms =
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
                ),
        )
}
