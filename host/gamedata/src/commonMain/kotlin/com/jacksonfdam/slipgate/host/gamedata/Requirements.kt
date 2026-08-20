package com.jacksonfdam.slipgate.host.gamedata

import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataRequirements

/**
 * The files a gate still needs before it can boot, given what is already on its shelf.
 *
 * A gate's requirement is keyed by the name its data is stored under, so this is a set difference
 * rather than a search: what a player supplied was renamed on the way in, and the key is what it was
 * renamed to.
 *
 * Optional entries are never unmet. A gate that would boot without a file is not waiting for it, and
 * a launcher that said otherwise would be holding shut a game the player can already play. Use
 * [absent] to find those, which is what a screen offering them wants.
 */
public fun DataRequirements.unmet(stored: Set<String>): List<DataEntry> =
    entries.filter { !it.optional && it.key !in stored }

/**
 * Everything the gate declared that is not on its shelf, whether it holds a boot up or not.
 *
 * This is what an offer is built from, where [unmet] is what a gate is held up by.
 */
public fun DataRequirements.absent(stored: Set<String>): List<DataEntry> = entries.filter { it.key !in stored }

/** Whether every file this gate needs is already stored. */
public suspend fun GameDataStore.satisfies(
    gate: String,
    requirements: DataRequirements,
): Boolean = requirements.unmet(names(gate)).isEmpty()
