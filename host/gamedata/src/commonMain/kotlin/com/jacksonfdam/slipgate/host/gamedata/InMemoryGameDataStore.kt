package com.jacksonfdam.slipgate.host.gamedata

/**
 * A store that keeps everything in memory.
 *
 * It is what a test uses, and what a platform falls back to when its own storage cannot be opened:
 * the player can still supply a file and play, and only loses it when the app closes.
 */
public class InMemoryGameDataStore : GameDataStore {
    private val shelves = mutableMapOf<String, MutableMap<String, ByteArray>>()

    override suspend fun names(gate: String): Set<String> = shelves[gate]?.keys?.toSet() ?: emptySet()

    override suspend fun write(
        gate: String,
        name: String,
        bytes: ByteArray,
    ) {
        shelves.getOrPut(gate) { mutableMapOf() }[safeStorageName(name)] = bytes
    }

    override suspend fun read(
        gate: String,
        name: String,
    ): ByteArray = shelves[gate]?.get(name) ?: throw NoSuchElementException("$gate has no $name")

    override suspend fun size(
        gate: String,
        name: String,
    ): Long = read(gate, name).size.toLong()

    override suspend fun delete(
        gate: String,
        name: String?,
    ) {
        if (name == null) {
            shelves.remove(gate)
        } else {
            shelves[gate]?.remove(name)
        }
    }
}
