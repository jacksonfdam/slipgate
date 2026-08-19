package com.jacksonfdam.slipgate.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.jacksonfdam.slipgate.host.graphics.core.FrameTimeSampler
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.Wordmark
import com.jacksonfdam.slipgate.ui.design.accentRamp
import com.jacksonfdam.slipgate.ui.design.glyphPath
import com.jacksonfdam.slipgate.ui.design.reducedMotion
import kotlinx.coroutines.launch

/**
 * The cold-start splash, and the device benchmark window behind it: a scanline sweeps and
 * leaves the wordmark assembling glyph by glyph from displaced fragments, the letterforms
 * settle, and a soft bloom in the pre-data steel accent closes the move. While it plays,
 * frame times are sampled; [onFinished] receives their median in microseconds, or null
 * when there was nothing usable to measure.
 *
 * A tap skips ahead. After the animation ends the settled wordmark stays composed on
 * screen — never a spinner — so the shell can take over whenever it is ready. Reduced
 * motion renders the settled frame immediately.
 */
@Composable
public fun SplashScreen(
    onFinished: (medianFrameMicros: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = reducedMotion
    val progress = remember { Animatable(0f) }
    val sampler = remember { FrameTimeSampler() }
    val scope = rememberCoroutineScope()
    val accent = accentRamp

    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
            onFinished(null)
            return@LaunchedEffect
        }
        launch {
            var last = 0L
            while (progress.value < 1f) {
                withFrameNanos { now ->
                    if (last != 0L) sampler.add((now - last) / NANOS_PER_MICRO)
                    last = now
                }
            }
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SPLASH_MS, easing = LinearEasing),
        )
        onFinished(sampler.median())
    }

    val glyphPaths = remember { Wordmark.glyphs.map(::glyphPath) }
    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorTokens.Void)
                .pointerInput(Unit) {
                    detectTapGestures {
                        scope.launch { progress.snapTo(1f) }
                    }
                },
    ) {
        drawSplashFrame(progress.value, glyphPaths, accent.base)
    }
}

private fun DrawScope.drawSplashFrame(
    p: Float,
    glyphPaths: List<Path>,
    accent: Color,
) {
    val scale = size.width * WORDMARK_WIDTH_FRACTION / Wordmark.width
    val markWidth = Wordmark.width * scale
    val markHeight = Wordmark.CAP_HEIGHT * scale
    val originX = (size.width - markWidth) / 2f
    val originY = (size.height - markHeight) / 2f

    // Each glyph assembles inside its own window, staggered left to right.
    glyphPaths.forEachIndexed { index, path ->
        val t = window(p, ASSEMBLE_START + index * GLYPH_STAGGER, GLYPH_WINDOW)
        if (t <= 0f) return@forEachIndexed
        val remaining = 1f - t
        val side = if (index % 2 == 0) -1f else 1f
        val displacement = side * remaining * remaining * size.height * DISPLACEMENT_FRACTION
        withTransform({
            translate(originX + index * Wordmark.ADVANCE * scale, originY + displacement)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(path, ColorTokens.Text, alpha = t)
        }
    }

    // The sweep that appears to leave the letters behind.
    val sweep = window(p, 0f, SWEEP_PHASE)
    if (sweep < 1f) {
        val y = sweep * size.height
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, accent.copy(alpha = SWEEP_TRAIL_ALPHA)),
                    startY = y - SWEEP_TRAIL_PX,
                    endY = y,
                ),
            topLeft = Offset(0f, y - SWEEP_TRAIL_PX),
            size = Size(size.width, SWEEP_TRAIL_PX),
        )
        drawRect(
            color = ColorTokens.Text.copy(alpha = SWEEP_LINE_ALPHA),
            topLeft = Offset(0f, y),
            size = Size(size.width, SWEEP_LINE_PX),
        )
    }

    // A soft palette bloom in the fallback steel accent, rising and falling once.
    val bloom = window(p, BLOOM_START, BLOOM_WINDOW)
    if (bloom > 0f && bloom < 1f) {
        val strength = BLOOM_PARABOLA * bloom * (1f - bloom) * BLOOM_PEAK_ALPHA
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = strength), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width / 2f,
                ),
        )
    }
}

private fun window(
    p: Float,
    start: Float,
    length: Float,
): Float = ((p - start) / length).coerceIn(0f, 1f)

private const val SPLASH_MS = 1600
private const val NANOS_PER_MICRO = 1_000L
private const val WORDMARK_WIDTH_FRACTION = 0.6f
private const val ASSEMBLE_START = 0.08f
private const val GLYPH_STAGGER = 0.05f
private const val GLYPH_WINDOW = 0.24f
private const val DISPLACEMENT_FRACTION = 0.22f
private const val SWEEP_PHASE = 0.62f
private const val SWEEP_LINE_PX = 3f
private const val SWEEP_TRAIL_PX = 90f
private const val SWEEP_LINE_ALPHA = 0.55f
private const val SWEEP_TRAIL_ALPHA = 0.10f
private const val BLOOM_START = 0.74f
private const val BLOOM_WINDOW = 0.2f
private const val BLOOM_PEAK_ALPHA = 0.30f
private const val BLOOM_PARABOLA = 4f
