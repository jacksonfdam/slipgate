package com.jacksonfdam.slipgate.host.graphics.backend.skia

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws one full-surface scene shader into a Compose canvas.
 *
 * A scene is anything the interface draws with a fragment shader rather than with composables: a
 * gate's portrait, the attract background behind the rack. A painter owns the compiled shader, so
 * callers keep one rather than recompiling every frame.
 */
public interface ScenePainter {
    public fun draw(
        scope: DrawScope,
        uniforms: SceneUniforms,
    )
}

/**
 * The painter for the scene shader named [shaderName], or null when this platform cannot run a
 * runtime shader or no shader of that name exists. A null is the caller's cue to draw its composed
 * fallback — degraded, not broken.
 */
public expect fun scenePainter(shaderName: String): ScenePainter?

/**
 * The painter for [gateId]'s portrait — the gate's cover, since there is no cover art in this
 * project, so what a card and the stage show is a live shader with the gate's own accent in it.
 */
public fun portraitPainter(gateId: String): ScenePainter? = scenePainter("portrait_$gateId")
