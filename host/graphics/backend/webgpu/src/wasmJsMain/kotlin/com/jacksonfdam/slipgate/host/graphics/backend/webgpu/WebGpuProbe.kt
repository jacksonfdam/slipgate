package com.jacksonfdam.slipgate.host.graphics.backend.webgpu

/** Outcome of asking the browser for a WebGPU device. */
public sealed interface WebGpuProbe {
    public data class Ready(
        val backend: WebGpuBackend,
    ) : WebGpuProbe

    /** WebGPU cannot be used here, and [reason] says which step said so. */
    public data class Unavailable(
        val reason: String,
    ) : WebGpuProbe
}
