package com.jacksonfdam.slipgate.games.korax

import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TIC_MILLIS = 29L

/** Long enough for the interpreter to have compiled nothing and cached everything it will. */
private const val WARMUP_FRAMES = 200

/** A little under a minute of game time, which is long enough for one slow frame not to be the median. */
private const val MEASURED_FRAMES = 1_500

private const val MICROS_PER_MILLI = 1_000.0
private const val NANOS_PER_MICRO = 1_000

/**
 * What one frame of Hexen costs on this host, which is the frame budget the README documents.
 *
 * Reported rather than asserted tightly: the number depends on the machine, and the point of
 * measuring is to know it. The one assertion is the claim that matters — a frame fits inside the
 * 28.6 ms a 35 Hz tic gets, because a gate that cannot hold its own tic rate is not playable.
 *
 * Needs game data, which never lives in this repository:
 *
 * ```
 * ./gradlew :games:korax:jvmTest --tests '*FrameBudgetTest*' -Pslipgate.iwad=/path/to/hexen.wad
 * ```
 */
class FrameBudgetTest {
    private val gate = KoraxGate()
    private val iwad: File? = System.getenv("SLIPGATE_IWAD")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun aFrameFitsInsideATic() =
        runTest {
            val file =
                iwad?.takeIf { it.isFile } ?: return@runTest run {
                    println("skipping: set -Pslipgate.iwad to a hexen IWAD to measure the frame budget")
                }
            val factory = assertNotNull(gate.sessionFactories()[BackendId.Wasm])
            val session = factory.create(SingleFileData(KORAX_IWAD, file.readBytes()), RecordingHost())

            repeat(WARMUP_FRAMES) { session.step(InputFrame.Idle, TIC_MILLIS) }

            val micros = IntArray(MEASURED_FRAMES)
            for (frame in 0 until MEASURED_FRAMES) {
                val started = System.nanoTime()
                val result = session.step(InputFrame.Idle, TIC_MILLIS)
                micros[frame] = ((System.nanoTime() - started) / NANOS_PER_MICRO).toInt()
                if (result.status != SessionStatus.Running) {
                    error("the session stopped after $frame measured frames")
                }
            }
            micros.sort()

            val median = micros[micros.size / 2] / MICROS_PER_MILLI
            val ninetyNinth = micros[micros.size * 99 / 100] / MICROS_PER_MILLI
            val worst = micros.last() / MICROS_PER_MILLI
            println(
                "Hexen frame budget: median $median ms, 99th $ninetyNinth ms, worst $worst ms",
            )

            assertTrue(median < TIC_MILLIS, "the median frame took $median ms, which does not fit a $TIC_MILLIS ms tic")
        }
}
