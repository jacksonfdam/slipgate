package com.jacksonfdam.slipgate.games.corvus

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
import com.jacksonfdam.slipgate.host.runtime.InputExtension
import com.jacksonfdam.slipgate.host.runtime.InputProfile

/** The IWAD the gate runs from, whatever the user actually supplies. */
public const val CORVUS_IWAD: String = "heretic.wad"

/**
 * Blasphemer, the freely licensed replacement Slipgate offers to download for this gate.
 *
 * A pinned release rather than the latest, for the reason the Doom gate pins Freedoom: what the app
 * downloads should be the same file next month. The IWAD arrives inside the archive, and the entry is
 * matched by how its name ends, so a release that nests it in a folder still resolves.
 */
private const val BLASPHEMER_URL =
    "https://github.com/Blasphemer/blasphemer/releases/download/v0.1.8/blasphem-0.1.8.zip"
private const val BLASPHEMER_ENTRY = "blasphem.wad"

/** The inventory and flight controls Heretic has and Doom does not, as the pad and the session see them. */
public const val CORVUS_INVENTORY_PREVIOUS: String = "corvus.inventory.previous"
public const val CORVUS_INVENTORY_NEXT: String = "corvus.inventory.next"
public const val CORVUS_INVENTORY_USE: String = "corvus.inventory.use"
public const val CORVUS_FLY_UP: String = "corvus.fly.up"
public const val CORVUS_FLY_DOWN: String = "corvus.fly.down"

/**
 * Heretic, running as WebAssembly through Chocolate Doom's Heretic port.
 *
 * The same shape as the Doom gate, because the contract is the point: what differs is the module it
 * boots, the keys that module speaks, and the controls Heretic has that Doom never did.
 */
public class CorvusGate : Gate {
    override val descriptor: GateDescriptor =
        GateDescriptor(
            id = GateId("corvus"),
            title = "Corvus",
            engine = "Heretic",
            artwork = GateArtwork(coverKey = "corvus/cover"),
            // The launcher recolours itself from this game's own PLAYPAL, which is what makes it
            // green-gold here and red for Doom with no colour written down in the app. The index
            // records the entry this gate considers its signature — Heretic's gold band — but note
            // that PlaypalAccent samples by hue cluster rather than reading it, so it is
            // documentation of intent rather than the input to a lookup.
            accent = AccentSource.PaletteEntry(index = 220),
        )

    override fun requirements(): DataRequirements =
        DataRequirements(
            entries =
                listOf(
                    DataEntry(
                        key = CORVUS_IWAD,
                        displayName = "Heretic IWAD",
                        sources =
                            listOf(
                                DataSource.FreeDownload(
                                    displayName = "Blasphemer",
                                    url = BLASPHEMER_URL,
                                    archiveEntry = BLASPHEMER_ENTRY,
                                ),
                                DataSource.UserSupplied,
                            ),
                    ),
                ),
        )

    override fun sessionFactories(): Map<BackendId, GateSessionFactory> =
        mapOf(BackendId.Wasm to GateSessionFactory(::openWasmSession))

    /**
     * Heretic's controls, which are Doom's plus an inventory, flight and a look axis.
     *
     * The five extras travel as extensions rather than as new [GateAction] entries, because they are
     * this engine's and not every engine's — that is what the extension list is for, and it is what
     * lets the pad show the buttons Heretic uses without Doom growing them too.
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
                    GateAction.Confirm,
                ),
            extensions =
                listOf(
                    InputExtension(key = CORVUS_INVENTORY_PREVIOUS, label = "ITEM ‹"),
                    InputExtension(key = CORVUS_INVENTORY_NEXT, label = "ITEM ›"),
                    InputExtension(key = CORVUS_INVENTORY_USE, label = "USE ITEM"),
                    InputExtension(key = CORVUS_FLY_UP, label = "FLY UP"),
                    InputExtension(key = CORVUS_FLY_DOWN, label = "FLY DOWN"),
                ),
            // No look axis yet: nothing produces one — the pad has one wheel and the session reads
            // movement only — and Heretic plays without free look, as it did on a keyboard. Declaring
            // an axis no control fills would be a promise to the layout that nothing keeps.
            usesLookAxis = false,
        )
}
