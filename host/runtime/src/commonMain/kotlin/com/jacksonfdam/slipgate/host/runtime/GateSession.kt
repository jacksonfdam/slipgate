package com.jacksonfdam.slipgate.host.runtime

/** Layout of the pixels a session produces. */
public enum class PixelFormat {
    /** One byte per pixel, resolved through the session's palette. */
    Indexed8,

    /** Four bytes per pixel, red first. */
    Rgba8888,
    ;

    public val bytesPerPixel: Int
        get() =
            when (this) {
                Indexed8 -> 1
                Rgba8888 -> 4
            }
}

/**
 * Dimensions and pixel layout of a session's output. Never assumed by the host.
 *
 * [pixelAspect] is the width-to-height ratio of one source pixel. Only the gate knows it: the
 * id Tech 1 engines render 320x200 for a 4:3 display, so their pixels are not square.
 */
public data class DisplayFormat(
    val width: Int,
    val height: Int,
    val pixelFormat: PixelFormat,
    val pixelAspect: Float = 1f,
) {
    init {
        require(width > 0 && height > 0) { "display must have a positive size" }
        require(pixelAspect > 0f) { "pixel aspect must be positive" }
    }

    public val frameSizeBytes: Int
        get() = width * height * pixelFormat.bytesPerPixel
}

/** Lifecycle state a session reports after a step. */
public enum class SessionStatus {
    Running,
    Finished,
    Failed,
}

/** Outcome of a single step. */
public data class FrameResult(
    val status: SessionStatus,
    val frameRendered: Boolean,
    val paletteChanged: Boolean,
) {
    public companion object {
        public val Skipped: FrameResult =
            FrameResult(
                status = SessionStatus.Running,
                frameRendered = false,
                paletteChanged = false,
            )
    }
}

/**
 * A running game. The host drives it one step at a time and reads the framebuffer it owns;
 * the session decides what a step means internally.
 */
public interface GateSession {
    public val display: DisplayFormat

    /**
     * Palette as 0xAARRGGBB entries, or null when [DisplayFormat.pixelFormat] needs no
     * lookup. The array is owned by the session and must not be mutated by the caller.
     */
    public fun palette(): IntArray?

    /**
     * Most recent frame, [DisplayFormat.frameSizeBytes] long. Owned by the session and
     * valid until the next [step].
     */
    public fun framebuffer(): ByteArray

    public fun step(
        input: InputFrame,
        elapsedMillis: Long,
    ): FrameResult

    /** Serialised state for suspend and resume. */
    public fun snapshot(): ByteArray

    public fun close()
}
