package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws one gate's portrait shader into a Compose canvas.
 *
 * The portrait is the gate's cover: there is no cover art in this project, so what a card and the
 * stage show is a live fragment shader with the gate's own accent in it. A painter owns the compiled
 * shader, so callers keep one per gate rather than recompiling every frame.
 */
public interface PortraitPainter {
    public fun draw(
        scope: DrawScope,
        uniforms: PortraitUniforms,
    )
}

/**
 * The painter for [gateId], or null when this platform cannot run a runtime shader or this gate has
 * no portrait yet. A null is the caller's cue to draw its composed fallback — degraded, not broken.
 */
public expect fun portraitPainter(gateId: String): PortraitPainter?
