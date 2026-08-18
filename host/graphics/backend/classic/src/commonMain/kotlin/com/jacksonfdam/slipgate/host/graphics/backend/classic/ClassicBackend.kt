package com.jacksonfdam.slipgate.host.graphics.backend.classic

import com.jacksonfdam.slipgate.host.graphics.core.ArgbImage
import com.jacksonfdam.slipgate.host.graphics.core.CpuFrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat

/**
 * The path that always works: resolve the palette on the CPU and hand the pixels to whatever
 * 2D drawing the platform offers — `Bitmap` on Android, the browser's canvas on web.
 *
 * It has no feature detection because there is nothing to detect. Every other backend can
 * decline; this one is why declining is safe.
 */
public class ClassicBackend : GraphicsBackend {
    override val id: GraphicsBackendId = GraphicsBackendId.Classic

    override fun isAvailable(): Boolean = true

    override fun createRenderer(format: DisplayFormat): CpuFrameRenderer = ClassicFrameRenderer(format)
}

internal class ClassicFrameRenderer(
    private val format: DisplayFormat,
) : CpuFrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.Classic

    private val resolver = PaletteResolver(format.width * format.height)
    private val image = ArgbImage(format.width, format.height, resolver.output)
    private var presented = false

    override fun present(
        frame: PresentedFrame,
        viewport: Viewport,
    ) {
        require(frame.format == format) {
            "renderer was created for $format but was given ${frame.format}"
        }
        resolver.resolve(frame)
        presented = true
    }

    override fun image(): ArgbImage {
        check(presented) { "no frame has been presented yet" }
        return image
    }

    override fun close() {
        presented = false
    }
}
