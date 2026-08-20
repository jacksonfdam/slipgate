package com.jacksonfdam.slipgate.games.macil

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
public const val MACIL_IWAD: String = "strife1.wad"

/**
 * Strife's voice acting, which sits beside the IWAD rather than inside it.
 *
 * Optional, and genuinely so: without it every line is still subtitled and the game is complete.
 * With it, the game is the one Rogue shipped. There is no free replacement for either file.
 */
public const val MACIL_VOICES: String = "voices.wad"

/** The controls Strife has that Doom never did, as the pad and the session see them. */
public const val MACIL_INVENTORY_PREVIOUS: String = "macil.inventory.previous"
public const val MACIL_INVENTORY_NEXT: String = "macil.inventory.next"
public const val MACIL_INVENTORY_USE: String = "macil.inventory.use"
public const val MACIL_INVENTORY_DROP: String = "macil.inventory.drop"
public const val MACIL_USE_HEALTH: String = "macil.health.use"
public const val MACIL_QUERY: String = "macil.inventory.query"
public const val MACIL_MISSION: String = "macil.mission"
public const val MACIL_INVENTORY_POPUP: String = "macil.inventory.popup"
public const val MACIL_JUMP: String = "macil.jump"
public const val MACIL_LOOK_UP: String = "macil.look.up"
public const val MACIL_LOOK_DOWN: String = "macil.look.down"

/**
 * Strife, running as WebAssembly through Chocolate Doom's Strife port.
 *
 * The same shape as the other three gates, because the contract is the point. What differs is how
 * much of it Strife uses: eleven controls beyond Doom's against Heretic's five, a second data file,
 * and no free replacement for either.
 */
public class MacilGate : Gate {
    override val descriptor: GateDescriptor =
        GateDescriptor(
            id = GateId("macil"),
            title = "Macil",
            engine = "Strife",
            artwork = GateArtwork(coverKey = "macil/cover"),
            // Strife's palette is sodium amber over gunmetal, which is what makes the launcher read
            // as oxidised metal here rather than as hellfire or magic. As with the other gates the
            // index records the entry this gate considers its signature; PlaypalAccent samples by hue
            // cluster rather than reading it, so it documents intent rather than feeding a lookup.
            accent = AccentSource.PaletteEntry(index = 168),
        )

    /**
     * The IWAD, and the voices beside it.
     *
     * Neither has a freely licensed replacement — there is no Freedoom for Strife — so the card says
     * user-supplied only, the way Hexen's does, rather than showing a download that cannot work.
     */
    override fun requirements(): DataRequirements =
        DataRequirements(
            entries =
                listOf(
                    DataEntry(
                        key = MACIL_IWAD,
                        displayName = "Strife IWAD",
                        sources = listOf(DataSource.UserSupplied),
                    ),
                    DataEntry(
                        key = MACIL_VOICES,
                        displayName = "Strife voices",
                        sources = listOf(DataSource.UserSupplied),
                        optional = true,
                    ),
                ),
        )

    override fun sessionFactories(): Map<BackendId, GateSessionFactory> =
        mapOf(BackendId.Wasm to GateSessionFactory(::openWasmSession))

    /**
     * Strife's controls: Doom's, plus an inventory, a dialogue economy, jumping and free look.
     *
     * Eleven extensions is more than a pad can draw over a phone screen, and the pad drawing all of
     * them would cover the game — which is the reason the Doom gate declares four buttons rather than
     * eight. So the list is ordered by what a player reaches for while something is shooting at them:
     * the inventory pair, using an item, jumping and the medkit come first, and the layout takes the
     * first five. The rest are declared because the engine has them and a remap should be able to
     * reach them, not because the pad should show them.
     *
     * A look axis, unlike the other three gates: Strife shipped with free look and its levels are
     * built with height the player is expected to aim at.
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
                    InputExtension(key = MACIL_INVENTORY_PREVIOUS, label = "ITEM ‹"),
                    InputExtension(key = MACIL_INVENTORY_NEXT, label = "ITEM ›"),
                    InputExtension(key = MACIL_INVENTORY_USE, label = "USE ITEM"),
                    InputExtension(key = MACIL_JUMP, label = "JUMP"),
                    InputExtension(key = MACIL_USE_HEALTH, label = "MEDKIT"),
                    InputExtension(key = MACIL_INVENTORY_DROP, label = "DROP"),
                    InputExtension(key = MACIL_INVENTORY_POPUP, label = "ITEMS"),
                    InputExtension(key = MACIL_QUERY, label = "QUERY"),
                    InputExtension(key = MACIL_MISSION, label = "MISSION"),
                    InputExtension(key = MACIL_LOOK_UP, label = "LOOK ↑"),
                    InputExtension(key = MACIL_LOOK_DOWN, label = "LOOK ↓"),
                ),
            usesLookAxis = true,
        )
}
