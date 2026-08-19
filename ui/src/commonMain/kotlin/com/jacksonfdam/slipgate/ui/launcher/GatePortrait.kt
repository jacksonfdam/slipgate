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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jacksonfdam.slipgate.host.graphics.backend.skia.PortraitUniforms
import com.jacksonfdam.slipgate.host.graphics.backend.skia.portraitPainter
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.accentRamp
import kotlinx.coroutines.isActive

/**
 * A gate's cover: its own portrait shader, live.
 *
 * There is no cover art in this project, so a card and the stage show the same thing a launch will
 * fly into. A gate whose portrait has not been authored, or a device with no runtime shader at all,
 * gets the composed glow below instead, so a rack of mixed gates still reads as one rack. The frame
 * around it — clip, ground, border — belongs to whatever is placing the portrait.
 *
 * [focus] is how selected this portrait is, from 0 to 1: the shader uses it to tighten its core, so a
 * resting card is calmer than the stage above it.
 */
@Composable
internal fun GatePortrait(
    card: GateCard,
    focus: Float,
    modifier: Modifier = Modifier,
) {
    val painter = remember(card.id) { portraitPainter(card.id) }
    val ramp = accentRamp
    val reducedMotion = LocalReducedMotion.current
    val octaves = LocalPortraitOctaves.current

    Box(modifier = modifier) {
        if (painter == null) {
            ComposedPortrait(dimmed = !card.isPlayable)
        } else {
            var seconds by remember { mutableFloatStateOf(0f) }
            if (!reducedMotion) {
                // Reduced motion holds the portrait at its opening frame rather than freezing a
                // random one: a still portrait should look composed, not paused.
                ShaderClock { elapsed -> seconds = elapsed }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                painter.draw(
                    scope = this,
                    uniforms =
                        PortraitUniforms(
                            widthPixels = size.width,
                            heightPixels = size.height,
                            timeSeconds = seconds,
                            accentDim = ramp.dim.toArgb(),
                            accentBase = ramp.base.toArgb(),
                            accentHot = ramp.hot.toArgb(),
                            focusAmount = if (card.isPlayable) focus else focus * UNAVAILABLE_FOCUS,
                            audioLevel = 0f,
                            octaves = octaves,
                        ),
                )
            }
        }
    }
}

/** Drives shader time from the frame clock, so a portrait animates without a timer of its own. */
@Composable
private fun ShaderClock(onSeconds: (Float) -> Unit) {
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

/** The fallback cover: the recess floor with a low accent glow, dimmed for an unplayable gate. */
@Composable
private fun ComposedPortrait(dimmed: Boolean) {
    val ramp = accentRamp
    val glow = if (dimmed) ramp.dim.copy(alpha = DIMMED_GLOW_ALPHA) else ramp.dim
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(colors = listOf(glow, Color.Transparent))),
    )
}

private const val MILLIS_PER_SECOND = 1000f
private const val UNAVAILABLE_FOCUS = 0.3f
private const val DIMMED_GLOW_ALPHA = 0.35f
