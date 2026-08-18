package com.jacksonfdam.slipgate.host.runtime

/**
 * The gates this build knows about. Populated by the platform entry point, which is the only
 * place allowed to name concrete gates: nothing under `host` may depend on a game module.
 */
public class GateRegistry(
    gates: List<Gate>,
) {
    public val gates: List<Gate> = gates.toList()

    init {
        val duplicates =
            gates
                .groupingBy { it.descriptor.id }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicates.isEmpty()) { "duplicate gate ids registered: $duplicates" }
    }

    public operator fun get(id: GateId): Gate? = gates.firstOrNull { it.descriptor.id == id }

    public val descriptors: List<GateDescriptor>
        get() = gates.map { it.descriptor }
}
