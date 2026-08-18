// Every parameter here is consumed inside a js(...) body, which static analysis cannot see, and
// setViewport's six arguments are WebGPU's own signature rather than a choice made here. The
// file is long because it mirrors an API; splitting it would scatter one vocabulary across files.
@file:Suppress("UnusedParameter", "LongParameterList", "TooManyFunctions")

package com.jacksonfdam.slipgate.host.graphics.backend.webgpu

import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Promise

/**
 * The slice of the browser's WebGPU API Slipgate uses. Hand-written because the pipeline needs
 * exactly six objects and one draw call; a full binding would be more surface than the renderer
 * touches.
 *
 * Descriptors are plain JavaScript objects built by the `js(...)` factories below, which is the
 * only way to express WebGPU's dictionary arguments from Kotlin/Wasm.
 */
internal external interface GpuAdapter : JsAny {
    fun requestDevice(): Promise<GpuDevice>
}

internal external interface GpuDevice : JsAny {
    val queue: GpuQueue

    fun createShaderModule(descriptor: JsAny): JsAny

    fun createRenderPipeline(descriptor: JsAny): GpuRenderPipeline

    fun createTexture(descriptor: JsAny): GpuTexture

    fun createSampler(descriptor: JsAny): JsAny

    fun createBindGroup(descriptor: JsAny): JsAny

    fun createCommandEncoder(): GpuCommandEncoder

    fun destroy()
}

internal external interface GpuQueue : JsAny {
    fun writeTexture(
        destination: JsAny,
        data: JsAny,
        dataLayout: JsAny,
        size: JsAny,
    )

    fun submit(commandBuffers: JsArray<JsAny>)
}

internal external interface GpuRenderPipeline : JsAny {
    fun getBindGroupLayout(index: Int): JsAny
}

internal external interface GpuTexture : JsAny {
    fun createView(): JsAny

    fun destroy()
}

internal external interface GpuCommandEncoder : JsAny {
    fun beginRenderPass(descriptor: JsAny): GpuRenderPass

    fun finish(): JsAny
}

internal external interface GpuRenderPass : JsAny {
    fun setPipeline(pipeline: GpuRenderPipeline)

    fun setBindGroup(
        index: Int,
        bindGroup: JsAny,
    )

    fun setViewport(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        minDepth: Float,
        maxDepth: Float,
    )

    fun draw(vertexCount: Int)

    fun end()
}

internal external interface GpuCanvasContext : JsAny {
    fun configure(configuration: JsAny)

    fun getCurrentTexture(): GpuTexture
}

internal fun browserHasWebGpu(): Boolean = js("typeof navigator !== 'undefined' && navigator.gpu !== undefined")

internal fun requestGpuAdapter(): Promise<JsAny?> = js("navigator.gpu.requestAdapter()")

internal fun preferredCanvasFormat(): String = js("navigator.gpu.getPreferredCanvasFormat()")

internal fun webGpuContext(canvas: HTMLCanvasElement): GpuCanvasContext? = js("canvas.getContext('webgpu')")

internal fun asAdapter(value: JsAny): GpuAdapter = js("value")

internal fun canvasConfiguration(
    device: GpuDevice,
    format: String,
): JsAny = js("({ device: device, format: format, alphaMode: 'premultiplied' })")

internal fun shaderModuleDescriptor(code: String): JsAny = js("({ code: code })")

internal fun renderPipelineDescriptor(
    module: JsAny,
    format: String,
): JsAny =
    js(
        """({
            layout: 'auto',
            vertex: { module: module, entryPoint: 'vertexMain' },
            fragment: { module: module, entryPoint: 'fragmentMain', targets: [{ format: format }] },
            primitive: { topology: 'triangle-strip' }
        })""",
    )

internal fun textureDescriptor(
    width: Int,
    height: Int,
    format: String,
): JsAny =
    js(
        """({
            size: { width: width, height: height },
            format: format,
            usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST
        })""",
    )

internal fun nearestSamplerDescriptor(): JsAny = js("({ magFilter: 'nearest', minFilter: 'nearest' })")

internal fun bindGroupDescriptor(
    layout: JsAny,
    sampler: JsAny,
    indexedView: JsAny,
    paletteView: JsAny,
): JsAny =
    js(
        """({
            layout: layout,
            entries: [
                { binding: 0, resource: sampler },
                { binding: 1, resource: indexedView },
                { binding: 2, resource: paletteView }
            ]
        })""",
    )

internal fun renderPassDescriptor(view: JsAny): JsAny =
    js(
        """({
            colorAttachments: [{
                view: view,
                clearValue: { r: 0, g: 0, b: 0, a: 1 },
                loadOp: 'clear',
                storeOp: 'store'
            }]
        })""",
    )

internal fun textureCopyDestination(texture: GpuTexture): JsAny = js("({ texture: texture })")

internal fun textureDataLayout(bytesPerRow: Int): JsAny = js("({ bytesPerRow: bytesPerRow })")

internal fun textureCopySize(
    width: Int,
    height: Int,
): JsAny = js("({ width: width, height: height })")

internal fun allocateBytes(size: Int): JsAny = js("new Uint8Array(size)")

internal fun writeByte(
    buffer: JsAny,
    index: Int,
    value: Int,
) {
    js("buffer[index] = value")
}

internal fun singleCommandBuffer(buffer: JsAny): JsArray<JsAny> = js("[buffer]")
