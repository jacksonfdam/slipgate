package com.jacksonfdam.slipgate.host.backend.wasm

/**
 * What an engine module can ask of the shell while it runs.
 *
 * The list is deliberately short. An engine that could ask for more would be an engine the host has
 * to trust; this one can report that it died and it can talk, and everything else it needs arrives
 * before it starts.
 */
public interface WasmHost {
    /** The engine has stopped and will not run again. */
    public fun fatal(message: String)

    /** Diagnostics from the engine's own start-up and error paths. */
    public fun log(message: String)
}
