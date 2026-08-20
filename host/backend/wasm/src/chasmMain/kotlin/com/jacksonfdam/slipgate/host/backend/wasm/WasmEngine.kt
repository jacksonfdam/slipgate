package com.jacksonfdam.slipgate.host.backend.wasm

import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue

/**
 * Drives one engine module.
 *
 * Every gate that runs on WebAssembly speaks the same exported surface, so this class knows how to
 * boot and step an engine without knowing which one it is. What it does not do is decide anything:
 * the caller supplies the module, the data and the command line, and reads frames back out.
 */
@Suppress("TooManyFunctions") // The class mirrors the module's exported surface, not a choice here.
public class WasmEngine private constructor(
    private val store: Store,
    private val instance: Instance,
    private val memory: Memory,
) : EngineInstance {
    private var audioPointer = 0
    private var audioCapacityFrames = 0

    /** Bytes of the most recent frame, in the engine's own pixel format. */
    override fun framebuffer(): ByteArray = read(call(FRAMEBUFFER), call(FRAMEBUFFER_SIZE))

    override fun framebufferWidth(): Int = call(FRAMEBUFFER_WIDTH)

    override fun framebufferHeight(): Int = call(FRAMEBUFFER_HEIGHT)

    /** The engine's palette as 256 red-green-blue triples. */
    override fun palette(): ByteArray = read(call(PALETTE), PALETTE_BYTES)

    /**
     * Starts playback of a demo the game data carries, and reports whether the engine took it.
     *
     * [untilTheEnd] decides what happens when the demo runs out: the session finishes, or the engine
     * returns to its title screen and carries on — which is what an attract loop wants.
     */
    override fun playDemo(
        name: String,
        untilTheEnd: Boolean,
    ): Boolean {
        val pointer = writeString(name)
        val accepted = call(PLAY_DEMO, pointer, if (untilTheEnd) 1 else 0) == 1
        call(FREE, pointer)
        return accepted
    }

    /**
     * Everything the engine has written to its own filesystem: savegames, its config, and for Hexen a
     * file per map of a hub.
     *
     * Read rather than intercepted. The engine saves by writing a file, and a host that wanted to
     * catch those writes would have to reimplement enough of stdio to fool it; reading the directory
     * afterwards is the same information for none of the risk.
     */
    override fun savedFiles(): Map<String, ByteArray> {
        val count = call(SAVE_SCAN)
        if (count <= 0) {
            return emptyMap()
        }
        val namePointer = call(ALLOC, MAX_SAVE_NAME_BYTES)
        check(namePointer != 0) { "the engine could not allocate a name buffer" }
        val saved = mutableMapOf<String, ByteArray>()
        for (index in 0 until count) {
            val size = call(SAVE_SIZE, index)
            val length = call(SAVE_NAME, index, namePointer, MAX_SAVE_NAME_BYTES)
            if (size < 0 || length <= 0) {
                continue
            }
            val name = read(namePointer, length).decodeToString()
            val dataPointer = call(ALLOC, size)
            check(dataPointer != 0) { "the engine could not allocate $size bytes for $name" }
            if (call(SAVE_READ, index, dataPointer, size) == size) {
                saved[name] = read(dataPointer, size)
            }
            call(FREE, dataPointer)
        }
        call(FREE, namePointer)
        return saved
    }

    /** Writes one file the host kept back into the engine's filesystem. Returns whether it landed. */
    override fun putSavedFile(
        name: String,
        bytes: ByteArray,
    ): Boolean {
        val namePointer = writeString(name)
        val dataPointer = call(ALLOC, bytes.size)
        check(dataPointer != 0) { "the engine could not allocate ${bytes.size} bytes for $name" }
        writeBytes(store, memory, dataPointer, bytes).expect("could not write $name into the engine")
        val stored = call(SAVE_PUT, namePointer, dataPointer, bytes.size) == 1
        call(FREE, dataPointer)
        call(FREE, namePointer)
        return stored
    }

    /** Advances the engine by [elapsedMillis] and returns the status flags it reports. */
    override fun step(elapsedMillis: Int): Int = call(STEP, elapsedMillis)

    override fun pushEvent(
        type: Int,
        code: Int,
        value: Int,
    ) {
        call(PUSH_EVENT, type, code, value)
    }

    /**
     * Drains rendered audio into [destination] and returns how many frames arrived.
     *
     * The buffer inside the engine is allocated once and kept: this runs every frame, and asking a
     * WebAssembly heap for the same block sixty times a second fragments it for no gain.
     */
    override fun drainAudio(
        destination: ByteArray,
        frames: Int,
    ): Int {
        if (frames <= 0) {
            return 0
        }
        require(frames * AUDIO_FRAME_BYTES <= destination.size) {
            "asked for $frames frames but the destination holds ${destination.size / AUDIO_FRAME_BYTES}"
        }
        val drained = call(AUDIO_DRAIN, audioBuffer(frames), frames)
        if (drained > 0) {
            readBytes(store, memory, destination, audioPointer, drained * AUDIO_FRAME_BYTES)
                .expect("could not read drained audio")
        }
        return drained
    }

    private fun audioBuffer(frames: Int): Int {
        if (frames > audioCapacityFrames) {
            if (audioPointer != 0) {
                call(FREE, audioPointer)
            }
            audioPointer = call(ALLOC, frames * AUDIO_FRAME_BYTES)
            check(audioPointer != 0) { "the engine could not allocate an audio buffer" }
            audioCapacityFrames = frames
        }
        return audioPointer
    }

    private fun read(
        pointer: Int,
        size: Int,
    ): ByteArray {
        val destination = ByteArray(size)
        readBytes(store, memory, destination, pointer, size)
            .expect("could not read $size bytes of engine memory at $pointer")
        return destination
    }

    private fun writeString(value: String): Int {
        val bytes = value.encodeToByteArray() + 0
        val pointer = call(ALLOC, bytes.size)
        check(pointer != 0) { "the engine could not allocate ${bytes.size} bytes" }
        writeBytes(store, memory, pointer, bytes).expect("could not write a string into the engine")
        return pointer
    }

    private fun call(
        name: String,
        vararg arguments: Int,
    ): Int {
        val results =
            invoke(store, instance, name, arguments.map { NumberValue.I32(it) })
                .expect("$name did not run")
        return (results.firstOrNull() as? NumberValue.I32)?.value ?: 0
    }

    public companion object {
        private const val ALLOC = "slipgate_alloc"
        private const val FREE = "slipgate_free"
        private const val ARG_PUSH = "slipgate_arg_push"
        private const val MOUNT = "slipgate_mount"
        private const val INIT = "slipgate_init"
        private const val STEP = "slipgate_step"
        private const val FRAMEBUFFER = "slipgate_framebuffer"
        private const val FRAMEBUFFER_SIZE = "slipgate_framebuffer_size"
        private const val FRAMEBUFFER_WIDTH = "slipgate_framebuffer_width"
        private const val FRAMEBUFFER_HEIGHT = "slipgate_framebuffer_height"
        private const val PALETTE = "slipgate_palette"
        private const val PUSH_EVENT = "slipgate_push_event"
        private const val AUDIO_DRAIN = "slipgate_audio_drain"
        private const val PLAY_DEMO = "slipgate_play_demo"
        private const val SAVE_SCAN = "slipgate_save_scan"
        private const val SAVE_SIZE = "slipgate_save_size"
        private const val SAVE_NAME = "slipgate_save_name"
        private const val SAVE_READ = "slipgate_save_read"
        private const val SAVE_PUT = "slipgate_save_put"

        /** As long a name as the module will report; see MAX_PATH_BYTES in platform/sg_saves.c. */
        private const val MAX_SAVE_NAME_BYTES = 128

        private const val PALETTE_BYTES = 768
        private const val AUDIO_FRAME_BYTES = 4

        /**
         * Instantiates [moduleBytes], mounts [files] into the engine's memory, and runs the
         * engine's start-up with [arguments].
         *
         * Mounting copies each file into the module rather than pointing at host memory, because
         * the two do not share an address space — that copy is the price of a sandbox.
         *
         * [saves] is whatever the host kept from a previous session, written into the engine's own
         * filesystem before it starts. What comes back out is [savedFiles].
         */
        public fun start(
            moduleBytes: ByteArray,
            files: Map<String, ByteArray>,
            arguments: List<String>,
            host: WasmHost,
            saves: Map<String, ByteArray> = emptyMap(),
        ): WasmEngine {
            val store = store()
            val parsed = module(moduleBytes).expect("the engine module did not parse")
            // The bridge needs the instance's memory to answer anything, and the instance needs the
            // bridge's imports to exist: it learns the memory as soon as there is one.
            val bridge = EngineBridge(store, host)
            val created =
                instance(store, parsed, bridge.imports())
                    .expect("the engine module did not instantiate")
            val memory =
                exports(created)
                    .firstNotNullOfOrNull { it.value as? Memory }
                    ?: error("the engine module exports no memory")
            bridge.memory = memory

            val engine = WasmEngine(store, created, memory)
            files.forEach { (name, bytes) -> engine.mount(name, bytes) }
            // Before start-up, because start-up is when the engine reads its config, and a player's
            // settings are one of the files this carries.
            saves.forEach { (name, bytes) -> engine.putSavedFile(name, bytes) }
            arguments.forEach { argument ->
                check(engine.call(ARG_PUSH, engine.writeString(argument)) == 1) {
                    "the engine refused the argument $argument"
                }
            }
            engine.call(INIT)
            return engine
        }
    }

    private fun mount(
        name: String,
        bytes: ByteArray,
    ) {
        val namePointer = writeString(name)
        val dataPointer = call(ALLOC, bytes.size)
        check(dataPointer != 0) { "the engine could not allocate ${bytes.size} bytes for $name" }
        writeBytes(store, memory, dataPointer, bytes).expect("could not write $name into the engine")
        check(call(MOUNT, namePointer, dataPointer, bytes.size) == 1) {
            "the engine refused to mount $name"
        }
    }
}

/** Chasm runs the module everywhere but the web, where the browser has an engine of its own. */
public actual suspend fun startEngine(
    moduleBytes: ByteArray,
    files: Map<String, ByteArray>,
    arguments: List<String>,
    host: WasmHost,
    saves: Map<String, ByteArray>,
): EngineInstance =
    WasmEngine.start(
        moduleBytes = moduleBytes,
        files = files,
        arguments = arguments,
        host = host,
        saves = saves,
    )
