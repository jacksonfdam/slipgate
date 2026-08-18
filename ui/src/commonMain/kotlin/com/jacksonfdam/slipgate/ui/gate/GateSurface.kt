package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelector
import com.jacksonfdam.slipgate.host.graphics.core.CpuFrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.SurfaceSize
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.graphics.core.ViewportRect
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import org.koin.compose.koinInject

private val EmptyRect = ViewportRect(x = 0, y = 0, width = 0, height = 0)

/**
 * Draws a running session, stepping it once per display frame through the selected graphics
 * backend. Input is idle for now; the control layer feeds real frames once it exists.
 */
@Composable
public fun GateSurface(
    session: GateSession,
    modifier: Modifier = Modifier,
    selector: BackendSelector = koinInject(),
) {
    val selection = remember(session, selector) { selector.select() }
    val renderer = remember(session, selection) { selection.backend.createRenderer(session.display) }
    var surface by remember(session) { mutableStateOf(SurfaceSize(width = 0, height = 0)) }
    var destination by remember(session) { mutableStateOf(EmptyRect) }
    var image by remember(session) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    LaunchedEffect(session, renderer) {
        var previousFrameMillis = 0L
        var running = true
        while (running) {
            withFrameMillis { frameTimeMillis ->
                val elapsed =
                    if (previousFrameMillis == 0L) 0L else frameTimeMillis - previousFrameMillis
                previousFrameMillis = frameTimeMillis
                val result = session.step(InputFrame.Idle, elapsed)
                running = result.status == SessionStatus.Running
                if (result.frameRendered && !surface.isEmpty) {
                    val viewport = Viewport(source = session.display, surface = surface)
                    renderer.present(
                        PresentedFrame(
                            format = session.display,
                            pixels = session.framebuffer(),
                            palette = session.palette(),
                        ),
                        viewport,
                    )
                    destination = viewport.destination()
                    image = (renderer as? CpuFrameRenderer)?.image()?.toImageBitmap()
                }
            }
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { surface = SurfaceSize(it.width, it.height) },
        ) {
            val frame = image ?: return@Canvas
            drawImage(
                image = frame,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(frame.width, frame.height),
                dstOffset = IntOffset(destination.x, destination.y),
                dstSize = IntSize(destination.width, destination.height),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

