package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.SessionStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TIC_MILLIS = 29L
private const val FRAMES = 200
private const val HEADER_BYTES = 12
private const val DIRECTORY_ENTRY_BYTES = 16
private const val LUMP_NAME_OFFSET = 8

/**
 * Which name the module is handed the IWAD under, and whether Doom II boots at all.
 *
 * The name is the engine's only way of telling one Doom from another, so getting it wrong is not a
 * cosmetic bug: it is the difference between Doom II running and Doom II dying in start-up. The
 * first two tests pin the decision with files small enough to write here; the third boots the real
 * thing, because a synthetic wad cannot prove a game runs.
 *
 * ```
 * ./gradlew :games:mars:jvmTest --tests '*MountNameTest*' -Pslipgate.iwad2=/path/to/doom2.wad
 * ```
 */
class MountNameTest {
    private val mapped: File? =
        System
            .getenv("SLIPGATE_IWAD_MAPPED")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    @Test
    fun anEpisodicIwadKeepsDoomsOwnName() {
        assertEquals(MARS_IWAD, mountName(syntheticWad(listOf("PLAYPAL", "E1M1", "E2M1"))))
    }

    @Test
    fun aMappedIwadIsHandedOverAsDoomTwo() {
        assertEquals("doom2.wad", mountName(syntheticWad(listOf("PLAYPAL", "MAP01", "MAP02"))))
    }

    @Test
    fun anUnreadableFileFallsBackToTheGatesOwnKey() {
        // Not a wad at all. The gate is not the place to refuse it — the launcher already inspected
        // what it stored — so the old behaviour stands and the engine says what is wrong with it.
        assertEquals(MARS_IWAD, mountName(ByteArray(HEADER_BYTES)))
    }

    @Test
    fun doomTwoBootsAndDraws() =
        runTest {
            val file =
                mapped ?: return@runTest run {
                    println("skipping: set -Pslipgate.iwad2 to a Doom II IWAD to run this")
                }
            val host = RecordingHost()
            val session = openWasmSession(SingleFileData(MARS_IWAD, file.readBytes()), host)

            var status = SessionStatus.Running
            repeat(FRAMES) { status = session.step(InputFrame.Idle, TIC_MILLIS).status }

            assertEquals(SessionStatus.Running, status, host.tail())
            assertTrue(session.framebuffer().toSet().size > 1, "the frame is one flat colour: ${host.tail()}")
        }

    /** A wad holding nothing but named lumps, which is all the inspector reads. */
    private fun syntheticWad(lumps: List<String>): ByteArray {
        val directoryOffset = HEADER_BYTES + lumps.size
        val bytes = ByteArray(directoryOffset + lumps.size * DIRECTORY_ENTRY_BYTES)
        "IWAD".encodeToByteArray().copyInto(bytes)
        writeInt(bytes, offset = 4, value = lumps.size)
        writeInt(bytes, offset = 8, value = directoryOffset)
        lumps.forEachIndexed { index, name ->
            bytes[HEADER_BYTES + index] = 1
            val entry = directoryOffset + index * DIRECTORY_ENTRY_BYTES
            writeInt(bytes, entry, value = HEADER_BYTES + index)
            writeInt(bytes, entry + 4, value = 1)
            name.encodeToByteArray().copyInto(bytes, destinationOffset = entry + LUMP_NAME_OFFSET)
        }
        return bytes
    }

    private fun writeInt(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        for (index in 0 until Int.SIZE_BYTES) {
            bytes[offset + index] = (value shr (index * Byte.SIZE_BITS) and 0xFF).toByte()
        }
    }
}
