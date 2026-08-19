package com.jacksonfdam.slipgate.games.mars

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the engine module the gate ships against the surface the host calls.
 *
 * A module that parses but exports the wrong names fails at the first frame with nothing to point
 * at, so the names are worth asserting here, where a rename in the platform layer shows up as a
 * test failure rather than a runtime one.
 */
class MarsModuleTest {
    @Test
    fun theModuleShipsWithTheGate() =
        runTest {
            val bytes = marsModuleBytes()

            assertTrue(bytes.size > 100_000, "the module is suspiciously small: ${bytes.size} bytes")
            assertTrue(
                bytes[0] == 0.toByte() && bytes[1] == 'a'.code.toByte(),
                "the resource is not a wasm module",
            )
        }

    @Test
    fun theModuleExportsWhatTheHostCalls() =
        runTest {
            val text = marsModuleBytes().decodeToString()

            listOf(
                "slipgate_alloc",
                "slipgate_free",
                "slipgate_arg_push",
                "slipgate_mount",
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
        }
}
