package com.jacksonfdam.slipgate.games.korax

import com.jacksonfdam.slipgate.host.backend.wasm.DirectionBindings
import com.jacksonfdam.slipgate.host.backend.wasm.WasmGateSession
import com.jacksonfdam.slipgate.host.backend.wasm.WasmHost
import com.jacksonfdam.slipgate.host.backend.wasm.engineArguments
import com.jacksonfdam.slipgate.host.backend.wasm.keptSaves
import com.jacksonfdam.slipgate.host.backend.wasm.startEngine
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.host.runtime.addOnNames

/**
 * Hexen's key codes, from `doomkeys.h` and the defaults in `m_controls.c`.
 *
 * The same shared keys the other two gates read, because all three are the same engine's input layer:
 * fire is right control, use is space, Tab shows the map, Escape opens the game's own menu. The
 * weapon cycle is bound by the platform layer, because the engines ship it unbound.
 */
internal val HEXEN_KEYS: Map<GateAction, Int> =
    mapOf(
        // 0x80 + 0x1d, which is what doomkeys.h defines KEY_RCTRL as. It was 0xA3 here, a number that
        // is not a key at all: fire has never fired.
        GateAction.Fire to 0x9D, // KEY_RCTRL
        GateAction.Use to ' '.code,
        GateAction.NextWeapon to '\''.code,
        GateAction.PreviousWeapon to ';'.code,
        // Tab, which is what key_map_toggle is bound to in m_controls.c. It used to be F8 here, and
        // F8 is the messages toggle: the MAP button turned messages off rather than showing the map.
        GateAction.Map to 9, // KEY_TAB
        GateAction.Menu to 27, // KEY_ESCAPE
        // Enter, which Hexen reads twice over: it takes a menu choice, and in play it uses the
        // artifact the inventory is showing. That is the engine's own default, not this port's idea.
        GateAction.Confirm to 13, // KEY_ENTER
        // Hexen is the one game here with a jump, at the key m_controls.c gives it.
        GateAction.Jump to '/'.code, // key_jump
    )

/**
 * The controls Hexen has that Doom never did, at the codes `m_controls.c` gives them by default.
 *
 * Every key here is bound to an extension the gate declares in its input profile, so a button the pad
 * draws is a button the engine hears.
 */
internal val HEXEN_EXTENSION_KEYS: Map<String, Int> =
    mapOf(
        KORAX_INVENTORY_PREVIOUS to '['.code, // key_invleft
        KORAX_INVENTORY_NEXT to ']'.code, // key_invright
        KORAX_INVENTORY_USE to 13, // key_useartifact, which is Enter
        KORAX_FLY_UP to 0x80 + 0x49, // KEY_PGUP, key_flyup
        KORAX_FLY_DOWN to 0x80 + 0x52, // KEY_INS, key_flydown
    )

/**
 * The arrow keys, which are what Hexen reads for travel and what its menus read for a choice.
 * Turning rather than strafing on the horizontal axis, the same way the Doom gate does it and for the
 * same reason: it is the engine's own default.
 */
private val HEXEN_DIRECTIONS =
    DirectionBindings(
        forward = 0xAD, // KEY_UPARROW
        backward = 0xAF, // KEY_DOWNARROW
        left = 0xAC, // KEY_LEFTARROW
        right = 0xAE, // KEY_RIGHTARROW
    )

/**
 * Boots the Hexen module with the data the host mounted, and any add-ons stored beside it.
 *
 * Deathkings of the Dark Citadel is one of these: it ships with an `IWAD` signature but carries no
 * palette, so it is an add-on by what it holds rather than by what it claims, and it loads over
 * Hexen the same way any map pack would.
 */
internal suspend fun openWasmSession(
    data: MountedGameData,
    host: GateHost,
): GateSession {
    // The IWAD by the name the gate asked for, not whichever file the store happens to list first:
    // the launcher caches a palette sidecar beside the game data, and mounting 768 bytes of colours
    // as an IWAD is a gate that will not boot.
    val iwadName = KORAX_IWAD
    val iwad = data.read(iwadName)
    val addOns = data.addOnNames()

    host.logger.log(LogLevel.Info, "booting the korax gate from $iwadName")
    if (addOns.isNotEmpty()) {
        host.logger.log(LogLevel.Info, "loading ${addOns.size} add-on(s) over it: ${addOns.joinToString()}")
    }

    val engine =
        startEngine(
            moduleBytes = koraxModuleBytes(),
            files = mapOf(iwadName to iwad) + addOns.associateWith { data.read(it) },
            arguments = engineArguments(iwadName, addOns),
            host = GateHostBridge(host),
            // Whatever the player saved last time, back in the engine's own filesystem before it looks.
            saves = keptSaves(host),
        )

    return WasmGateSession(
        engine = engine,
        host = host,
        keyBindings = HEXEN_KEYS,
        directionBindings = HEXEN_DIRECTIONS,
        pixelAspect = ID_TECH_1_PIXEL_ASPECT,
        extensionBindings = HEXEN_EXTENSION_KEYS,
    )
}

/** Lets the engine reach the host's logger without knowing what a gate host is. */
private class GateHostBridge(
    private val host: GateHost,
) : WasmHost {
    override fun fatal(message: String) {
        host.logger.log(LogLevel.Error, message)
    }

    override fun log(message: String) {
        host.logger.log(LogLevel.Debug, message)
    }
}
