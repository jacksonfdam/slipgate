package com.jacksonfdam.slipgate.host.gamedata

import com.jacksonfdam.slipgate.host.runtime.SaveStorage

/** Separates a slot from the file inside it. Doubled, because a single underscore is a legal name. */
private const val SLOT_SEPARATOR = "__"

/** The shelf a gate's saves live on: beside the game data rather than inside it. */
internal fun saveShelf(gate: String): String = "saves.$gate"

/**
 * A gate's save files, kept on their own shelf in the same store the game data uses.
 *
 * A separate shelf rather than a separate folder per slot: every platform's store is two levels
 * deep — a shelf and the names on it — and slots are the third. Encoding the slot into the name is
 * what lets all three platforms keep saves without a second storage backend to go wrong.
 *
 * Clearing a gate's game data leaves its saves alone, which is the behaviour a player expects of a
 * game they reinstalled rather than abandoned.
 */
public class StoredSaves(
    private val store: GameDataStore,
    private val gate: String,
) : SaveStorage {
    override suspend fun slots(): List<String> =
        stored()
            .map { name -> name.substringBefore(SLOT_SEPARATOR) }
            .distinct()
            .sorted()

    override suspend fun files(slot: String): List<String> {
        val prefix = key(slot) + SLOT_SEPARATOR
        return stored().filter { name -> name.startsWith(prefix) }.map { name -> name.removePrefix(prefix) }.sorted()
    }

    override suspend fun read(
        slot: String,
        name: String,
    ): ByteArray? {
        val stored = path(slot, name)
        if (stored !in stored()) return null
        return store.read(saveShelf(gate), stored)
    }

    override suspend fun write(
        slot: String,
        name: String,
        bytes: ByteArray,
    ) {
        store.write(saveShelf(gate), path(slot, name), bytes)
    }

    override suspend fun delete(
        slot: String,
        name: String?,
    ) {
        if (name != null) {
            store.delete(saveShelf(gate), path(slot, name))
            return
        }
        val prefix = key(slot) + SLOT_SEPARATOR
        stored().filter { stored -> stored.startsWith(prefix) }.forEach { stored ->
            store.delete(saveShelf(gate), stored)
        }
    }

    private suspend fun stored(): Set<String> = store.names(saveShelf(gate))

    private fun path(
        slot: String,
        name: String,
    ): String = key(slot) + SLOT_SEPARATOR + safeStorageName(name)

    /** A slot name with no separator left in it, so the split back into slot and file is exact. */
    private fun key(slot: String): String = safeStorageName(slot).replace(SLOT_SEPARATOR, "_")
}
