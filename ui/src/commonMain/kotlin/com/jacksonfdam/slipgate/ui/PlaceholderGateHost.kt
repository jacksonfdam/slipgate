package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.audio.AudioOutput
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.SaveStorage

/**
 * Host services that store nothing, so a session can run before the storage and logging modules
 * exist. Audio is real and comes from outside: the remaining fields are replaced the same way, each
 * in its own change.
 */
internal class PlaceholderGateHost(
    // The platform's output rather than the bare sink from the contract: what the shell registers is
    // the thing that owns a device, and asking for the narrower type is what a container cannot see.
    override val audio: AudioOutput,
) : GateHost {
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
}
