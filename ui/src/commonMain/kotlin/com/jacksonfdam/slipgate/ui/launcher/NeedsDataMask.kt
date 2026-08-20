package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.jacksonfdam.slipgate.ui.design.ColorTokens

private const val WASH_ALPHA = 0.40f
private const val SPECKLE_ALPHA = 0.20f
private const val CELL_PIXELS = 4f

// Sparse on purpose: a mask that lit a third of its cells was television static, and the portrait
// under it may as well not have been drawn.
private const val SPECKLE_THRESHOLD = 0.93f
private const val HASH_X = 127.1f
private const val HASH_Y = 311.7f
private const val HASH_SCALE = 43758.547f

/**
 * What a gate whose data is not installed is drawn behind: a wash that takes the colour out of the
 * portrait, and a still speckle over it.
 *
 * Still, deliberately. An animated mask would read as an effect the card was doing on purpose; this
 * has to read as a card that is not ready — the specification's word is "degraded, not broken".
 *
 * Drawn with composables rather than a shader so it looks the same on a device with no runtime shader
 * at all, which is the very device most likely to be showing an uninstalled gate.
 */
@Composable
internal fun NeedsDataMask(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = ColorTokens.Void.copy(alpha = WASH_ALPHA))

        val speckle = Color.White.copy(alpha = SPECKLE_ALPHA)
        val columns = (size.width / CELL_PIXELS).toInt()
        val rows = (size.height / CELL_PIXELS).toInt()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (noiseAt(column, row) < SPECKLE_THRESHOLD) {
                    continue
                }
                drawRect(
                    color = speckle,
                    topLeft = Offset(column * CELL_PIXELS, row * CELL_PIXELS),
                    size = Size(CELL_PIXELS, CELL_PIXELS),
                )
            }
        }
    }
}

/**
 * The same value-noise hash the portrait shaders use, so the mask is grain of the same family — and
 * the same every frame, because it takes no time as an input.
 */
private fun noiseAt(
    column: Int,
    row: Int,
): Float {
    val dot = column * HASH_X + row * HASH_Y
    val scaled = kotlin.math.sin(dot) * HASH_SCALE
    return scaled - kotlin.math.floor(scaled)
}
