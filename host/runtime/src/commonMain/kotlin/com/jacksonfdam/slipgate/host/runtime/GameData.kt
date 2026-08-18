package com.jacksonfdam.slipgate.host.runtime

/** Where a required data file can legitimately come from. */
public sealed interface DataSource {
    /** A freely licensed replacement the app may offer to download. */
    public data class FreeDownload(
        val displayName: String,
        val url: String,
    ) : DataSource

    /** The user must supply the file; nothing may be offered for download. */
    public data object UserSupplied : DataSource
}

/** One file a gate needs before it can run. */
public data class DataEntry(
    val key: String,
    val displayName: String,
    val sources: List<DataSource>,
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
