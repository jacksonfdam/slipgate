package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.ActionSet
import com.jacksonfdam.slipgate.host.runtime.AudioSink
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import com.jacksonfdam.slipgate.host.runtime.SaveStorage
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives the gate the way the launcher will: resolve a factory for the backend, hand it mounted
 * data, and step the session it returns.
 *
 * The parts that need no game data — the descriptor, the requirements, the input profile — are
 * checked always. Actually booting Doom needs an IWAD, which never lives in this repository, so
 * those tests skip unless one is supplied:
 *
 * ```
 * ./gradlew :games:mars:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class MarsGateTest {
    private val gate = MarsGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun theGateDescribesItself() {
        val descriptor = gate.descriptor

        assertEquals("mars", descriptor.id.value)
        assertEquals("Doom", descriptor.engine)
    }

    @Test
    fun theGateNeedsAnIwadAndOffersAFreeOne() {
        val entries = gate.requirements().entries

        assertEquals(1, entries.size)
        val sources = entries.single().sources
        assertTrue(sources.any { it is DataSource.FreeDownload }, "no free replacement is offered")
        assertTrue(sources.any { it == DataSource.UserSupplied }, "a user IWAD is not accepted")
    }

    @Test
    fun theGateRunsOnTheWasmBackend() {
        assertNotNull(gate.sessionFactories()[BackendId.Wasm], "no wasm factory")
    }

    @Test
    fun theInputProfileAsksForWhatDoomActuallyUses() {
        val profile = gate.inputProfile()

        assertTrue(GateAction.Fire in profile.actions)
        assertTrue(GateAction.Use in profile.actions)
        assertTrue(GateAction.Jump !in profile.actions, "Doom has no jump")
        assertTrue(profile.extensions.isEmpty(), "Doom needs no engine-specific controls")
    }

    @Test
    fun theSessionReportsDoomsDisplayFormat() =
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

    @Test
    fun aClosedSessionStops() =
        runTest {
            val session = openSession() ?: return@runTest

            session.close()

            assertEquals(SessionStatus.Finished, session.step(InputFrame.Idle, 29).status)
        }

    /**
     * Opening the menu makes Doom play a sound, which is the shortest path from an input to an
     * audible sample. Asserting on a non-zero sample rather than on a frame count is what separates
     * a mixer that works from a drain that hands out silence.
     */
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
                factory.create(SingleFileData(file.name, file.readBytes()), host)
            }
            ?: null.also { println("skipping: set -Pslipgate.iwad to run the boot tests") }
}
