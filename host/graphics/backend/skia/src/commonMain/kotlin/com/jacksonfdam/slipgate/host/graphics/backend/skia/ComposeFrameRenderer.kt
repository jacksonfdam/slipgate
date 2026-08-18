package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jacksonfdam.slipgate.host.graphics.core.FrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.Viewport

/**
 * A renderer that draws into the surface Compose already owns.
 *
 * This is what makes the shader path work without a second surface: the runtime effect runs inside
 * Compose's own canvas, so the shell's UI composites over the game the way any other Compose
 * content does. A backend with its own canvas cannot do that on every platform — the web proved it.
 */
public interface ComposeFrameRenderer : FrameRenderer {
    /** Draws the most recently presented frame into [scope] at [viewport]'s destination. */
    public fun draw(
        scope: DrawScope,
        viewport: Viewport,
    )
}
