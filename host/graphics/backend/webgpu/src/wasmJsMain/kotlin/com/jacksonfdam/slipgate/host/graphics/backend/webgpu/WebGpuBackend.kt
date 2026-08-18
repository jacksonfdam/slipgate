package com.jacksonfdam.slipgate.host.graphics.backend.webgpu

import com.jacksonfdam.slipgate.host.graphics.core.FrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import kotlinx.coroutines.await
import org.w3c.dom.HTMLCanvasElement

/**
 * The browser's WebGPU implementation, drawing into its own canvas beneath the Compose surface.
 *
 * Acquiring an adapter and a device is asynchronous, so the platform entry point builds this
 * before it starts the shell: by the time selection happens, the answer to "can this render?" is
 * already known and a fallback costs nothing.
 */
public class WebGpuBackend internal constructor(
    private val device: GpuDevice,
    private val context: GpuCanvasContext,
    private val canvasFormat: String,
    private val canvas: HTMLCanvasElement,
) : GraphicsBackend {
    override val id: GraphicsBackendId = GraphicsBackendId.WebGpu

    override fun isAvailable(): Boolean = true

    override fun createRenderer(format: DisplayFormat): FrameRenderer =
        WebGpuFrameRenderer(
            device = device,
            context = context,
            canvasFormat = canvasFormat,
            canvas = canvas,
            format = format,
        )

    public companion object {
        /**
         * Probes WebGPU and claims [canvas].
         *
         * Every negative outcome is reported with the step that produced it. "WebGPU is missing" and
         * "the adapter request came back empty" are both ordinary on the web, but they are not the
         * same fact, and a silent null makes the difference invisible.
         */
        @Suppress("ReturnCount") // Each early return names a different reason WebGPU is out.
        public suspend fun probe(canvas: HTMLCanvasElement): WebGpuProbe {
            if (!browserHasWebGpu()) {
                return WebGpuProbe.Unavailable("navigator.gpu is missing")
            }
            val adapter =
                requestGpuAdapter().await()
                    ?: return WebGpuProbe.Unavailable("no adapter was offered")
            val device = asAdapter(adapter).requestDevice().await()
            val context =
                webGpuContext(canvas)
                    ?: return WebGpuProbe.Unavailable("the canvas refused a webgpu context")
            val format = preferredCanvasFormat()
            context.configure(canvasConfiguration(device, format))
            return WebGpuProbe.Ready(
                WebGpuBackend(
                    device = device,
                    context = context,
                    canvasFormat = format,
                    canvas = canvas,
                ),
            )
        }
    }
}
