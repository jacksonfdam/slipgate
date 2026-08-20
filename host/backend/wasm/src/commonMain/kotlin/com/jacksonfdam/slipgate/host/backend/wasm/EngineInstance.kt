package com.jacksonfdam.slipgate.host.backend.wasm

/**
 * One running engine module, whatever is executing it.
 *
 * Two things execute one: Chasm, which runs everywhere Kotlin has a JVM or a native target, and the
 * browser's own WebAssembly engine, which is the only one available on the web and much the faster of
 * the two. The session above this does not care which — it boots, steps, and reads frames.
 */
public interface EngineInstance {
    /** Bytes of the most recent frame, in the engine's own pixel format. */
    public fun framebuffer(): ByteArray

    public fun framebufferWidth(): Int

    public fun framebufferHeight(): Int

    /** The engine's palette as 256 red-green-blue triples. */
    public fun palette(): ByteArray

    /** Advances the engine by [elapsedMillis] and returns the status flags it reports. */
    public fun step(elapsedMillis: Int): Int

    public fun pushEvent(
        type: Int,
        code: Int,
        value: Int,
    )

    /** Drains rendered audio into [destination] and returns how many frames arrived. */
    public fun drainAudio(
        destination: ByteArray,
        frames: Int,
    ): Int

    /**
     * Starts playback of a demo the game data carries, and reports whether the engine took it.
     *
     * [untilTheEnd] decides what happens when the demo runs out: the session finishes, or the engine
     * returns to its title screen and carries on — which is what an attract loop wants.
     */
    public fun playDemo(
        name: String,
        untilTheEnd: Boolean,
    ): Boolean

    /** Everything the engine has written to its own filesystem: savegames, and Hexen's hub files. */
    public fun savedFiles(): Map<String, ByteArray>

    /** Writes one file the host kept back into the engine's filesystem. Returns whether it landed. */
    public fun putSavedFile(
        name: String,
        bytes: ByteArray,
    ): Boolean
}

/**
 * Boots an engine module on whatever this platform can execute it with.
 *
 * [files] is the game data, copied into the module because the two do not share an address space, and
 * [saves] is whatever the host kept from a previous session — written in before the engine looks.
 */
public expect suspend fun startEngine(
    moduleBytes: ByteArray,
    files: Map<String, ByteArray>,
    arguments: List<String>,
    host: WasmHost,
    saves: Map<String, ByteArray> = emptyMap(),
): EngineInstance
