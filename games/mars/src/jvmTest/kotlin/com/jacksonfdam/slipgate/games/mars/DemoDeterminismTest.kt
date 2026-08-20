package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.ActionSet
import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TIC_MILLIS = 29

/** Every id Tech 1 IWAD carries this, and it is the longest of the four Freedoom ships. */
private const val DEMO = "DEMO1"
private const val TICS_PER_SECOND = 35

/**
 * The highest-value test in the project, and the reason the platform layer drives the clock from the
 * host: an engine of this age is a state machine that must produce the same frames from the same
 * inputs, and a port that is subtly wrong desyncs loudly rather than failing quietly.
 *
 * Two runs are compared against each other rather than against a recorded hash. A stored hash would
 * pin one IWAD's content, and the whole point is that the harness works with whichever legitimate
 * IWAD a player supplied — the engine's determinism is the invariant, not Freedoom's pixels.
 *
 * Playing back a demo needs game data, which never lives in this repository, so these skip unless
 * one is supplied:
 *
 * ```
 * ./gradlew :games:mars:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class DemoDeterminismTest {
    private val iwad: File? =
        System
            .getenv("SLIPGATE_IWAD")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    @Test
    fun theSameInputsProduceTheSameFrames() =
        runTest {
            val first = trace(tics = 4 * TICS_PER_SECOND) { tic -> scriptedInput(tic) } ?: return@runTest
            val second = trace(tics = 4 * TICS_PER_SECOND) { tic -> scriptedInput(tic) } ?: return@runTest

            assertEquals(second, first, "two runs of the same input diverged")
        }

    @Test
    fun aRecordedDemoPlaysBackTheSameWayTwice() =
        runTest {
            val first = trace(tics = 6 * TICS_PER_SECOND, demo = DEMO) ?: return@runTest
            val second = trace(tics = 6 * TICS_PER_SECOND, demo = DEMO) ?: return@runTest

            assertEquals(second, first, "demo playback diverged between two runs")
        }

    /**
     * A demo that desyncs stops being the recorded game: the engine reads the recorded tics,
     * disagrees about the world, and ends early. Reaching the demo's own end is what "in sync" means.
     *
     * `-playdemo` rather than `-timedemo`: the engine reports timing results by way of `I_Error`,
     * which is a fatal stop by design, while a single demo that finishes calls `I_Quit` — and the
     * platform layer turns that into a session that finished rather than one that failed.
     */
    @Test
    fun aRecordedDemoRunsToItsEnd() =
        runTest {
            val host = RecordingHost()
            val session = open(demo = DEMO, host = host) ?: return@runTest
            var tics = 0
            var status = SessionStatus.Running

            // Freedoom's first demo is a little over three minutes of game time; the bound allows
            // for the whole of it plus room for a longer demo in another IWAD.
            while (status == SessionStatus.Running && tics < 300 * TICS_PER_SECOND) {
                status =
                    runCatching { session.step(InputFrame.Idle, TIC_MILLIS.toLong()).status }
                        .getOrElse { failure ->
                            // What the engine said last is the only clue to why it stopped this way.
                            error("the engine failed at tic $tics: ${failure.message}\n${host.tail()}")
                        }
                tics++
            }

            assertEquals(SessionStatus.Finished, status, "the demo was still running after $tics tics")
            assertTrue(tics > TICS_PER_SECOND, "the demo ended after only $tics tics")
        }

    /** Hash of every frame and palette the session produced, which is a proxy for its whole state. */
    private suspend fun trace(
        tics: Int,
        demo: String? = null,
        input: (Int) -> InputFrame = { InputFrame.Idle },
    ): String? {
        val session = open(demo) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")

        for (tic in 0 until tics) {
            val result = session.step(input(tic), TIC_MILLIS.toLong())
            if (result.status != SessionStatus.Running) {
                break
            }
            digest.update(session.framebuffer())
            session.palette()?.forEach { colour -> digest.update(colour.toByte()) }
        }
        return digest.digest().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** Walks, turns and shoots on a fixed schedule, so the run exercises more than the title screen. */
    private fun scriptedInput(tic: Int): InputFrame =
        when {
            tic < TICS_PER_SECOND -> {
                InputFrame(actions = ActionSet.of(GateAction.Use))
            }

            tic < 2 * TICS_PER_SECOND -> {
                InputFrame(movement = Axis2(x = 0f, y = 1f))
            }

            tic < 3 * TICS_PER_SECOND -> {
                InputFrame(movement = Axis2(x = 1f, y = 0f), actions = ActionSet.of(GateAction.Fire))
            }

            else -> {
                InputFrame(movement = Axis2(x = -1f, y = -1f))
            }
        }

    private suspend fun open(
        demo: String? = null,
        host: RecordingHost = RecordingHost(),
    ): GateSession? {
        val file = iwad ?: return null.also { println("skipping: set -Pslipgate.iwad to run the harness") }
        val data = SingleFileData(MARS_IWAD, file.readBytes())
        return if (demo == null) {
            openWasmSession(data = data, host = host)
        } else {
            openWasmDemoSession(data = data, host = host, demo = demo)
        }
    }
}
