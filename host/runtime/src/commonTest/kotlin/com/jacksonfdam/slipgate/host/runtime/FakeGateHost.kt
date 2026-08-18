package com.jacksonfdam.slipgate.host.runtime

/** Host services that record instead of doing anything, for tests that only drive a session. */
class FakeGateHost(
    override val audio: RecordingAudioSink = RecordingAudioSink(),
    override val storage: InMemorySaveStorage = InMemorySaveStorage(),
    override val logger: RecordingLogger = RecordingLogger(),
    override val clock: SteppedClock = SteppedClock(),
) : GateHost

class RecordingAudioSink(
    override val sampleRate: Int = 44_100,
    override val channels: Int = 2,
) : AudioSink {
    val submitted: MutableList<Int> = mutableListOf()

    override fun submit(
        samples: ShortArray,
        frameCount: Int,
    ): Int {
        submitted += frameCount
        return frameCount
    }
}

class InMemorySaveStorage : SaveStorage {
    private val files = mutableMapOf<String, MutableMap<String, ByteArray>>()

    override suspend fun slots(): List<String> = files.keys.sorted()

    override suspend fun files(slot: String): List<String> = files[slot]?.keys?.sorted() ?: emptyList()

    override suspend fun read(
        slot: String,
        name: String,
    ): ByteArray? = files[slot]?.get(name)

    override suspend fun write(
        slot: String,
        name: String,
        bytes: ByteArray,
    ) {
        files.getOrPut(slot) { mutableMapOf() }[name] = bytes
    }

    override suspend fun delete(
        slot: String,
        name: String?,
    ) {
        if (name == null) {
            files.remove(slot)
        } else {
            files[slot]?.remove(name)
        }
    }
}

class RecordingLogger : Logger {
    val lines: MutableList<String> = mutableListOf()

    override fun log(
        level: LogLevel,
        message: String,
        cause: Throwable?,
    ) {
        lines += "$level: $message"
    }
}

class SteppedClock(
    private val stepMillis: Long = 16,
) : Clock {
    private var elapsed = 0L

    override fun elapsedMillis(): Long {
        elapsed += stepMillis
        return elapsed
    }
}
