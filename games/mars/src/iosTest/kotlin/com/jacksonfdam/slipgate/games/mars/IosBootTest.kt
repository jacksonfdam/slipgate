package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.AudioSink
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.host.runtime.SaveStorage
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.getenv
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Frames enough for the engine to have loaded the wad, set its palette and drawn its title. */
private const val FRAMES = 200

private const val TIC_MILLIS = 29L

/**
 * The one test that says the iOS build actually runs a game rather than merely compiling.
 *
 * Everything under it is native: Kotlin/Native, the Chasm interpreter running the Doom module, and
 * the platform layer inside it. Nothing here draws — the frame is checked as data, because what a
 * shader does with it is [GoldenImageTest]'s question and this is about whether there is a picture
 * at all.
 *
 * Needs game data, which never lives in this repository:
 *
 * ```
 * ./gradlew :games:mars:iosSimulatorArm64Test -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class IosBootTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun theGateBootsAndDrawsOnIos() =
        runBlocking {
            val path = getenv("SLIPGATE_IWAD")?.toKString()?.takeIf { it.isNotBlank() }
            if (path == null) {
                println("skipping: set -Pslipgate.iwad to run the iOS boot test")
                return@runBlocking
            }
            val data = NSData.dataWithContentsOfFile(path) ?: error("no game data at $path")
            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }

            val host = RecordingHost()
            val factory = MarsGate().sessionFactories()[BackendId.Wasm] ?: error("no wasm factory")
            val session = factory.create(SingleFileData(MARS_IWAD, bytes), host)

            var status = SessionStatus.Running
            repeat(FRAMES) { status = session.step(InputFrame.Idle, TIC_MILLIS).status }

            assertEquals(SessionStatus.Running, status, host.lines.takeLast(8).joinToString("\n"))
            val frame = session.framebuffer()
            assertTrue(frame.toSet().size > 1, "the frame is a single colour: ${host.lines.takeLast(8)}")
        }
}

private class SingleFileData(
    private val name: String,
    private val bytes: ByteArray,
) : MountedGameData {
    override fun names(): Set<String> = setOf(name)

    override suspend fun read(name: String): ByteArray = bytes

    override suspend fun size(name: String): Long = bytes.size.toLong()
}

private class RecordingHost : GateHost {
    val lines: MutableList<String> = mutableListOf()

    override val audio: AudioSink =
        object : AudioSink {
            override val sampleRate: Int = 44_100
            override val channels: Int = 2

            override fun submit(
                samples: ShortArray,
                frameCount: Int,
            ): Int = frameCount
        }

    override val storage: SaveStorage =
        object : SaveStorage {
            override suspend fun slots(): List<String> = emptyList()

            override suspend fun files(slot: String): List<String> = emptyList()

            override suspend fun read(
                slot: String,
                name: String,
            ): ByteArray? = null

            override suspend fun write(
                slot: String,
                name: String,
                bytes: ByteArray,
            ) = Unit

            override suspend fun delete(
                slot: String,
                name: String?,
            ) = Unit
        }

    override val logger: Logger =
        object : Logger {
            override fun log(
                level: LogLevel,
                message: String,
                cause: Throwable?,
            ) {
                lines += "$level: $message"
            }
        }

    override val clock: Clock =
        object : Clock {
            override fun elapsedMillis(): Long = 0L
        }
}
