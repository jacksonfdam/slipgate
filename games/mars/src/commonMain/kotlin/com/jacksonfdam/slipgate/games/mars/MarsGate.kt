package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.runtime.AccentSource
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataRequirements
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateArtwork
import com.jacksonfdam.slipgate.host.runtime.GateDescriptor
import com.jacksonfdam.slipgate.host.runtime.GateId
import com.jacksonfdam.slipgate.host.runtime.GateSessionFactory
import com.jacksonfdam.slipgate.host.runtime.InputProfile

/** The IWAD the gate runs from, whatever the user actually supplies. */
public const val MARS_IWAD: String = "doom.wad"

/**
 * Freedoom's first phase, the freely licensed replacement Slipgate offers to download.
 *
 * A pinned release rather than the latest: what the app downloads should be the same file next month,
 * and a release that moved is a download that broke. The IWAD arrives inside the archive.
 */
private const val FREEDOOM_URL =
    "https://github.com/freedoom/freedoom/releases/download/v0.13.0/freedoom-0.13.0.zip"
private const val FREEDOOM_ENTRY = "freedoom1.wad"

/**
 * Doom, running as WebAssembly through Chocolate Doom.
 *
 * The gate declares what it needs and which backends can run it; it does not decide how it is
 * presented, when it steps, or where its data came from. That is what makes a second gate an
 * addition rather than a rewrite.
 */
public class MarsGate : Gate {
    override val descriptor: GateDescriptor =
        GateDescriptor(
            id = GateId("mars"),
            title = "Mars",
            engine = "Doom",
            artwork = GateArtwork(coverKey = "mars/cover"),
            // Doom's palette entry 176 is the deep red of its status bar; taking the accent from the
            // game's own palette is what makes the launcher look like the game it is about to run.
            accent = AccentSource.PaletteEntry(index = 176),
        )

    override fun requirements(): DataRequirements =
        DataRequirements(
            entries =
                listOf(
                    DataEntry(
                        key = MARS_IWAD,
                        displayName = "Doom IWAD",
                        sources =
                            listOf(
                                DataSource.FreeDownload(
                                    displayName = "Freedoom: Phase 1",
                                    url = FREEDOOM_URL,
                                    archiveEntry = FREEDOOM_ENTRY,
                                ),
                                DataSource.UserSupplied,
                            ),
                    ),
                ),
        )

    override fun sessionFactories(): Map<BackendId, GateSessionFactory> =
        mapOf(BackendId.Wasm to GateSessionFactory(::openWasmSession))

    /**
     * Doom needs fewer buttons than its successors: no inventory, no flight, no jumping. Declaring
     * that here is what lets the virtual gamepad show four buttons instead of eight.
     */
    override fun inputProfile(): InputProfile =
        InputProfile(
            actions =
                setOf(
                    GateAction.Fire,
                    GateAction.Use,
                    GateAction.NextWeapon,
                    GateAction.PreviousWeapon,
                    GateAction.Map,
                    GateAction.Menu,
                ),
            usesLookAxis = false,
        )
}
