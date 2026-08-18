package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a running session, stepping it once per display frame. Input is idle for now; the
 * control layer feeds real frames once it exists.
 */
@Composable
public fun GateSurface(
    session: GateSession,
    modifier: Modifier = Modifier,
) {
    val frame = remember(session) { GateFrame(session.display) }
    var image by remember(session) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(session) {
        var previousFrameMillis = 0L
        var running = true
        while (running) {
            withFrameMillis { frameTimeMillis ->
                val elapsed =
                    if (previousFrameMillis == 0L) 0L else frameTimeMillis - previousFrameMillis
                previousFrameMillis = frameTimeMillis
                val result = session.step(InputFrame.Idle, elapsed)
                if (result.frameRendered) {
                    image = frame.render(session)
                }
                running = result.status == SessionStatus.Running
            }
        }
    }

    Canvas(modifier = modifier) {
        image?.let { drawScaled(it) }
    }
}

/**
 * Scales the frame to fit while preserving its aspect ratio, with nearest-neighbour
 * sampling so pixels stay pixels. Integer scaling and the upscalers arrive with the
 * graphics backends.
 */
private fun DrawScope.drawScaled(image: ImageBitmap) {
    val scale = min(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset =
            IntOffset(
                x = ((size.width - width) / 2).roundToInt(),
                y = ((size.height - height) / 2).roundToInt(),
            ),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.None,
    )
}
