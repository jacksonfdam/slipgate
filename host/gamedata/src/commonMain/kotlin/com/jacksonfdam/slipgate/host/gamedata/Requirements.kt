package com.jacksonfdam.slipgate.host.gamedata

import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataRequirements

/**
 * The files a gate still needs, given what is already on its shelf.
 *
 * A gate's requirement is keyed by the name its data is stored under, so this is a set difference
 * rather than a search: what a player supplied was renamed on the way in, and the key is what it was
 * renamed to.
 */
public fun DataRequirements.unmet(stored: Set<String>): List<DataEntry> = entries.filter { it.key !in stored }

/** Whether every file this gate needs is already stored. */
public suspend fun GameDataStore.satisfies(
    gate: String,
    requirements: DataRequirements,
): Boolean = requirements.unmet(names(gate)).isEmpty()
