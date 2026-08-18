package com.jacksonfdam.slipgate.host.runtime

/**
 * Execution strategy for a gate session. A gate may support more than one; the platform
 * decides which is used, so the runtime never assumes an engine is interpreted or native.
 */
public enum class BackendId {
    /** The engine runs as a WebAssembly module. */
    Wasm,

    /** The engine runs as compiled native code reached through platform interop. */
    Native,
}
