package com.jacksonfdam.slipgate.host.runtime

/**
 * A session whose engine keeps files worth keeping.
 *
 * Separate from [GateSession] because it is not every session's business and because it suspends:
 * writing a save reaches storage, and [GateSession.close] is called from places that cannot wait —
 * a composable being disposed, for one. The shell calls this on the way out of a gate, where it can.
 */
public interface SessionSaves {
    /** Copies whatever the engine has written into the host's own storage. */
    public suspend fun keepSaves()
}
