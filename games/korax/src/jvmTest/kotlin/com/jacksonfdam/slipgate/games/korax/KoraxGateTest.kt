package com.jacksonfdam.slipgate.games.korax

import com.jacksonfdam.slipgate.host.runtime.ActionSet
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Hexen gate against the contract, the same way the other two are checked.
 *
 * Hexen has no freely licensed replacement, so unlike Doom and Heretic there is no way to run the boot
 * tests here without a player's own IWAD:
 *
 * ```
 * ./gradlew :games:korax:jvmTest -Pslipgate.iwad=/path/to/hexen.wad
 * ```
 *
 * Until somebody does that, this gate has never drawn a frame. The two bugs the Heretic port needed
 * were both invisible without data, and this is where the same class of bug would be found.
 */
class KoraxGateTest {
    private val gate = KoraxGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun theGateDescribesItself() {
        val descriptor = gate.descriptor

        assertEquals("korax", descriptor.id.value)
        assertEquals("Hexen", descriptor.engine)
    }

    @Test
    fun theGateAsksForTheUsersOwnIwadAndOffersNoDownload() {
        val entry = gate.requirements().entries.single()

        assertEquals(listOf(DataSource.UserSupplied), entry.sources)
    }

    @Test
    fun theGateRunsOnTheWasmBackend() {
        assertNotNull(gate.sessionFactories()[BackendId.Wasm], "no wasm factory")
    }

    @Test
    fun theInputProfileAsksForWhatHexenActuallyUses() {
        val profile = gate.inputProfile()

        assertTrue(GateAction.Jump in profile.actions, "Hexen is the one game here with a jump")
        assertEquals(5, profile.extensions.size, "the inventory and flight are what Doom does not have")
    }

    /** The invariant that keeps a drawn button from being a dead one, as the Heretic gate has it. */
    @Test
    fun everyDeclaredControlHasAKeyAndEveryKeyHasAControl() {
        val declared =
            gate
                .inputProfile()
                .extensions
                .map { extension -> extension.key }
                .toSet()

        assertEquals(declared, HEXEN_EXTENSION_KEYS.keys)
        assertEquals(gate.inputProfile().actions, HEXEN_KEYS.keys)
    }

    @Test
    fun theSessionReportsHexensDisplayFormat() =
        runTest {
            val session = openSession() ?: return@runTest

            assertEquals(320, session.display.width)
            assertEquals(200, session.display.height)
            assertEquals(PixelFormat.Indexed8, session.display.pixelFormat)
            assertEquals(ID_TECH_1_PIXEL_ASPECT, session.display.pixelAspect)
        }

    @Test
    fun theSessionDrawsAndCarriesAPalette() =
        runTest {
            val session = openSession() ?: return@runTest

            val results = (1..8).map { session.step(InputFrame.Idle, elapsedMillis = 29) }

            assertTrue(results.all { it.status == SessionStatus.Running }, "the session stopped: $results")
            assertTrue(results.any { it.frameRendered }, "no frame was ever rendered")
            assertTrue(session.framebuffer().any { it != 0.toByte() }, "the framebuffer is blank")

            val palette = assertNotNull(session.palette(), "an indexed session must have a palette")
            assertEquals(256, palette.size)
            assertTrue(palette.all { it ushr 24 == 0xFF }, "palette entries must be opaque")
            assertTrue(palette.distinct().size > 1, "the palette is a single colour")
        }

    /**
     * Hexen's frame is the engine's own loop, run one iteration per step through the frame boundary.
     * A loop that was left in the wrong place would show up as a session that stops, or as frames that
     * stop arriving, so stepping this many times is the test of the inversion itself.
     */
    @Test
    fun theEnginesOwnLoopKeepsGivingFrames() =
        runTest {
            val session = openSession() ?: return@runTest

            val results = (1..90).map { session.step(InputFrame.Idle, elapsedMillis = 29) }

            assertTrue(results.all { it.status == SessionStatus.Running }, "the session stopped early")
            assertTrue(results.count { it.frameRendered } > 40, "only ${results.count { it.frameRendered }} frames")
        }

    @Test
    fun anInventoryPressReachesTheEngine() =
        runTest {
            val host = RecordingHost()
            val session = openSession(host) ?: return@runTest

            val useItem = InputFrame(extensions = mapOf(KORAX_INVENTORY_USE to 1f))
            session.step(useItem, elapsedMillis = 29)
            val results = (1..8).map { session.step(InputFrame.Idle, elapsedMillis = 29) }

            assertTrue(results.all { it.status == SessionStatus.Running }, "the session stopped: ${host.tail()}")
        }

    @Test
    fun aClosedSessionStops() =
        runTest {
            val session = openSession() ?: return@runTest

            session.close()

            assertEquals(SessionStatus.Finished, session.step(InputFrame.Idle, 29).status)
        }

    @Test
    fun openingTheMenuIsAudible() =
        runTest {
            val host = RecordingHost()
            val session = openSession(host) ?: return@runTest

            val menu = InputFrame(actions = ActionSet.of(GateAction.Menu))
            session.step(menu, elapsedMillis = 29)
            repeat(16) { session.step(InputFrame.Idle, elapsedMillis = 29) }

            assertTrue(host.audio.frames > 0, "no audio was drained at all")
            assertTrue(host.audio.heardSomething, "every drained frame was silent")
        }

    private suspend fun openSession(host: RecordingHost = RecordingHost()) =
        iwad
            ?.takeIf { it.isFile }
            ?.let { file ->
                val factory = assertNotNull(gate.sessionFactories()[BackendId.Wasm])
                factory.create(SingleFileData(KORAX_IWAD, file.readBytes()), host)
            }
            ?: null.also { println("skipping: set -Pslipgate.iwad to a hexen IWAD to run the boot tests") }
}
