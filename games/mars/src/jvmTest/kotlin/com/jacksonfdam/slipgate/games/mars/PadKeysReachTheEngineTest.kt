package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.backend.wasm.WasmGateSession
import com.jacksonfdam.slipgate.host.backend.wasm.WasmHost
import com.jacksonfdam.slipgate.host.backend.wasm.startEngine
import com.jacksonfdam.slipgate.host.runtime.ActionSet
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private const val TIC_MILLIS = 29
private const val TICS_PER_SECOND = 35

/** Long enough for the level to have finished loading and settled into play. */
private const val SETTLE_TICS = TICS_PER_SECOND

/** Long enough for a shot to leave the barrel and for the sprite to come back down. */
private const val PRESS_TICS = 2 * TICS_PER_SECOND

/**
 * Proof that a button on the pad reaches the engine as the key the engine is listening for.
 *
 * A wrong key code is invisible from every direction except this one: the host sends it, the engine
 * accepts it, nothing rejects anything, and the button is simply dead. Fire was dead this way for
 * the whole life of the gate — it sent `0xA3`, which is not a key. So the test presses a button in a
 * running game and demands the frames change, having first shown that two idle runs do not: the only
 * difference between the runs is the press, so a run that looks identical means the press did
 * nothing.
 *
 * Needs game data, which never lives in this repository:
 *
 * ```
 * ./gradlew :games:mars:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class PadKeysReachTheEngineTest {
    private val iwad: File? =
        System
            .getenv("SLIPGATE_IWAD")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    @Test
    fun twoIdenticalRunsAgreeSoADifferenceMeansThePress() =
        runTest {
            val first = trace(action = null) ?: return@runTest
            val second = trace(action = null) ?: return@runTest

            assertEquals(second, first, "two runs of the same idle input diverged")
        }

    @Test
    fun theFireButtonFires() =
        runTest {
            val idle = trace(action = null) ?: return@runTest
            val firing = trace(action = GateAction.Fire) ?: return@runTest

            assertNotEquals(idle, firing, "holding fire changed nothing on screen: the key is dead")
        }

    @Test
    fun theWeaponButtonsChangeWeapon() =
        runTest {
            val idle = trace(action = null) ?: return@runTest
            val next = trace(action = GateAction.NextWeapon) ?: return@runTest
            val previous = trace(action = GateAction.PreviousWeapon) ?: return@runTest

            assertNotEquals(idle, next, "the next weapon button changed nothing: the key is dead")
            assertNotEquals(idle, previous, "the previous weapon button changed nothing: the key is dead")
        }

    /**
     * Hash of every frame a run produced: idle while the level settles, then [action] held down.
     */
    private suspend fun trace(action: GateAction?): String? {
        val file = iwad ?: return null.also { println("skipping: set -Pslipgate.iwad to run the harness") }
        val host = RecordingHost()
        val engine =
            startEngine(
                moduleBytes = marsModuleBytes(),
                files = mapOf(MARS_IWAD to file.readBytes()),
                // A game already in progress, because a button means nothing on the title screen.
                arguments = listOf("slipgate", "-iwad", MARS_IWAD, "-nomusic", "-skill", "3", "-warp", "1", "1"),
                host = SilentHost(),
            )
        val session =
            WasmGateSession(
                engine = engine,
                host = host,
                keyBindings = DOOM_KEYS,
                directionBindings = DOOM_DIRECTIONS,
                pixelAspect = ID_TECH_1_PIXEL_ASPECT,
            )

        val held = action?.let { InputFrame(actions = ActionSet.of(it)) } ?: InputFrame.Idle
        val digest = MessageDigest.getInstance("SHA-256")
        for (tic in 0 until SETTLE_TICS + PRESS_TICS) {
            val frame = if (tic < SETTLE_TICS) InputFrame.Idle else held
            val result = session.step(frame, TIC_MILLIS.toLong())
            if (result.status != SessionStatus.Running) {
                error("the session stopped at tic $tic: ${host.tail()}")
            }
            if (tic >= SETTLE_TICS) {
                digest.update(session.framebuffer())
            }
        }
        return digest.digest().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}

/** The engine says nothing worth keeping here; the host under test is the one that records. */
private class SilentHost : WasmHost {
    override fun fatal(message: String) = Unit

    override fun log(message: String) = Unit
}
