package com.jacksonfdam.slipgate.host.backend.wasm

import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.HostFunction
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loads the Doom module Slipgate builds from Chocolate Doom and checks it against the contract the
 * host relies on: the exported surface is present, and the imports are only the ones the platform
 * layer declares plus WASI.
 *
 * Booting the engine needs game data, which the host mounts and which never lives in this
 * repository, so this test stops at the boundary that can be checked without it.
 */
class DoomModuleTest {
    private fun moduleBytes(): ByteArray {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/mars.wasm")) {
                "the Doom module is missing from test resources"
            }
        return stream.use { it.readBytes() }
    }

    @Test
    fun theModuleParses() {
        val bytes = moduleBytes()

        assertTrue(bytes.size > 100_000, "the module is suspiciously small: ${bytes.size} bytes")
        module(bytes).expect("the Doom module did not parse")
    }

    @Test
    fun theModuleInstantiatesWithTheHostImports() {
        val store = store()
        val chasmModule = module(moduleBytes()).expect("the Doom module did not parse")

        val instance =
            instance(store, chasmModule, hostImports(store))
                .expect("the Doom module did not instantiate")

        assertTrue(instance.toString().isNotEmpty())
    }

    /**
     * Every import the module declares, stubbed. The engine cannot run on these — reading a WAD
     * needs a real filesystem — but instantiating on them proves the module asks for nothing the
     * host cannot supply.
     */
    private fun hostImports(store: io.github.charlietap.chasm.embedding.shapes.Store): List<Import> {
        val noResult = ResultType(emptyList())

        fun i32s(count: Int) = ResultType(List(count) { ValueType.Number(NumberType.I32) })

        fun i64s(count: Int) = ResultType(List(count) { ValueType.Number(NumberType.I64) })

        fun stub(
            module: String,
            name: String,
            parameters: ResultType,
            results: ResultType,
        ): Import {
            val type = FunctionType(parameters, results)
            val host: HostFunction = { _, _ -> results.types.map { NumberValue.I32(0) } }
            return Import(module, name, function(store, type, host))
        }

        val returnsI32 = i32s(1)
        return listOf(
            stub("slipgate", "fatal", i32s(1), noResult),
            stub("slipgate", "log", i32s(1), noResult),
            stub("env", "emscripten_notify_memory_growth", i32s(1), noResult),
            stub("wasi_snapshot_preview1", "proc_exit", i32s(1), noResult),
            stub(
                "wasi_snapshot_preview1",
                "clock_time_get",
                ResultType(
                    listOf(
                        ValueType.Number(NumberType.I32),
                        ValueType.Number(NumberType.I64),
                        ValueType.Number(NumberType.I32),
                    ),
                ),
                returnsI32,
            ),
            stub("wasi_snapshot_preview1", "fd_close", i32s(1), returnsI32),
            stub("wasi_snapshot_preview1", "fd_write", i32s(4), returnsI32),
            stub("wasi_snapshot_preview1", "fd_read", i32s(4), returnsI32),
            stub("wasi_snapshot_preview1", "environ_sizes_get", i32s(2), returnsI32),
            stub("wasi_snapshot_preview1", "environ_get", i32s(2), returnsI32),
            stub(
                "wasi_snapshot_preview1",
                "fd_seek",
                ResultType(
                    listOf(
                        ValueType.Number(NumberType.I32),
                        ValueType.Number(NumberType.I64),
                        ValueType.Number(NumberType.I32),
                        ValueType.Number(NumberType.I32),
                    ),
                ),
                returnsI32,
            ),
            stub("env", "__syscall_getdents64", i32s(3), returnsI32),
            stub("env", "__syscall_unlinkat", i32s(3), returnsI32),
            stub("env", "__syscall_rmdir", i32s(1), returnsI32),
            stub("env", "__syscall_renameat", i32s(4), returnsI32),
        )
    }

    @Test
    fun theExportedSurfaceIsWhatTheHostExpects() {
        val bytes = moduleBytes()
        val text = String(bytes, Charsets.ISO_8859_1)

        listOf(
            "slipgate_init",
            "slipgate_step",
            "slipgate_framebuffer",
            "slipgate_framebuffer_size",
            "slipgate_palette",
            "slipgate_push_event",
            "slipgate_audio_drain",
            "slipgate_save_state",
        ).forEach { export ->
            assertTrue(text.contains(export), "the module does not export $export")
        }
        assertEquals(true, text.contains("memory"))
    }
}
