package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.AudioSink
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.host.runtime.SaveStorage

/** One file, mounted under the name the user's own file had. */
internal class SingleFileData(
    private val name: String,
    private val bytes: ByteArray,
) : MountedGameData {
    override fun names(): Set<String> = setOf(name)

    override suspend fun read(name: String): ByteArray =
        if (name == this.name) bytes else throw NoSuchElementException(name)

    override suspend fun size(name: String): Long =
        if (name == this.name) bytes.size.toLong() else throw NoSuchElementException(name)
}

/** Counts what the session played, and whether any of it was more than silence. */
internal class CountingAudioSink : AudioSink {
    override val sampleRate: Int = 44_100
    override val channels: Int = 2

    var frames: Int = 0
        private set

    var heardSomething: Boolean = false
        private set

    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        frames += frameCount
        if (!heardSomething) {
            heardSomething = (0 until frameCount * channels).any { samples[it] != 0.toShort() }
        }
        return frameCount
    }
}

internal class RecordingHost : GateHost {
    val lines: MutableList<String> = mutableListOf()

    /** The last few things the engine said, which is what explains an unexpected stop. */
    fun tail(count: Int = 12): String = lines.takeLast(count).joinToString("\n")

    override val audio: CountingAudioSink = CountingAudioSink()

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
