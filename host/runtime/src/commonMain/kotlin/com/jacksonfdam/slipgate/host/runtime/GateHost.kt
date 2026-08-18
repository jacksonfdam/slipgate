package com.jacksonfdam.slipgate.host.runtime

/** Where a session's rendered audio goes. */
public interface AudioSink {
    public val sampleRate: Int

    public val channels: Int

    /**
     * Hands [frameCount] interleaved frames to the platform. Returns how many were accepted;
     * a short accept means the sink is full and the session should retry next step.
     */
    public fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int
}

/**
 * Save files for one gate. Multi-slot and multi-file, because hub-based games write several
 * files per save and expect them to survive together.
 */
public interface SaveStorage {
    public suspend fun slots(): List<String>

    public suspend fun files(slot: String): List<String>

    public suspend fun read(
        slot: String,
        name: String,
    ): ByteArray?

    public suspend fun write(
        slot: String,
        name: String,
        bytes: ByteArray,
    )

    public suspend fun delete(
        slot: String,
        name: String? = null,
    )
}

public enum class LogLevel {
    Debug,
    Info,
    Warning,
    Error,
}

public interface Logger {
    public fun log(
        level: LogLevel,
        message: String,
        cause: Throwable? = null,
    )
}

/** Monotonic time source. Sessions must not read wall-clock time themselves. */
public interface Clock {
    public fun elapsedMillis(): Long
}

/** Services the host lends to a session for as long as it runs. */
public interface GateHost {
    public val audio: AudioSink
    public val storage: SaveStorage
    public val logger: Logger
    public val clock: Clock
}
