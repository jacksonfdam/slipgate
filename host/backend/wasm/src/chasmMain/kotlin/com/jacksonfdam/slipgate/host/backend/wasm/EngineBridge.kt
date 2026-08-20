package com.jacksonfdam.slipgate.host.backend.wasm

import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.shapes.HostFunction
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.ExecutionValue
import io.github.charlietap.chasm.runtime.value.NumberValue
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType

private val I32 = ValueType.Number(NumberType.I32)
private val I64 = ValueType.Number(NumberType.I64)
private val NOTHING = ResultType(emptyList())

private const val WASI_SUCCESS = 0
private const val WASI_NOT_SUPPORTED = 52
private const val IOVEC_BYTES = 8
private const val BYTE_MASK = 0xFF
private const val MESSAGE_LIMIT = 512

// Argument counts come straight from the WASI signatures, and reading them as names beats reading
// them as numbers.
private const val FIRST = 0
private const val SECOND = 1
private const val THIRD = 2
private const val FOURTH = 3

private const val ONE_ARGUMENT = 1
private const val TWO_ARGUMENTS = 2
private const val THREE_ARGUMENTS = 3
private const val FOUR_ARGUMENTS = 4

private fun i32s(count: Int) = ResultType(List(count) { I32 })

/**
 * The host side of an engine module: two functions Slipgate defines, and the handful of WASI calls
 * the C library reaches for.
 *
 * The WASI answers are deliberately concrete rather than zero-filled stubs. A call that reports
 * success without doing the work is worse than one that fails: the C library will retry a write
 * that claims to have written nothing, forever, and an engine that hangs on its own start-up
 * message gives no clue why.
 */
internal class EngineBridge(
    private val store: Store,
    private val host: WasmHost,
) {
    /** Set once the instance exists; the imports are built before it does. */
    internal var memory: Memory? = null

    internal fun imports(): List<Import> {
        val failure = i32s(ONE_ARGUMENT)

        return listOf(
            define("slipgate", "fatal", i32s(ONE_ARGUMENT), NOTHING) { arguments ->
                host.fatal(textAt(pointer(arguments, FIRST)))
                emptyList()
            },
            define("slipgate", "log", i32s(ONE_ARGUMENT), NOTHING) { arguments ->
                host.log(textAt(pointer(arguments, FIRST)))
                emptyList()
            },
            define("env", "emscripten_notify_memory_growth", i32s(ONE_ARGUMENT), NOTHING),
            define("wasi_snapshot_preview1", "proc_exit", i32s(ONE_ARGUMENT), NOTHING) {
                host.fatal("the engine called exit")
                emptyList()
            },
            // Time comes from the host's own stepping, not from a clock the engine reads.
            define(
                "wasi_snapshot_preview1",
                "clock_time_get",
                ResultType(listOf(I32, I64, I32)),
                failure,
            ) { arguments ->
                writeLong(pointer(arguments, THIRD), 0L)
                success()
            },
            define("wasi_snapshot_preview1", "fd_close", i32s(ONE_ARGUMENT), failure) { success() },
            define("wasi_snapshot_preview1", "fd_write", i32s(FOUR_ARGUMENTS), failure) { arguments ->
                val written = writeVectors(pointer(arguments, SECOND), pointer(arguments, THIRD))
                writeInt(pointer(arguments, FOURTH), written)
                success()
            },
            // Nothing is read through a file descriptor: game data is mounted into memory. Reporting
            // end of file is what stops the library retrying.
            define("wasi_snapshot_preview1", "fd_read", i32s(FOUR_ARGUMENTS), failure) { arguments ->
                writeInt(pointer(arguments, FOURTH), 0)
                success()
            },
            define("wasi_snapshot_preview1", "environ_sizes_get", i32s(TWO_ARGUMENTS), failure) { arguments ->
                writeInt(pointer(arguments, FIRST), 0)
                writeInt(pointer(arguments, SECOND), 0)
                success()
            },
            define("wasi_snapshot_preview1", "environ_get", i32s(TWO_ARGUMENTS), failure) { success() },
            define(
                "wasi_snapshot_preview1",
                "fd_seek",
                ResultType(listOf(I32, I64, I32, I32)),
                failure,
            ) { notSupported() },
            define("env", "__syscall_getdents64", i32s(THREE_ARGUMENTS), failure) { notSupported() },
            define("env", "__syscall_unlinkat", i32s(THREE_ARGUMENTS), failure) { notSupported() },
            define("env", "__syscall_rmdir", i32s(ONE_ARGUMENT), failure) { notSupported() },
            define("env", "__syscall_renameat", i32s(FOUR_ARGUMENTS), failure) { notSupported() },
        )
    }

    private fun define(
        module: String,
        name: String,
        parameters: ResultType,
        results: ResultType,
        body: HostFunction = { results.types.map { NumberValue.I32(0) } },
    ): Import = Import(module, name, function(store, FunctionType(parameters, results), body))

    private fun success(): List<ExecutionValue> = listOf(NumberValue.I32(WASI_SUCCESS))

    private fun notSupported(): List<ExecutionValue> = listOf(NumberValue.I32(WASI_NOT_SUPPORTED))

    private fun pointer(
        arguments: List<ExecutionValue>,
        index: Int,
    ): Int = (arguments.getOrNull(index) as? NumberValue.I32)?.value ?: 0

    /** Writes each vector's text to the host log and returns how many bytes it accounted for. */
    private fun writeVectors(
        vectorsPointer: Int,
        vectorCount: Int,
    ): Int {
        val currentMemory = memory ?: return 0
        val vectors = ByteArray(vectorCount * IOVEC_BYTES)
        readBytes(store, currentMemory, vectors, vectorsPointer, vectors.size)
        var total = 0
        for (index in 0 until vectorCount) {
            val offset = index * IOVEC_BYTES
            val textPointer = readInt(vectors, offset)
            val length = readInt(vectors, offset + Int.SIZE_BYTES)
            if (length <= 0) {
                continue
            }
            val text = ByteArray(length)
            readBytes(store, currentMemory, text, textPointer, length)
            host.log(text.decodeToString().trimEnd('\n'))
            total += length
        }
        return total
    }

    private fun textAt(pointer: Int): String {
        val currentMemory = memory ?: return ""
        val buffer = ByteArray(MESSAGE_LIMIT)
        readBytes(store, currentMemory, buffer, pointer, buffer.size)
        val end = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
        return buffer.decodeToString(0, end)
    }

    private fun writeInt(
        pointer: Int,
        value: Int,
    ) {
        val currentMemory = memory ?: return
        val bytes =
            ByteArray(Int.SIZE_BYTES) { index -> ((value shr (index * Byte.SIZE_BITS)) and BYTE_MASK).toByte() }
        writeBytes(store, currentMemory, pointer, bytes)
    }

    private fun writeLong(
        pointer: Int,
        value: Long,
    ) {
        val currentMemory = memory ?: return
        val bytes =
            ByteArray(Long.SIZE_BYTES) { index ->
                ((value shr (index * Byte.SIZE_BITS)) and BYTE_MASK.toLong()).toByte()
            }
        writeBytes(store, currentMemory, pointer, bytes)
    }

    private fun readInt(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        var value = 0
        for (index in 0 until Int.SIZE_BYTES) {
            value = value or ((bytes[offset + index].toInt() and BYTE_MASK) shl (index * Byte.SIZE_BITS))
        }
        return value
    }
}
