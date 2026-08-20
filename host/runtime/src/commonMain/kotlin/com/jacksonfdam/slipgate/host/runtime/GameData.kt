package com.jacksonfdam.slipgate.host.runtime

/** Where a required data file can legitimately come from. */
public sealed interface DataSource {
    /** A freely licensed replacement the app may offer to download. */
    public data class FreeDownload(
        val displayName: String,
        val url: String,
        /**
         * When [url] points at an archive, the file inside it to take, matched by how its name ends.
         * Both free replacements are published inside a zip, so this is the usual case rather than
         * the exception.
         */
        val archiveEntry: String? = null,
    ) : DataSource

    /** The user must supply the file; nothing may be offered for download. */
    public data object UserSupplied : DataSource
}

/** One file a gate wants, and whether it can run without it. */
public data class DataEntry(
    val key: String,
    val displayName: String,
    val sources: List<DataSource>,
    /**
     * Whether the gate boots without this file.
     *
     * Strife's voice acting is the case this exists for: `voices.wad` sits beside the IWAD, adds
     * every spoken line in the game, and the game runs subtitled without it. Treating it as required
     * would lock a player out of a game they own; leaving it out of the requirements entirely would
     * mean never offering it. It is neither, and saying so is what this is.
     */
    val optional: Boolean = false,
) {
    init {
        require(sources.isNotEmpty()) { "data entry $key must declare at least one source" }
    }
}

/** Everything a gate needs mounted before a session can be created. */
public data class DataRequirements(
    val entries: List<DataEntry>,
) {
    public val isSatisfiedByNothing: Boolean
        get() = entries.isEmpty()
}

/**
 * Marks a stored name as an add-on rather than the game itself.
 *
 * A shelf is one flat namespace — no platform here allows a separator in a stored name — so what
 * separates the game from the maps loaded over it has to live in the name. A prefix does it without
 * a second store or an index file that could drift out of step with what is actually on disk.
 *
 * It lives beside the mount rather than beside the storage code because a gate reads it at boot to
 * decide what to hand the engine, and a gate cannot see the storage layer.
 */
public const val ADD_ON_PREFIX: String = "addon."

/** Whether a stored name belongs to an add-on. */
public fun isAddOnName(name: String): Boolean = name.startsWith(ADD_ON_PREFIX)

/** The name an add-on was supplied under, with the marker taken back off for a player to read. */
public fun addOnDisplayName(name: String): String = name.removePrefix(ADD_ON_PREFIX)

/**
 * Read-only view of the data a user supplied for one gate. Backed by app-private storage on
 * every platform; sessions see names, never paths.
 */
public interface MountedGameData {
    public fun names(): Set<String>

    public suspend fun read(name: String): ByteArray

    public suspend fun size(name: String): Long

    public companion object {
        /** Mount with no files, for gates that need none. */
        public val Empty: MountedGameData =
            object : MountedGameData {
                override fun names(): Set<String> = emptySet()

                override suspend fun read(name: String): ByteArray =
                    throw NoSuchElementException("no mounted file named $name")

                override suspend fun size(name: String): Long =
                    throw NoSuchElementException("no mounted file named $name")
            }
    }
}

/**
 * The add-ons on this mount, in the order the engine should load them.
 *
 * Sorted by name, because load order decides which of two add-ons wins when both replace the same
 * lump, and a set's iteration order is not something a player could predict or a bug report could
 * describe. Alphabetical is arbitrary but it is the same every time.
 */
public fun MountedGameData.addOnNames(): List<String> = names().filter(::isAddOnName).sorted()
