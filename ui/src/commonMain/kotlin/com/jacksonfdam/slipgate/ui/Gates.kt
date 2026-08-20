package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.mount
import com.jacksonfdam.slipgate.host.gamedata.unmet
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.ui.launcher.launcherState

/** What it takes to open a gate: the registry to find it, and the three services it runs on. */
internal class Gates(
    val registry: GateRegistry,
    val resolver: BackendResolver,
    val hosts: SessionHosts,
    val store: GameDataStore,
)

/** The rack read again from the shelves, keeping the player where they were standing in it. */
internal suspend fun Gates.reread(selected: Int = 0): Stage =
    Stage.Choosing(launcherState(registry.gates, store).select(selected))

/** Everything a chosen gate resolves to: missing data, a running session, or the reason. */
internal suspend fun Gates.openedStage(gate: Gate): Stage {
    val gateId = gate.descriptor.id.value
    val outstanding = gate.requirements().unmet(store.names(gateId)).firstOrNull()
    if (outstanding != null) {
        return Stage.NeedsData(gate, outstanding)
    }
    return resolver
        .factoryFor(gate)
        .mapCatching { factory ->
            Stage.Playing(
                session = factory.create(store.mount(gateId), hosts.forGate(gateId)),
                profile = gate.inputProfile(),
                title = gate.descriptor.title,
            ) as Stage
        }.getOrElse { failure -> Stage.Stuck(failure.message ?: "the gate did not open") }
}
