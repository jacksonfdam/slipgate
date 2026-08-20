package com.jacksonfdam.slipgate.host.graphics.backend.skia

import kotlin.test.Test
import kotlin.test.assertTrue

private const val WIDTH = 64
private const val HEIGHT = 48
private const val STEEL_DIM = 0xFF39414F.toInt()
private const val STEEL_BASE = 0xFF7E8CA3.toInt()
private const val STEEL_HOT = 0xFFBCC8DC.toInt()

/**
 * The two Raven portraits, compiled and rendered the way the mars one is.
 *
 * What is asserted is what each portrait claims to be, not that it looks nice: corvus draws an arch,
 * so its middle is lit and its edges are not; korax draws light falling through water, so the top is
 * brighter than the bottom. A shader that stopped doing its own thing would still compile, and these
 * are what would notice.
 */
class RavenPortraitShaderTest {
    @Test
    fun bothPortraitsComposeAFrame() {
        assertTrue(frame("portrait_corvus").colours().size > 1, "the corvus portrait is one colour")
        assertTrue(frame("portrait_korax").colours().size > 1, "the korax portrait is one colour")
    }

    @Test
    fun bothStillComposeWithNoOctaves() {
        assertTrue(frame("portrait_corvus", octaves = 0f, timeSeconds = 0f).colours().size > 1)
        assertTrue(frame("portrait_korax", octaves = 0f, timeSeconds = 0f).colours().size > 1)
    }

    @Test
    fun theCorvusArchIsLitInTheMiddleAndDarkAtTheEdges() {
        val rendered = frame("portrait_corvus")

        val middle = rendered.columnBrightness(WIDTH / 2)
        val edge = rendered.columnBrightness(0)

        assertTrue(middle > edge, "the arch's middle ($middle) is no brighter than its edge ($edge)")
    }

    @Test
    fun theKoraxLightFallsFromAbove() {
        val rendered = frame("portrait_korax")

        // Inside the frame fade at both ends, so what is compared is the picture and not its border.
        val high = rendered.rowBrightness(HEIGHT / 6)
        val low = rendered.rowBrightness(HEIGHT - HEIGHT / 6)

        assertTrue(high > low, "the light at the top ($high) is no brighter than at the bottom ($low)")
    }

    private fun frame(
        shaderName: String,
        octaves: Float = 4f,
        timeSeconds: Float = 3f,
    ): RenderedFrame =
        renderScene(
            shaderName = shaderName,
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
