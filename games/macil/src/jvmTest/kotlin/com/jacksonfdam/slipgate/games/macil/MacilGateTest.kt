package com.jacksonfdam.slipgate.games.macil

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
 * The Strife gate against the contract, the same way the other three are checked.
 *
 * What the gate declares is checked always. Actually booting Strife needs an IWAD, which never lives
 * in this repository and which has no freely licensed replacement at all, so those tests skip unless
 * one is supplied:
 *
 * ```
 * ./gradlew :games:macil:jvmTest -Pslipgate.iwad=/path/to/strife1.wad
 * ```
 */
private const val TIC_MILLIS = 29L
private const val TICS_PER_SECOND = 35

/** Long enough for the title screen to reach its demo, which is where a boot-time bug hides. */
private const val SECONDS_OF_ATTRACT = 30

class MacilGateTest {
    private val gate = MacilGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun theGateDescribesItself() {
        val descriptor = gate.descriptor

        assertEquals("macil", descriptor.id.value)
        assertEquals("Strife", descriptor.engine)
    }

    /** There is no Freedoom for Strife, so a download button here could only ever lie. */
    @Test
    fun theGateOffersNothingToDownload() {
        val sources = gate.requirements().entries.flatMap { entry -> entry.sources }

        assertTrue(sources.none { it is DataSource.FreeDownload }, "a free replacement was offered")
        assertTrue(sources.all { it == DataSource.UserSupplied })
    }

    @Test
    fun theIwadIsRequiredAndTheVoicesAreNot() {
        val entries = gate.requirements().entries.associateBy { entry -> entry.key }

        assertEquals(false, assertNotNull(entries[MACIL_IWAD]).optional, "the IWAD must be required")
        assertEquals(true, assertNotNull(entries[MACIL_VOICES]).optional, "the voices must be optional")
    }

    @Test
    fun theGateRunsOnTheWasmBackend() {
        assertNotNull(gate.sessionFactories()[BackendId.Wasm], "no wasm factory")
    }

    @Test
    fun theInputProfileAsksForWhatStrifeActuallyUses() {
        val profile = gate.inputProfile()

        assertTrue(GateAction.Fire in profile.actions)
        assertEquals(11, profile.extensions.size, "Strife has eleven controls Doom does not")
        assertTrue(profile.usesLookAxis, "Strife shipped with free look")
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

        assertEquals(declared, STRIFE_EXTENSION_KEYS.keys)
        assertEquals(gate.inputProfile().actions, STRIFE_KEYS.keys)
    }

    /**
     * Strife overrides the inventory keys the Raven games use, so this is not a formality: taking
     * Heretic's bracket pair would compile and then bind two keys Strife does nothing with.
     */
    @Test
    fun theInventoryKeysAreStrifesRatherThanRavens() {
        assertEquals(0x80 + 0x52, STRIFE_EXTENSION_KEYS[MACIL_INVENTORY_PREVIOUS], "KEY_INS expected")
        assertEquals(0x80 + 0x53, STRIFE_EXTENSION_KEYS[MACIL_INVENTORY_NEXT], "KEY_DEL expected")
    }

    @Test
    fun theSessionReportsStrifesDisplayFormat() =
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

            val results = (1..8).map { session.step(InputFrame.Idle, elapsedMillis = TIC_MILLIS) }

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

            val useItem = InputFrame(extensions = mapOf(MACIL_INVENTORY_USE to 1f))
            session.step(useItem, elapsedMillis = TIC_MILLIS)
            val results = (1..8).map { session.step(InputFrame.Idle, elapsedMillis = TIC_MILLIS) }

            assertTrue(results.all { it.status == SessionStatus.Running }, "the session stopped: ${host.tail()}")
        }

    @Test
    fun aClosedSessionStops() =
        runTest {
            val session = openSession() ?: return@runTest

            session.close()

            assertEquals(SessionStatus.Finished, session.step(InputFrame.Idle, TIC_MILLIS).status)
        }

    @Test
    fun openingTheMenuIsAudible() =
        runTest {
            val host = RecordingHost()
            val session = openSession(host) ?: return@runTest

            val menu = InputFrame(actions = ActionSet.of(GateAction.Menu))
            session.step(menu, elapsedMillis = TIC_MILLIS)
            repeat(16) { session.step(InputFrame.Idle, elapsedMillis = TIC_MILLIS) }

            assertTrue(host.audio.frames > 0, "no audio was drained at all")
            assertTrue(host.audio.heardSomething, "every drained frame was silent")
        }

    /**
     * The gate has to survive its own attract loop, the way the Heretic gate had to.
     *
     * Every boot-time check passes in the first second; what killed the Heretic gate was a sound
     * seven seconds in. Strife has not been run long enough to know what its equivalent is, which is
     * exactly why this steps for thirty seconds rather than eight.
     */
    @Test
    fun theGateSurvivesItsAttractLoop() =
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
                factory.create(SingleFileData(MACIL_IWAD, file.readBytes()), host)
            }
            ?: null.also { println("skipping: set -Pslipgate.iwad to a strife IWAD to run the boot tests") }
}
