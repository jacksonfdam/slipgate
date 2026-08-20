package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.storedAccent
import com.jacksonfdam.slipgate.host.gamedata.unmet
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.addOnDisplayName
import com.jacksonfdam.slipgate.host.runtime.isAddOnName

/**
 * Reads the rack from the gates the entry point registered and the files already stored.
 *
 * The store is asked once per gate rather than watched: a shelf only changes when the player installs
 * something, and the screen that does the installing says so when it is done.
 */
public suspend fun launcherState(
    gates: List<Gate>,
    store: GameDataStore,
): LauncherState =
    LauncherState(
        cards =
            gates.map { gate ->
                val gateId = gate.descriptor.id.value
                GateCard(
                    descriptor = gate.descriptor,
                    availability = availabilityOf(gate, store),
                    accent = storedAccent(store, gateId),
                    // Sorted the way the gate will load them, so what Settings lists top to bottom is
                    // the order in which two add-ons replacing the same map override each other.
                    addOns = addOnsOn(store, gateId),
                )
            },
    )

/** One gate's add-ons, named as the player supplied them, in the order the engine will load them. */
private suspend fun addOnsOn(
    store: GameDataStore,
    gateId: String,
): List<String> =
    store
        .names(gateId)
        .filter(::isAddOnName)
        .sorted()
        .map(::addOnDisplayName)

/** Reads one gate's availability, which is what changes after an install. */
public suspend fun availabilityOf(
    gate: Gate,
    store: GameDataStore,
): GateAvailability {
    val missing = gate.requirements().unmet(store.names(gate.descriptor.id.value)).firstOrNull()
    return when {
        missing == null -> {
            GateAvailability.Installed
        }

        missing.sources.any { source -> source is DataSource.FreeDownload } -> {
            GateAvailability.NeedsData(missing)
        }

        else -> {
            GateAvailability.UserSuppliedOnly(missing)
        }
    }
}
