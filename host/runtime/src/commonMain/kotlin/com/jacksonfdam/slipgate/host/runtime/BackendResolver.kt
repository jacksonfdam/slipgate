package com.jacksonfdam.slipgate.host.runtime

/** Why a gate could not be started on this platform. */
public data class UnsupportedGate(
    val gateId: GateId,
    val requested: BackendId?,
    val available: Set<BackendId>,
)

/**
 * Chooses which backend runs a gate. The platform entry point supplies what it can execute,
 * in preference order, and the user may override it from settings.
 */
public class BackendResolver(
    private val supported: List<BackendId>,
    private val override: BackendId? = null,
) {
    init {
        require(supported.isNotEmpty()) { "a platform must support at least one backend" }
    }

    /** The backend that would run [gate], or null when none of them can. */
    public fun resolve(gate: Gate): BackendId? {
        val factories = gate.sessionFactories().keys
        if (override != null) {
            return override.takeIf { it in factories && it in supported }
        }
        return supported.firstOrNull { it in factories }
    }

    public fun factoryFor(gate: Gate): Result<GateSessionFactory> {
        val backend = resolve(gate)
        val factory = backend?.let { gate.sessionFactories()[it] }
        return if (factory == null) {
            Result.failure(
                UnsupportedGateException(
                    UnsupportedGate(
                        gateId = gate.descriptor.id,
                        requested = override,
                        available = gate.sessionFactories().keys,
                    ),
                ),
            )
        } else {
            Result.success(factory)
        }
    }
}

public class UnsupportedGateException(
    public val detail: UnsupportedGate,
) : IllegalStateException(
        "gate ${detail.gateId} cannot run here: requested ${detail.requested}, " +
            "gate offers ${detail.available}",
    )
