package com.jacksonfdam.slipgate.host.graphics.core

/** Result of choosing a backend, including what was passed over. */
public data class BackendSelection(
    val backend: GraphicsBackend,
    val requested: GraphicsBackendId?,
    val rejected: List<GraphicsBackendId>,
) {
    /** True when the user asked for a backend that could not be used. */
    public val fellBack: Boolean
        get() = requested != null && requested != backend.id
}

public class NoGraphicsBackendException(
    public val considered: List<GraphicsBackendId>,
) : IllegalStateException("no graphics backend is available; considered $considered")

/**
 * Picks the rendering path. Candidates are supplied by the platform entry point in preference
 * order, so this class never names a platform, and the classic backend is expected to sit last
 * as the one that always works.
 */
public class BackendSelector(
    private val candidates: List<GraphicsBackend>,
) {
    init {
        require(candidates.isNotEmpty()) { "at least one backend candidate is required" }
        val duplicates =
            candidates
                .groupingBy { it.id }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicates.isEmpty()) { "duplicate backend candidates: $duplicates" }
    }

    public fun available(): List<GraphicsBackendId> = candidates.filter { it.isAvailable() }.map { it.id }

    /**
     * Chooses [preferred] when it is available, otherwise the first available candidate.
     * Throws [NoGraphicsBackendException] when nothing can render, which is a bug in the
     * platform's candidate list rather than a condition to recover from.
     */
    public fun select(preferred: GraphicsBackendId? = null): BackendSelection {
        val rejected = mutableListOf<GraphicsBackendId>()
        val requestedBackend = candidates.firstOrNull { it.id == preferred }
        if (requestedBackend != null && requestedBackend.isAvailable()) {
            return BackendSelection(requestedBackend, preferred, rejected)
        }
        if (preferred != null) {
            rejected += preferred
        }
        for (candidate in candidates) {
            if (candidate.id == preferred) {
                continue
            }
            if (candidate.isAvailable()) {
                return BackendSelection(candidate, preferred, rejected)
            }
            rejected += candidate.id
        }
        throw NoGraphicsBackendException(candidates.map { it.id })
    }
}
