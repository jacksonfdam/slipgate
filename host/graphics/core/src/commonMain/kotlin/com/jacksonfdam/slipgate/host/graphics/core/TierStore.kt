package com.jacksonfdam.slipgate.host.graphics.core

/**
 * Where the detected tier and the signals behind it are kept between runs. Platform
 * storage arrives with the settings work; the in-memory store keeps one process honest.
 */
public interface TierStore {
    public suspend fun load(): TierDecision?

    public suspend fun save(decision: TierDecision)
}

/** Process-lifetime store, also the test double. */
public class InMemoryTierStore : TierStore {
    private var decision: TierDecision? = null

    override suspend fun load(): TierDecision? = decision

    override suspend fun save(decision: TierDecision) {
        this.decision = decision
    }
}
