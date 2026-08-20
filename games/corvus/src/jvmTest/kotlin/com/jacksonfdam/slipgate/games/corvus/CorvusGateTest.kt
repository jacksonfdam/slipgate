package com.jacksonfdam.slipgate.games.corvus

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
 * The Heretic gate against the contract, the same way the Doom gate is checked.
 *
 * What the gate declares is checked always. Actually booting Heretic needs an IWAD, which never lives
 * in this repository and which — unlike Doom — has no freely licensed replacement to fall back on, so
 * those tests skip unless one is supplied:
 *
 * ```
 * ./gradlew :games:corvus:jvmTest -Pslipgate.iwad=/path/to/heretic.wad
 * ```
 */
private const val TIC_MILLIS = 29L
private const val TICS_PER_SECOND = 35

/** Comfortably past the seven seconds it took an imp to make a linked sound in the title demo. */
private const val SECONDS_OF_ATTRACT = 30

class CorvusGateTest {
    private val gate = CorvusGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun theGateDescribesItself() {
        val descriptor = gate.descriptor

        assertEquals("corvus", descriptor.id.value)
        assertEquals("Heretic", descriptor.engine)
    }

    @Test
    fun theGateNeedsAnIwadAndOffersAFreeOne() {
        val entry = gate.requirements().entries.single()

        assertTrue(entry.sources.any { it is DataSource.FreeDownload }, "no free replacement is offered")
        assertTrue(entry.sources.any { it == DataSource.UserSupplied }, "a user IWAD is not accepted")
    }

    /** The pin is the point: a release that moved is a download that broke. */
    @Test
    fun theFreeReplacementIsAPinnedRelease() {
        val download =
            gate
                .requirements()
                .entries
                .single()
                .sources
                .filterIsInstance<DataSource.FreeDownload>()
                .single()

        assertEquals("Blasphemer", download.displayName)
        assertTrue(download.url.contains("/download/v0.1.8/"), "the release is not pinned: ${download.url}")
        assertEquals("blasphem.wad", download.archiveEntry)
    }

    @Test
    fun theGateRunsOnTheWasmBackend() {
        assertNotNull(gate.sessionFactories()[BackendId.Wasm], "no wasm factory")
    }

    @Test
    fun theInputProfileAsksForWhatHereticActuallyUses() {
        val profile = gate.inputProfile()

        assertTrue(GateAction.Fire in profile.actions)
        assertTrue(GateAction.Jump !in profile.actions, "Heretic has no jump; Hexen does")
        assertEquals(5, profile.extensions.size, "the inventory and flight are what Doom does not have")
    }

    /**
     * The invariant that keeps a drawn button from being a dead one: the pad draws whatever the profile
     * declares, and only what the session has a key for reaches the engine. A control in one and not
     * the other is silent rather than broken, which is the kind of bug that ships.
     */
    @Test
    fun everyDeclaredControlHasAKeyAndEveryKeyHasAControl() {
        val declared =
            gate
                .inputProfile()
                .extensions
                .map { extension -> extension.key }
                .toSet()

        assertEquals(declared, HERETIC_EXTENSION_KEYS.keys)
        assertEquals(gate.inputProfile().actions, HERETIC_KEYS.keys)
    }

    @Test
    fun theSessionReportsHereticsDisplayFormat() =
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
    fun anInventoryPressReachesTheEngine() =
        runTest {
            val host = RecordingHost()
            val session = openSession(host) ?: return@runTest

            val useItem = InputFrame(extensions = mapOf(CORVUS_INVENTORY_USE to 1f))
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

    /**
     * The gate has to survive its own attract loop, which is where a linked sound comes from.
     *
     * Heretic's sound table names some entries "-impact" or "-mumact": the dash says the lump belongs
     * to the sound they link to, and asking the wad for the dashed name is fatal. Nothing plays one
     * until an imp is idling somewhere in the demo the title screen runs, which took about seven
     * seconds of stepping to reach — long after every boot test had passed.
     */
    @Test
    fun aLinkedSoundDoesNotKillTheGate() =
        runTest {
            val host = RecordingHost()
            val session = openSession(host) ?: return@runTest
            repeat(SECONDS_OF_ATTRACT * TICS_PER_SECOND) { tic ->
                val result =
                    runCatching { session.step(InputFrame.Idle, elapsedMillis = TIC_MILLIS) }
                        .getOrElse { failure -> error("the gate died at tic $tic: ${failure.message}\n${host.tail()}") }
                assertEquals(SessionStatus.Running, result.status, "the gate stopped at tic $tic")
            }
        }

    private suspend fun openSession(host: RecordingHost = RecordingHost()) =
        iwad
            ?.takeIf { it.isFile }
            ?.let { file ->
                val factory = assertNotNull(gate.sessionFactories()[BackendId.Wasm])
                factory.create(SingleFileData(CORVUS_IWAD, file.readBytes()), host)
            }
            ?: null.also { println("skipping: set -Pslipgate.iwad to a heretic IWAD to run the boot tests") }
}
