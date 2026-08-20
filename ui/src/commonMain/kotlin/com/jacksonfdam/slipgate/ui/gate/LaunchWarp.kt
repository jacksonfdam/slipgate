package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.jacksonfdam.slipgate.host.graphics.backend.skia.SceneUniforms
import com.jacksonfdam.slipgate.host.graphics.backend.skia.scenePainter
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.Motion
import com.jacksonfdam.slipgate.ui.design.accentRamp
import kotlinx.coroutines.isActive

private const val MILLIS_PER_SECOND = 1000f
private const val FALLBACK_ALPHA = 0.9f

/**
 * The launch transition: the launcher being pulled into a game.
 *
 * Drawn over whatever the shell was showing, so the rack is still there while it happens — the warp is
 * the thing that ends the rack, not a screen that replaces it. It runs for [Motion.LAUNCH_MS], the one
 * transition the motion tokens allow real time, and for the short cross-fade instead when the player
 * asked for reduced motion.
 *
 * A device with no runtime shader gets a wash in the same colours, closing at the same rate. Degraded,
 * not broken.
 */
@Composable
internal fun LaunchWarp(modifier: Modifier = Modifier) {
    val painter = remember { scenePainter("warp_launch") }
    val ramp = accentRamp
    val reducedMotion = LocalReducedMotion.current
    val duration = Motion.duration(Motion.LAUNCH_MS, reducedMotion)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by
        animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(durationMillis = duration, easing = Motion.Standard),
            label = "launch warp",
        )

    if (painter == null) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ramp.hot, ramp.dim),
                        ),
                    ).graphicsLayer { alpha = progress * FALLBACK_ALPHA },
        )
        return
    }

    var seconds by remember { mutableFloatStateOf(0f) }
    // The tear walks on its own clock: the progress decides how much of the frame it takes, and this
    // decides where it is. Frozen under reduced motion, like every other ambient movement.
    if (!reducedMotion) {
        LaunchedEffect(Unit) {
            var startMillis = 0L
            while (isActive) {
                withFrameMillis { frameMillis ->
                    if (startMillis == 0L) {
                        startMillis = frameMillis
                    }
                    seconds = (frameMillis - startMillis) / MILLIS_PER_SECOND
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
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
                    // The transition's own progress: what that slot means for a warp.
                    focusAmount = progress,
                    audioLevel = 0f,
                    octaves = 0f,
                ),
        )
    }
}
