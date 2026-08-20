@file:OptIn(ExperimentalWasmJsInterop::class)
// The helpers below have JavaScript bodies, which static analysis cannot see reads their parameters.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.host.backend.wasm

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.get

private const val PALETTE_BYTES = 768
private const val AUDIO_FRAME_BYTES = 4

/**
 * An engine module running on the browser's own WebAssembly engine.
 *
 * The web is the one platform that cannot use Chasm — it publishes no wasmJs artifact — and the one
 * platform that needs it least: a browser compiles the module properly, which is the difference
 * between an interpreter and a JIT.
 *
 * The imports are written in JavaScript rather than declared as interfaces, for the same reason the
 * private file system is: what they are is a few lines of the platform's own API, and a table of
 * Kotlin declarations describing it would be longer than the thing itself.
 */
@Suppress("TooManyFunctions") // The class mirrors the module's exported surface, not a choice here.
private class BrowserEngine(
    private val context: JsAny,
    private val host: WasmHost,
) : EngineInstance {
    private var audioPointer = 0
    private var audioCapacityFrames = 0

    override fun framebuffer(): ByteArray = read(call("slipgate_framebuffer"), call("slipgate_framebuffer_size"))

    override fun framebufferWidth(): Int = call("slipgate_framebuffer_width")

    override fun framebufferHeight(): Int = call("slipgate_framebuffer_height")

    override fun palette(): ByteArray = read(call("slipgate_palette"), PALETTE_BYTES)

    override fun step(elapsedMillis: Int): Int {
        val status = call("slipgate_step", elapsedMillis)
        drainLog()
        return status
    }

    override fun pushEvent(
        type: Int,
        code: Int,
        value: Int,
    ) {
        call("slipgate_push_event", type, code, value)
    }

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
        val drained = call("slipgate_audio_drain", audioBuffer(frames), frames)
        if (drained > 0) {
            read(audioPointer, drained * AUDIO_FRAME_BYTES).copyInto(destination)
        }
        return drained
    }

    override fun playDemo(
        name: String,
        untilTheEnd: Boolean,
    ): Boolean {
        val pointer = writeString(name)
        val accepted = call("slipgate_play_demo", pointer, if (untilTheEnd) 1 else 0) == 1
        call("slipgate_free", pointer)
        drainLog()
        return accepted
    }

    override fun savedFiles(): Map<String, ByteArray> {
        val count = call("slipgate_save_scan")
        if (count <= 0) {
            return emptyMap()
        }
        val namePointer = call("slipgate_alloc", MAX_SAVE_NAME_BYTES)
        val saved = mutableMapOf<String, ByteArray>()
        for (index in 0 until count) {
            val size = call("slipgate_save_size", index)
            val length = call("slipgate_save_name", index, namePointer, MAX_SAVE_NAME_BYTES)
            if (size < 0 || length <= 0) {
                continue
            }
            val name = read(namePointer, length).decodeToString()
            val dataPointer = call("slipgate_alloc", size)
            if (call("slipgate_save_read", index, dataPointer, size) == size) {
                saved[name] = read(dataPointer, size)
            }
            call("slipgate_free", dataPointer)
        }
        call("slipgate_free", namePointer)
        return saved
    }

    override fun putSavedFile(
        name: String,
        bytes: ByteArray,
    ): Boolean {
        val namePointer = writeString(name)
        val dataPointer = call("slipgate_alloc", bytes.size)
        sgWrite(context, dataPointer, bytes.toInt8Array())
        val stored = call("slipgate_save_put", namePointer, dataPointer, bytes.size) == 1
        call("slipgate_free", dataPointer)
        call("slipgate_free", namePointer)
        return stored
    }

    fun mount(
        name: String,
        bytes: ByteArray,
    ) {
        val namePointer = writeString(name)
        val dataPointer = call("slipgate_alloc", bytes.size)
        check(dataPointer != 0) { "the engine could not allocate ${bytes.size} bytes for $name" }
        sgWrite(context, dataPointer, bytes.toInt8Array())
        check(call("slipgate_mount", namePointer, dataPointer, bytes.size) == 1) {
            "the engine refused to mount $name"
        }
    }

    fun pushArgument(argument: String) {
        check(call("slipgate_arg_push", writeString(argument)) == 1) {
            "the engine refused the argument $argument"
        }
    }

    fun boot() {
        call("slipgate_init")
        drainLog()
    }

    private fun audioBuffer(frames: Int): Int {
        if (frames > audioCapacityFrames) {
            if (audioPointer != 0) {
                call("slipgate_free", audioPointer)
            }
            audioPointer = call("slipgate_alloc", frames * AUDIO_FRAME_BYTES)
            check(audioPointer != 0) { "the engine could not allocate an audio buffer" }
            audioCapacityFrames = frames
        }
        return audioPointer
    }

    /** What the engine said while it was running, in the order it said it. */
    private fun drainLog() {
        val lines = sgTakeLog(context)
        for (index in 0 until lines.length) {
            val line = lines[index]?.toString() ?: continue
            if (line.startsWith(FATAL_PREFIX)) {
                host.fatal(line.removePrefix(FATAL_PREFIX))
            } else {
                host.log(line)
            }
        }
    }

    private fun read(
        pointer: Int,
        size: Int,
    ): ByteArray = sgRead(context, pointer, size).toByteArray()

    private fun writeString(value: String): Int {
        val bytes = value.encodeToByteArray() + 0
        val pointer = call("slipgate_alloc", bytes.size)
        check(pointer != 0) { "the engine could not allocate ${bytes.size} bytes" }
        sgWrite(context, pointer, bytes.toInt8Array())
        return pointer
    }

    private fun call(
        name: String,
        first: Int = 0,
        second: Int = 0,
        third: Int = 0,
    ): Int = sgCall(context, name, first, second, third)

    private companion object {
        /** As long a name as the module will report; see MAX_PATH_BYTES in platform/sg_saves.c. */
        const val MAX_SAVE_NAME_BYTES = 128
        const val FATAL_PREFIX = "fatal:"
    }
}

