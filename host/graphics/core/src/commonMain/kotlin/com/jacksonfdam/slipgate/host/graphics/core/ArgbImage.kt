package com.jacksonfdam.slipgate.host.graphics.core

/**
 * A frame already resolved to 0xAARRGGBB pixels in host memory.
 *
 * Only the CPU backends produce this. A shader backend resolves the palette on the GPU and
 * never materialises an image like this one, which is the entire reason it is faster.
 */
public class ArgbImage(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "an image must have a positive size" }
        require(pixels.size >= width * height) {
            "pixel array holds ${pixels.size} entries, expected at least ${width * height}"
        }
    }
}

/**
 * A renderer that hands finished pixels back instead of owning a surface. The presentation
 * layer draws them, which is what makes the classic path work anywhere Compose runs.
 */
public interface CpuFrameRenderer : FrameRenderer {
    /** The most recently presented frame. Valid until the next [FrameRenderer.present]. */
    public fun image(): ArgbImage
}
