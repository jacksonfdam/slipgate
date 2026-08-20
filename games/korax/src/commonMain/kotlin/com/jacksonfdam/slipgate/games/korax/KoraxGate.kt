package com.jacksonfdam.slipgate.games.korax

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
public const val KORAX_IWAD: String = "hexen.wad"

/** The inventory and flight controls Hexen has, as the pad and the session see them. */
public const val KORAX_INVENTORY_PREVIOUS: String = "korax.inventory.previous"
public const val KORAX_INVENTORY_NEXT: String = "korax.inventory.next"
public const val KORAX_INVENTORY_USE: String = "korax.inventory.use"
public const val KORAX_FLY_UP: String = "korax.fly.up"
public const val KORAX_FLY_DOWN: String = "korax.fly.down"

/**
 * Hexen, running as WebAssembly through Chocolate Doom's Hexen port.
 *
 * The third gate and the same shape as the first two, which is the contract's own claim. What differs
 * is the module, the keys, and that Hexen is the one game here with a jump.
 */
public class KoraxGate : Gate {
    override val descriptor: GateDescriptor =
        GateDescriptor(
            id = GateId("korax"),
            title = "Korax",
            engine = "Hexen",
            artwork = GateArtwork(coverKey = "korax/cover"),
            // The launcher recolours itself from this game's own PLAYPAL — cold blue here, where Doom
            // is red and Heretic gold. The index records the entry this gate considers its signature;
            // PlaypalAccent samples by hue cluster rather than reading it, so it is documentation of
            // intent rather than the input to a lookup.
            accent = AccentSource.PaletteEntry(index = 200),
        )

    /**
     * Hexen has no freely licensed replacement, which is why the README's gate table says so: there is
     * no Blasphemer for it. The gate asks for the player's own copy and offers nothing it cannot keep.
     */
    override fun requirements(): DataRequirements =
        DataRequirements(
            entries =
                listOf(
                    DataEntry(
                        key = KORAX_IWAD,
                        displayName = "Hexen IWAD",
                        sources = listOf(DataSource.UserSupplied),
                    ),
                ),
        )

    override fun sessionFactories(): Map<BackendId, GateSessionFactory> =
        mapOf(BackendId.Wasm to GateSessionFactory(::openWasmSession))

    /**
     * Hexen's controls: Heretic's, plus the jump the Raven engines gained with it.
     *
     * The class-specific artifact hotkeys are deliberately absent. Hexen binds a key per artifact as
     * well as the inventory it shares with Heretic, and a pad that drew all of them would be a
     * keyboard; the inventory walk reaches every one of them.
     */
    override fun inputProfile(): InputProfile =
        InputProfile(
            actions =
                setOf(
                    GateAction.Fire,
                    GateAction.Use,
                    GateAction.Jump,
                    GateAction.NextWeapon,
                    GateAction.PreviousWeapon,
                    GateAction.Map,
                    GateAction.Menu,
                    GateAction.Confirm,
                ),
            extensions =
                listOf(
                    InputExtension(key = KORAX_INVENTORY_PREVIOUS, label = "ITEM ‹"),
                    InputExtension(key = KORAX_INVENTORY_NEXT, label = "ITEM ›"),
                    InputExtension(key = KORAX_INVENTORY_USE, label = "USE ITEM"),
                    InputExtension(key = KORAX_FLY_UP, label = "FLY UP"),
                    InputExtension(key = KORAX_FLY_DOWN, label = "FLY DOWN"),
                ),
            // No look axis, for the reason the Heretic gate has none: nothing produces one yet.
            usesLookAxis = false,
        )
}
