package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.backend.wasm.ENGINE_SAVE_SLOT
import com.jacksonfdam.slipgate.host.backend.wasm.WasmEngine
import com.jacksonfdam.slipgate.host.backend.wasm.WasmHost
import com.jacksonfdam.slipgate.host.backend.wasm.flattenSavePath
import com.jacksonfdam.slipgate.host.backend.wasm.keptSaves
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val KEY_DOWN = 1
private const val KEY_UP = 2
private const val ESCAPE = 27
private const val ENTER = 13
private const val ARROW_DOWN = 0xAF

/** A save of a real level runs to tens of kilobytes; anything smaller is not a save. */
private const val PLAUSIBLE_SAVE_BYTES = 10_000

/**
 * A game saved through the game's own menu, kept by the host, and handed back to the next session.
 *
 * This is the whole point of the save work and the only test that proves it: the module is built
 * standalone, so its C library has no filesystem, and every save the engines wrote before this went
 * nowhere. The engine now writes into a filesystem the platform layer keeps in the module's memory,
 * and the host copies that across the sandbox in both directions.
 *
 * The engine is driven directly rather than through the gate, because saving needs a started game and
 * the gate boots to the title screen. Needs an IWAD, like everything that boots:
 *
 * ```
 * ./gradlew :games:mars:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class SaveMirrorTest {
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun aGameSavedThroughTheMenuComesBackForTheNextSession() =
        runTest {
            val file =
                iwad?.takeIf { it.isFile }
                    ?: return@runTest run { println("skipping: set -Pslipgate.iwad to run the mirror test") }
            val host = RecordingHost()

            val first = start(file, host, saves = emptyMap())
            saveThroughTheMenu(first)
            val written = first.savedFiles()

            val save = written.entries.firstOrNull { (_, bytes) -> bytes.size > PLAUSIBLE_SAVE_BYTES }
            assertTrue(save != null, "the engine wrote no savegame; it wrote ${written.mapValues { it.value.size }}")
            assertTrue(save.key.endsWith(".dsg"), "${save.key} is not a Doom save")

            // The way out of a gate: what the engine wrote becomes the host's, under a name a store
            // keeps unchanged.
            written.forEach { (path, bytes) ->
                host.storage.write(ENGINE_SAVE_SLOT, flattenSavePath(path), bytes)
            }

            // The way back in: a new session is handed the same files, at the same paths.
            val second = start(file, host, saves = keptSaves(host))
            val restored = second.savedFiles()

            assertEquals(written.keys, restored.keys, "the paths changed on the way through storage")
            written.forEach { (path, bytes) ->
                assertContentEquals(bytes, restored[path], "$path came back different")
            }
        }

    private suspend fun start(
        file: File,
        host: GateHost,
        saves: Map<String, ByteArray>,
    ): WasmEngine =
        WasmEngine.start(
            moduleBytes = marsModuleBytes(),
            files = mapOf(file.name to file.readBytes()),
            // Straight into a level: the save menu refuses a game that has not started.
            arguments = listOf("slipgate", "-iwad", file.name, "-nomusic", "-warp", "1", "1"),
            host = LoggingHost(host),
            saves = saves,
        )

    /**
     * Presses what a player presses: Escape, down to Save Game, a slot, a name, Enter.
     *
     * Doom's menu is the only way in — there is no export that saves, and inventing one would test
     * the export rather than the game.
     */
    private fun saveThroughTheMenu(engine: WasmEngine) {
        repeat(60) { engine.step(TIC_MILLIS) }
        tap(engine, ESCAPE)
        repeat(3) { tap(engine, ARROW_DOWN) }
        tap(engine, ENTER, settle = 12)
        tap(engine, ARROW_DOWN)
        tap(engine, ENTER, settle = 12)
        tap(engine, 'X'.code, value = 'X'.code)
        tap(engine, ENTER, settle = 40)
    }

    private fun tap(
        engine: WasmEngine,
        code: Int,
        settle: Int = 8,
        value: Int = 0,
    ) {
        engine.pushEvent(KEY_DOWN, code, value)
        repeat(3) { engine.step(TIC_MILLIS) }
        engine.pushEvent(KEY_UP, code, value)
        repeat(settle) { engine.step(TIC_MILLIS) }
    }

    private companion object {
        const val TIC_MILLIS = 29
    }
}

/** Passes the engine's own words to whatever host is collecting them. */
private class LoggingHost(
    private val host: GateHost,
) : WasmHost {
    override fun fatal(message: String) = host.logger.log(LogLevel.Error, message)

    override fun log(message: String) = host.logger.log(LogLevel.Debug, message)
}
