package com.jacksonfdam.slipgate.host.gamedata

import com.jacksonfdam.slipgate.host.runtime.MountedGameData

/**
 * Where the files a player supplied are kept, one shelf per gate.
 *
 * Every platform keeps them somewhere the app owns and no other app can read: app-private storage on
 * Android, the app's Documents folder on iOS, the origin's private file system on the web. None of
 * it is ever part of the artifact, and nothing here exposes a path — a session sees names.
 */
public interface GameDataStore {
    /** Files stored for [gate], by the name they were stored under. */
    public suspend fun names(gate: String): Set<String>

    public suspend fun write(
        gate: String,
        name: String,
        bytes: ByteArray,
    )

    public suspend fun read(
        gate: String,
        name: String,
    ): ByteArray

    public suspend fun size(
        gate: String,
        name: String,
    ): Long

    /** Removes one file, or every file for [gate] when [name] is null. */
    public suspend fun delete(
        gate: String,
        name: String? = null,
    )
}

/** Presents one gate's shelf as the read-only view a session is given. */
public suspend fun GameDataStore.mount(gate: String): MountedGameData =
    StoredGameData(gate = gate, store = this, names = names(gate))

private class StoredGameData(
    private val gate: String,
    private val store: GameDataStore,
    private val names: Set<String>,
) : MountedGameData {
    override fun names(): Set<String> = names

    override suspend fun read(name: String): ByteArray = store.read(gate, require(name))

    override suspend fun size(name: String): Long = store.size(gate, require(name))

    /**
     * A mount is a snapshot of what was there when it was taken. Asking for anything else is a bug
     * in the caller rather than a missing file, and it says so.
     */
    private fun require(name: String): String {
        if (name !in names) {
            throw NoSuchElementException("$gate has no mounted file named $name")
        }
        return name
    }
}
