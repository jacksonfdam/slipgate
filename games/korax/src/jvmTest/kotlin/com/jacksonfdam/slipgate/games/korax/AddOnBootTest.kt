package com.jacksonfdam.slipgate.games.korax

import com.jacksonfdam.slipgate.host.runtime.ADD_ON_PREFIX
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TIC_MILLIS = 29L
private const val FRAMES = 200

/**
 * Hexen with Deathkings loaded over it, which is the case add-on support exists for.
 *
 * Deathkings ships as an expansion with an `IWAD` signature and no palette of its own, so the shelf
 * holds it as an add-on and the gate passes it to the engine with `-file`. Booting it is the only way
 * to know the two halves agree: a wrong load order or a missing argument gives a gate that starts and
 * then cannot find a map.
 *
 * ```
 * ./gradlew :games:korax:jvmTest --tests '*AddOnBootTest*' \
 *   -Pslipgate.iwad=/path/to/hexen.wad -Pslipgate.addon=/path/to/hexdd.wad
 * ```
 */
class AddOnBootTest {
    private val gate = KoraxGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)
    private val addOn: File? = System.getenv("SLIPGATE_ADDON")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun deathkingsLoadsOverHexen() =
        runTest {
            val game = iwad?.takeIf { it.isFile } ?: return@runTest skip()
            val expansion = addOn?.takeIf { it.isFile } ?: return@runTest skip()

            val host = RecordingHost()
            val factory = assertNotNull(gate.sessionFactories()[BackendId.Wasm])
            val data =
                MountedFiles(
                    mapOf(
                        KORAX_IWAD to game.readBytes(),
                        "$ADD_ON_PREFIX${expansion.name}" to expansion.readBytes(),
                    ),
                )
            val session = factory.create(data, host)

            var status = SessionStatus.Running
            repeat(FRAMES) { status = session.step(InputFrame.Idle, TIC_MILLIS).status }

            assertEquals(SessionStatus.Running, status, host.tail())
            assertTrue(session.framebuffer().toSet().size > 1, "the frame is one flat colour: ${host.tail()}")
            // The engine says which wads it opened, and the add-on has to be one of them: a gate that
            // quietly dropped it would pass every check above.
            assertTrue(
                host.lines.any { line -> line.contains(expansion.name, ignoreCase = true) },
                "the engine never mentioned ${expansion.name}:\n${host.tail(30)}",
            )
        }

    private fun skip() {
        println("skipping: set -Pslipgate.iwad and -Pslipgate.addon to run the add-on boot test")
    }
}

/** Several files, the way a shelf holding a game and an add-on beside it is mounted. */
private class MountedFiles(
    private val files: Map<String, ByteArray>,
) : MountedGameData {
    override fun names(): Set<String> = files.keys

    override suspend fun read(name: String): ByteArray = files[name] ?: throw NoSuchElementException(name)

    override suspend fun size(name: String): Long = read(name).size.toLong()
}
