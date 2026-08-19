package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.audio.AudioOutput
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.StoredSaves
import com.jacksonfdam.slipgate.host.runtime.Clock
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.Logger
import com.jacksonfdam.slipgate.host.runtime.MonotonicClock
import com.jacksonfdam.slipgate.host.runtime.PrintingLogger
import com.jacksonfdam.slipgate.host.runtime.SaveStorage

/**
 * Builds the services a gate runs on, one set per gate.
 *
 * Per gate rather than one shared set, because saves are not shared: two gates writing to the same
 * shelf would overwrite each other's slots. Audio is the exception and stays shared — it owns a
 * device, and only one session runs at a time.
 */
public class SessionHosts(
    private val audio: AudioOutput,
    private val store: GameDataStore,
) {
    public fun forGate(gate: String): GateHost =
        SessionHost(
            audio = audio,
            storage = StoredSaves(store = store, gate = gate),
            logger = PrintingLogger(tag = "slipgate/$gate"),
        )
}

private class SessionHost(
    // The platform's output rather than the bare sink from the contract: what the shell registers is
    // the thing that owns a device, and asking for the narrower type is what a container cannot see.
    override val audio: AudioOutput,
    override val storage: SaveStorage,
    override val logger: Logger,
) : GateHost {
    /** Made with the host, so what a session reads starts at the moment its gate opened. */
    override val clock: Clock = MonotonicClock()
}
