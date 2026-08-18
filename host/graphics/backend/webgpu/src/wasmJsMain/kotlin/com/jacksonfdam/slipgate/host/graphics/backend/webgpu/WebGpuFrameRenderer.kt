package com.jacksonfdam.slipgate.host.graphics.backend.webgpu

import com.jacksonfdam.slipgate.host.graphics.core.FrameRenderer
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.PresentedFrame
import com.jacksonfdam.slipgate.host.graphics.core.Viewport
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import org.w3c.dom.HTMLCanvasElement

private const val PALETTE_ENTRIES = 256
private const val PALETTE_HEIGHT = 1
private const val BYTES_PER_PALETTE_ENTRY = 4
private const val QUAD_VERTICES = 4
private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val INDEXED_TEXTURE_FORMAT = "r8unorm"
private const val PALETTE_TEXTURE_FORMAT = "rgba8unorm"
private const val SHADER_NAME = "palette_indexed"
private const val PALETTE_GREEN_BYTE = 1
private const val PALETTE_BLUE_BYTE = 2
private const val PALETTE_ALPHA_BYTE = 3

/**
 * Uploads the indexed frame and its palette as two textures and resolves the colour in the
 * fragment shader. The palette never reaches the CPU as pixels, which is the whole point: a
 * palette change is a 1 KB upload rather than a full reconversion of the frame.
 */
internal class WebGpuFrameRenderer(
    private val device: GpuDevice,
    private val context: GpuCanvasContext,
    canvasFormat: String,
    private val canvas: HTMLCanvasElement,
    private val format: DisplayFormat,
) : FrameRenderer {
    override val backendId: GraphicsBackendId = GraphicsBackendId.WebGpu

    private val shader =
        device.createShaderModule(
            shaderModuleDescriptor(
                requireNotNull(wgslSources[SHADER_NAME]) { "missing shader $SHADER_NAME" },
            ),
        )
    private val pipeline = device.createRenderPipeline(renderPipelineDescriptor(shader, canvasFormat))
    private val sampler = device.createSampler(nearestSamplerDescriptor())
    private val indexedTexture =
        device.createTexture(
            textureDescriptor(format.width, format.height, INDEXED_TEXTURE_FORMAT),
        )
    private val paletteTexture =
        device.createTexture(
            textureDescriptor(PALETTE_ENTRIES, PALETTE_HEIGHT, PALETTE_TEXTURE_FORMAT),
        )
    private val bindGroup =
        device.createBindGroup(
            bindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0),
                sampler = sampler,
                indexedView = indexedTexture.createView(),
                paletteView = paletteTexture.createView(),
            ),
        )

    private val frameStaging = allocateBytes(format.width * format.height)
    private val paletteStaging = allocateBytes(PALETTE_ENTRIES * BYTES_PER_PALETTE_ENTRY)
    private var uploadedPalette: IntArray? = null

    init {
        require(format.pixelFormat == PixelFormat.Indexed8) {
            "the WebGPU pipeline presents indexed frames; got ${format.pixelFormat}"
        }
    }

    override fun present(
        frame: PresentedFrame,
        viewport: Viewport,
    ) {
        require(frame.format == format) {
            "renderer was created for $format but was given ${frame.format}"
        }
        resizeCanvas(viewport)
        uploadFrame(frame)
        uploadPaletteIfChanged(frame)
        draw(viewport)
    }

    override fun close() {
        indexedTexture.destroy()
        paletteTexture.destroy()
    }

    private fun resizeCanvas(viewport: Viewport) {
        if (canvas.width != viewport.surface.width) {
            canvas.width = viewport.surface.width
        }
        if (canvas.height != viewport.surface.height) {
            canvas.height = viewport.surface.height
        }
    }

    private fun uploadFrame(frame: PresentedFrame) {
        val pixels = frame.pixels
        for (index in 0 until format.width * format.height) {
            writeByte(frameStaging, index, pixels[index].toInt() and BYTE_MASK)
        }
        device.queue.writeTexture(
            destination = textureCopyDestination(indexedTexture),
            data = frameStaging,
            dataLayout = textureDataLayout(format.width),
            size = textureCopySize(format.width, format.height),
        )
    }

    private fun uploadPaletteIfChanged(frame: PresentedFrame) {
        val palette = requireNotNull(frame.palette) { "an indexed frame must carry a palette" }
        if (uploadedPalette.contentEqualsPalette(palette)) {
            return
        }
        for (entry in 0 until PALETTE_ENTRIES) {
            val colour = palette[entry]
            val offset = entry * BYTES_PER_PALETTE_ENTRY
            writeByte(paletteStaging, offset, colour shr RED_SHIFT and BYTE_MASK)
            writeByte(paletteStaging, offset + PALETTE_GREEN_BYTE, colour shr GREEN_SHIFT and BYTE_MASK)
            writeByte(paletteStaging, offset + PALETTE_BLUE_BYTE, colour and BYTE_MASK)
            writeByte(paletteStaging, offset + PALETTE_ALPHA_BYTE, colour shr ALPHA_SHIFT and BYTE_MASK)
        }
        device.queue.writeTexture(
            destination = textureCopyDestination(paletteTexture),
            data = paletteStaging,
            dataLayout = textureDataLayout(PALETTE_ENTRIES * BYTES_PER_PALETTE_ENTRY),
            size = textureCopySize(PALETTE_ENTRIES, PALETTE_HEIGHT),
        )
        uploadedPalette = palette.copyOf()
    }

    private fun draw(viewport: Viewport) {
        val destination = viewport.destination()
        if (destination.width == 0 || destination.height == 0) {
            return
        }
        val encoder = device.createCommandEncoder()
        val pass = encoder.beginRenderPass(renderPassDescriptor(context.getCurrentTexture().createView()))
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, bindGroup)
        pass.setViewport(
            x = destination.x.toFloat(),
            y = destination.y.toFloat(),
            width = destination.width.toFloat(),
            height = destination.height.toFloat(),
            minDepth = 0f,
            maxDepth = 1f,
        )
        pass.draw(QUAD_VERTICES)
        pass.end()
        device.queue.submit(singleCommandBuffer(encoder.finish()))
    }
}

private fun IntArray?.contentEqualsPalette(other: IntArray): Boolean = this != null && this.contentEquals(other)
