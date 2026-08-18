package com.jacksonfdam.slipgate.host.runtime

import kotlin.jvm.JvmInline

/** Stable identifier for a gate, used for storage keys and deep links. */
@JvmInline
public value class GateId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "gate id must not be blank" }
    }

    override fun toString(): String = value
}

/** Where a gate's cover art comes from. Resolution is the presentation layer's problem. */
public data class GateArtwork(
    val coverKey: String,
    val logoKey: String? = null,
)

/** How the launcher tints itself for a gate. */
public sealed interface AccentSource {
    /** A fixed colour, as 0xAARRGGBB. */
    public data class Fixed(
        val argb: Int,
    ) : AccentSource

    /** An index into the game's own palette, read once the data is mounted. */
    public data class PaletteEntry(
        val index: Int,
    ) : AccentSource
}

/** Everything the launcher needs to draw a gate before any data is mounted. */
public data class GateDescriptor(
    val id: GateId,
    val title: String,
    val engine: String,
    val artwork: GateArtwork,
    val accent: AccentSource,
)

/** Creates a session for one gate on one backend. */
public fun interface GateSessionFactory {
    public suspend fun create(
        data: MountedGameData,
        host: GateHost,
    ): GateSession
}

/**
 * A game the launcher can present and run. A gate declares what data it needs and which
 * backends can execute it; it never decides which backend is used.
 */
public interface Gate {
    public val descriptor: GateDescriptor

    public fun requirements(): DataRequirements

    public fun sessionFactories(): Map<BackendId, GateSessionFactory>

    public fun inputProfile(): InputProfile
}