/** The browser's own engine runs the module; there is nothing else on the web that can. */
public actual suspend fun startEngine(
    moduleBytes: ByteArray,
    files: Map<String, ByteArray>,
    arguments: List<String>,
    host: WasmHost,
    saves: Map<String, ByteArray>,
): EngineInstance {
    val context = sgInstantiate(moduleBytes.toInt8Array()).await()
    val engine = BrowserEngine(context, host)
    files.forEach { (name, bytes) -> engine.mount(name, bytes) }
    // Before start-up, because start-up is when the engine reads what it was left.
    saves.forEach { (name, bytes) -> engine.putSavedFile(name, bytes) }
    arguments.forEach { argument -> engine.pushArgument(argument) }
    engine.boot()
    return engine
}

// Instantiates the module with the imports its C library asks for. The answers are the same ones the
// Chasm bridge gives, for the same reasons: a call that claims success without doing the work makes
// the library retry forever, and a file the module cannot open must fail rather than hang.
// One function rather than two, and long because of it: a js() body is JavaScript, and a second one
// could not be called from inside this one — Kotlin compiles each to a name of its own choosing.
@Suppress("LongMethod")
private fun sgInstantiate(bytes: Int8Array): Promise<JsAny> =
    js(
        """(async () => {
             const context = { log: [] };
             const text = (pointer) => {
               const memory = new Uint8Array(context.memory.buffer);
               let end = pointer;
               while (memory[end] !== 0) { end++; }
               return new TextDecoder().decode(memory.subarray(pointer, end));
             };
             const writeVectors = (vectors, count, resultPointer) => {
               const view = new DataView(context.memory.buffer);
               const memory = new Uint8Array(context.memory.buffer);
               let total = 0;
               for (let index = 0; index < count; index++) {
                 const pointer = view.getUint32(vectors + index * 8, true);
                 const length = view.getUint32(vectors + index * 8 + 4, true);
                 if (length > 0) {
                   context.log.push(new TextDecoder().decode(memory.subarray(pointer, pointer + length)));
                 }
                 total += length;
               }
               view.setUint32(resultPointer, total, true);
               return 0;
             };
             const notSupported = () => 52;
             const imports = {
               slipgate: {
                 log: (pointer) => { context.log.push(text(pointer)); },
                 fatal: (pointer) => { context.log.push('fatal:' + text(pointer)); },
               },
               env: {
                 emscripten_notify_memory_growth: () => {},
                 __syscall_getdents64: notSupported,
                 __syscall_unlinkat: notSupported,
                 __syscall_rmdir: notSupported,
                 __syscall_renameat: notSupported,
               },
               wasi_snapshot_preview1: {
                 proc_exit: () => { context.log.push('fatal:the engine called exit'); },
                 clock_time_get: (id, precision, resultPointer) => {
                   new DataView(context.memory.buffer).setBigUint64(resultPointer, 0n, true);
                   return 0;
                 },
                 fd_close: () => 0,
                 fd_write: (fd, vectors, count, resultPointer) => writeVectors(vectors, count, resultPointer),
                 fd_read: (fd, vectors, count, resultPointer) => {
                   new DataView(context.memory.buffer).setUint32(resultPointer, 0, true);
                   return 0;
                 },
                 environ_sizes_get: (countPointer, sizePointer) => {
                   const view = new DataView(context.memory.buffer);
                   view.setUint32(countPointer, 0, true);
                   view.setUint32(sizePointer, 0, true);
                   return 0;
                 },
                 environ_get: () => 0,
                 fd_seek: notSupported,
               },
             };
             const result = await WebAssembly.instantiate(bytes.buffer, imports);
             context.exports = result.instance.exports;
             context.memory = context.exports.memory;
             return context;
           })()""",
    )

private fun sgCall(
    context: JsAny,
    name: String,
    first: Int,
    second: Int,
    third: Int,
): Int =
    js(
        """(() => {
             const exported = context.exports[name];
             const value = exported(first, second, third);
             return typeof value === 'number' ? value : 0;
           })()""",
    )

private fun sgRead(
    context: JsAny,
    pointer: Int,
    size: Int,
): Int8Array = js("new Int8Array(context.memory.buffer.slice(pointer, pointer + size))")

private fun sgWrite(
    context: JsAny,
    pointer: Int,
    bytes: Int8Array,
) {
    js("new Uint8Array(context.memory.buffer).set(new Uint8Array(bytes.buffer), pointer)")
}

private fun sgTakeLog(context: JsAny): JsArray<JsString> =
    js("(() => { const lines = context.log; context.log = []; return lines; })()")
