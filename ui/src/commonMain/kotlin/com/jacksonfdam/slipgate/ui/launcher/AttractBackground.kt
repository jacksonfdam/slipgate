package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.jacksonfdam.slipgate.host.graphics.backend.skia.SceneUniforms
import com.jacksonfdam.slipgate.host.graphics.backend.skia.attractPainter
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.accentRamp
import kotlinx.coroutines.isActive

/**
 * The ground the launcher stands on: a fire in the focused gate's own colours, rising behind the
 * rack.
 *
 * The launcher must not look like a list view, and this is what stops it: the palette comes from the
 * game the player is about to launch, so the whole shell changes temperature as the selection moves.
 * It burns low on purpose — everything else in the interface is drawn on top of it and has to stay
 * readable.
 *
 * A device with no runtime shader gets the composed fallback below. Degraded, not broken: the same
 * colours, the same direction, without the flame.
 */
@Composable
internal fun AttractBackground(modifier: Modifier = Modifier) {
    val painter = remember { attractPainter() }
    val ramp = accentRamp
    val reducedMotion = LocalReducedMotion.current
    val octaves = LocalPortraitOctaves.current

    if (painter == null) {
        ComposedAttract(modifier = modifier)
        return
    }

    var seconds by remember { mutableFloatStateOf(0f) }
    if (!reducedMotion) {
        // Reduced motion holds the fire at its opening frame, the way a portrait is held.
        AttractClock { elapsed -> seconds = elapsed }
    }
    Canvas(modifier = modifier) {
        painter.draw(
            scope = this,
            uniforms =
                SceneUniforms(
                    widthPixels = size.width,
                    heightPixels = size.height,
                    timeSeconds = seconds,
                    accentDim = ramp.dim.toArgb(),
                    accentBase = ramp.base.toArgb(),
                    accentHot = ramp.hot.toArgb(),
                    focusAmount = 1f,
                    audioLevel = 0f,
                    octaves = octaves,
                ),
        )
    }
}

/** Drives shader time from the frame clock, so the fire animates without a timer of its own. */
@Composable
private fun AttractClock(onSeconds: (Float) -> Unit) {
    LaunchedEffect(Unit) {
        var startMillis = 0L
        while (isActive) {
            withFrameMillis { frameMillis ->
                if (startMillis == 0L) {
                    startMillis = frameMillis
                }
                onSeconds((frameMillis - startMillis) / MILLIS_PER_SECOND)
            }
        }
    }
}

/** The fallback ground: the void with the accent's embers banked along the bottom edge. */
@Composable
private fun ComposedAttract(modifier: Modifier = Modifier) {
    val ramp = accentRamp
    Box(
        modifier =
            modifier
                .background(ColorTokens.Void)
                .background(
                    Brush.verticalGradient(
                        // Stopped rather than smooth, so the fallback reads as banked embers rather
                        // than as a gradient somebody forgot to finish.
                        colorStops =
                            arrayOf(
                                0.0f to ColorTokens.Void,
                                EMBER_START to ColorTokens.Void,
                                EMBER_MID to ramp.dim.copy(alpha = EMBER_ALPHA),
                                1.0f to ramp.base.copy(alpha = EMBER_ALPHA),
                            ),
                    ),
                ).fillMaxSize(),
    )
}

private const val MILLIS_PER_SECOND = 1000f
private const val EMBER_START = 0.55f
private const val EMBER_MID = 0.85f
private const val EMBER_ALPHA = 0.35f
