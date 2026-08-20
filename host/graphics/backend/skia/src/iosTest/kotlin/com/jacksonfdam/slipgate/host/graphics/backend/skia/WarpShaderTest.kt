package com.jacksonfdam.slipgate.host.graphics.backend.skia

import kotlin.test.Test
import kotlin.test.assertTrue

private const val WIDTH = 64
private const val HEIGHT = 48
private const val STEEL_DIM = 0xFF39414F.toInt()
private const val STEEL_BASE = 0xFF7E8CA3.toInt()
private const val STEEL_HOT = 0xFFBCC8DC.toInt()

/**
 * The launch transition's shader, compiled and rendered at both ends of its progress.
 *
 * What is asserted is the promise the transition makes to the shell: at the start it covers nothing,
 * so the rack behind it is untouched, and by the end it has taken the frame. A warp that covered the
 * launcher on its first frame would be a cut rather than a transition, and it would still compile.
 */
class WarpShaderTest {
    @Test
    fun atTheStartItCoversNothing() {
        val frame = frame(progress = 0f)

        assertTrue(frame.rowBrightness(HEIGHT / 2) < 1f, "the warp is already covering the launcher")
    }

    @Test
    fun byTheEndItHasTakenTheFrame() {
        val start = frame(progress = 0f).rowBrightness(HEIGHT / 2)
        val end = frame(progress = 1f).rowBrightness(HEIGHT / 2)

        assertTrue(end > start, "the warp never closed: $start to $end")
    }

    /**
     * The closing is monotone: every step of the transition gives up more of the frame than the last.
     * A warp that brightened and dimmed on the way would read as a flicker rather than as a pull.
     */
    @Test
    fun itClosesInOneDirection() {
        val brightness = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { frame(progress = it).rowBrightness(HEIGHT / 2) }

        brightness.zipWithNext().forEach { (earlier, later) ->
            assertTrue(later >= earlier, "the warp went backwards: $brightness")
        }
    }

    private fun frame(progress: Float): RenderedFrame =
        renderScene(
            shaderName = "warp_launch",
            uniforms =
                SceneUniforms(
                    widthPixels = WIDTH.toFloat(),
                    heightPixels = HEIGHT.toFloat(),
                    timeSeconds = 0.5f,
                    accentDim = STEEL_DIM,
                    accentBase = STEEL_BASE,
                    accentHot = STEEL_HOT,
                    // The transition's progress rides in the focus slot; see the shader's own comment.
                    focusAmount = progress,
                    audioLevel = 0f,
                    octaves = 0f,
                ),
        )
}
