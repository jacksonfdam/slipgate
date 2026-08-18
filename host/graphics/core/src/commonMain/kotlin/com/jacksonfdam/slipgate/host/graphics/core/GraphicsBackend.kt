package com.jacksonfdam.slipgate.host.graphics.core

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat

/** The rendering paths Slipgate can present a frame through. */
public enum class GraphicsBackendId {
    /** WebGPU: browser API on web, Jetpack WebGPU on Android. */
    WebGpu,

    /** Skia runtime effects, used on iOS. */
    Skia,

    /** Platform 2D drawing with no shaders. Always available, always the last resort. */
    Classic,
}

/**
 * One frame ready to present. Palette-indexed frames carry their palette so the lookup can
 * happen in a shader; the arrays belong to the session and must not be retained.
 */
public data class PresentedFrame(
    val format: DisplayFormat,
    val pixels: ByteArray,
    val palette: IntArray?,
) {
    init {
        require(pixels.size >= format.frameSizeBytes) {
            "frame is ${pixels.size} bytes, expected at least ${format.frameSizeBytes}"
        }
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = format.hashCode()
}

/** Draws frames onto one surface. Owned by whoever created it. */
public interface FrameRenderer {
    public val backendId: GraphicsBackendId

    /** Presents [frame] in [viewport]. Called once per displayed frame. */
    public fun present(
        frame: PresentedFrame,
        viewport: Viewport,
    )

    public fun close()
}

/**
 * A rendering path the platform may or may not be able to use. Backends report their own
 * availability so selection is a runtime decision, not a compile-time one.
 */
public interface GraphicsBackend {
    public val id: GraphicsBackendId

    /** Whether this backend can run here, right now. */
    public fun isAvailable(): Boolean

    public fun createRenderer(format: DisplayFormat): FrameRenderer
}
