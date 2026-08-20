package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.controls.ControlState
import com.jacksonfdam.slipgate.host.controls.VirtualGamepad
import com.jacksonfdam.slipgate.host.controls.gateKeyboard
import com.jacksonfdam.slipgate.host.graphics.backend.classic.ClassicBackend
import com.jacksonfdam.slipgate.host.graphics.backend.skia.ComposeFrameRenderer
import com.jacksonfdam.slipgate.host.graphics.backend.skia.skiaBackend
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelection
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelector
import com.jacksonfdam.slipgate.host.graphics.core.CpuFrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.FrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.host.graphics.core.SurfaceSize
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.graphics.core.ViewportRect
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.host.runtime.SessionStatus

private val EmptyRect = ViewportRect(x = 0, y = 0, width = 0, height = 0)

/**
 * Draws a running session, stepping it once per display frame through the selected graphics
 * backend.
 *
 * A [paused] surface keeps drawing the last frame it was given but stops stepping the session, so a
 * game does not play on behind the menu a player opened over it. Held controls are forgotten on the
 * way in, because a thumb that was on the fire button when the menu opened is not still on it — and
 * the keyboard is let go of too, so Escape reaches the menu rather than the game behind it.
 *
 * Two kinds of renderer are handled. A shader renderer draws into Compose's own canvas, so the
 * frame counter is what invalidates the draw. A CPU renderer hands back pixels, so the uploaded
 * image is what invalidates it.
 */
@Composable
public fun GateSurface(
    session: GateSession,
    inputProfile: InputProfile,
    modifier: Modifier = Modifier,
    crt: CrtSettings = CrtSettings.Default,
    scaling: ScalingMode = ScalingMode.Fit,
    paused: Boolean = false,
) {
    val controls = remember(session) { ControlState() }
    // The backend is built here rather than injected, because the tube settings are a player's
    // choice: a selector built once at start-up could never change with them.
    val selection =
        remember(crt) {
            BackendSelector(candidates = listOfNotNull(skiaBackend(crt), ClassicBackend())).select()
        }
    val presentation =
        remember(session, selection, scaling) {
            GatePresentation(
                session = session,
                renderer = selection.backend.createRenderer(session.display),
                scaling = scaling,
            )
        }

    DisposableEffect(presentation) {
        onDispose { presentation.close() }
    }

    LaunchedEffect(presentation, paused) {
        if (paused) {
            controls.releaseAll()
            return@LaunchedEffect
        }
        var previousFrameMillis = 0L
        var running = true
        while (running) {
            withFrameMillis { frameTimeMillis ->
                val elapsed =
                    if (previousFrameMillis == 0L) 0L else frameTimeMillis - previousFrameMillis
                previousFrameMillis = frameTimeMillis
                running = presentation.step(elapsed, controls.frame())
            }
        }
    }

    Box(
        // A physical keyboard reaches the gate here rather than through the pad: the two write to the
        // same control state, so a player can hold a key and tap a button in the same frame.
        modifier = if (paused) modifier else modifier.gateKeyboard(controls),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { presentation.resize(SurfaceSize(it.width, it.height)) },
        ) {
            presentation.draw(this, presentation.presentedFrames)
        }
        if (!paused) {
            VirtualGamepad(profile = inputProfile, state = controls)
        }
        BackendLabel(
            text = selection.describe(),
            // Bottom centre: the top left corner belongs to the menu button, the top right to the
            // pad's utility row, and both bottom corners to a thumb.
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        )
    }
}

/**
 * Holds what one session needs to reach the screen: the surface it was measured against, the
 * viewport it was fitted into, and whichever of the two renderer shapes the backend provides.
 *
 * A shader renderer draws into Compose's own canvas; a CPU renderer hands back pixels the host
 * uploads. Both are driven the same way from here.
 */
private class GatePresentation(
    private val session: GateSession,
    private val renderer: FrameRenderer,
    private val scaling: ScalingMode,
) {
    private val shaderRenderer = renderer as? ComposeFrameRenderer
    private val cpuRenderer = renderer as? CpuFrameRenderer

    private var surface by mutableStateOf(SurfaceSize(width = 0, height = 0))
    private var viewport by mutableStateOf<Viewport?>(null)
    private var destination by mutableStateOf(EmptyRect)
    private var image by mutableStateOf<ImageBitmap?>(null)

    /** Counts presented frames, so a draw that reads it repeats whenever a new frame lands. */
    var presentedFrames: Int by mutableIntStateOf(0)
        private set

    fun resize(size: SurfaceSize) {
        surface = size
    }

    /** Steps the session and presents what it drew. Returns whether the session is still running. */
    fun step(
        elapsedMillis: Long,
        input: InputFrame,
    ): Boolean {
        val result = session.step(input, elapsedMillis)
        if (result.frameRendered && !surface.isEmpty) {
            val current = Viewport(source = session.display, surface = surface, mode = scaling)
            renderer.present(
                PresentedFrame(
                    format = session.display,
                    pixels = session.framebuffer(),
                    palette = session.palette(),
                ),
                current,
            )
            viewport = current
            destination = current.destination()
            image = cpuRenderer?.image()?.toImageBitmap()
            presentedFrames++
        }
        return result.status == SessionStatus.Running
    }

    /**
     * Draws the last presented frame. [frameVersion] is read by the caller so Compose repeats the
     * draw when a new frame arrives; nothing in here needs its value.
     */
    fun draw(
        scope: DrawScope,
        @Suppress("UNUSED_PARAMETER") frameVersion: Int,
    ) {
        val currentViewport = viewport ?: return
        val shader = shaderRenderer
        when {
            shader != null -> shader.draw(scope, currentViewport)
            else -> image?.let { scope.drawUploadedFrame(it, destination) }
        }
    }

    fun close() {
        renderer.close()
    }
}

/** Nearest-neighbour blit of an uploaded frame: pixels stay pixels. */
private fun DrawScope.drawUploadedFrame(
    frame: ImageBitmap,
    destination: ViewportRect,
) {
    drawImage(
        image = frame,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(frame.width, frame.height),
        dstOffset = IntOffset(destination.x, destination.y),
        dstSize = IntSize(destination.width, destination.height),
        filterQuality = FilterQuality.None,
    )
}

private fun BackendSelection.describe(): String = if (fellBack) "${backend.id} (fallback)" else "${backend.id}"

/** Names the active rendering path, so a silent fallback is never invisible. */
@Composable
private fun BackendLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = modifier,
    )
}
