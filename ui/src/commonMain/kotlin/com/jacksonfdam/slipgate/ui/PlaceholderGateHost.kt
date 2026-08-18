package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.runtime.AudioSink
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.SaveStorage

/**
 * Host services that accept everything and store nothing, so a session can run before the
 * audio, storage and logging modules exist. Each field is replaced by a real implementation
 * in its own change; nothing here is meant to survive.
 */
internal class PlaceholderGateHost : GateHost {
    override val audio: AudioSink =
        object : AudioSink {
            override val sampleRate: Int = SAMPLE_RATE
            override val channels: Int = CHANNELS

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
            ) = Unit
        }

    override val clock: Clock =
        object : Clock {
            override fun elapsedMillis(): Long = 0L
        }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
    }
}
