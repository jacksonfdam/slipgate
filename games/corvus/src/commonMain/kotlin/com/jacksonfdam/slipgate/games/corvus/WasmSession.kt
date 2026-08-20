package com.jacksonfdam.slipgate.games.corvus

import com.jacksonfdam.slipgate.host.backend.wasm.DirectionBindings
import com.jacksonfdam.slipgate.host.backend.wasm.WasmEngine
import com.jacksonfdam.slipgate.host.backend.wasm.WasmGateSession
import com.jacksonfdam.slipgate.host.backend.wasm.WasmHost
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.ID_TECH_1_PIXEL_ASPECT
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.MountedGameData

/**
 * Heretic's key codes, from `doomkeys.h` and the defaults in `m_controls.c`.
 *
 * The same shared keys Doom reads, because both games are the same engine's input layer: fire is
 * right control, use is space, the weapons are the bracket-less pair, and Escape opens the game's own
 * menu.
 */
internal val HERETIC_KEYS: Map<GateAction, Int> =
    mapOf(
        GateAction.Fire to 0xA3, // KEY_RCTRL
        GateAction.Use to ' '.code,
        GateAction.NextWeapon to '\''.code,
        GateAction.PreviousWeapon to ';'.code,
        // Tab, which is what key_map_toggle is bound to in m_controls.c. It used to be F8 here, and
        // F8 is the messages toggle: the MAP button turned messages off rather than showing the map.
        GateAction.Map to 9, // KEY_TAB
        GateAction.Menu to 27, // KEY_ESCAPE
        // Enter, which Heretic reads twice over: it takes a menu choice, and in play it uses the
        // artifact the inventory is showing. That is the engine's own default, not this port's idea.
        GateAction.Confirm to 13, // KEY_ENTER
    )

/**
 * The controls Heretic has that Doom never did, at the codes `m_controls.c` gives them by default.
 *
 * Every key here is bound to an extension the gate declares in its input profile, so a button the pad
 * draws is a button the engine hears.
 */
internal val HERETIC_EXTENSION_KEYS: Map<String, Int> =
    mapOf(
        CORVUS_INVENTORY_PREVIOUS to '['.code, // key_invleft
        CORVUS_INVENTORY_NEXT to ']'.code, // key_invright
        CORVUS_INVENTORY_USE to 13, // key_useartifact, which is Enter
        CORVUS_FLY_UP to 0x80 + 0x49, // KEY_PGUP, key_flyup
        CORVUS_FLY_DOWN to 0x80 + 0x52, // KEY_INS, key_flydown
    )

/**
 * The arrow keys, which are what Heretic reads for travel and what its menus read for a choice.
 * Turning rather than strafing on the horizontal axis, the same way the Doom gate does it and for the
 * same reason: it is the engine's own default.
 */
private val HERETIC_DIRECTIONS =
    DirectionBindings(
        forward = 0xAD, // KEY_UPARROW
        backward = 0xAF, // KEY_DOWNARROW
        left = 0xAC, // KEY_LEFTARROW
        right = 0xAE, // KEY_RIGHTARROW
    )

/**
 * Boots the Heretic module with the data the host mounted.
 *
 * `-nomusic` is passed for the reason the Doom gate passes it: the platform layer mixes sound effects
 * and leaves music to a later measured budget.
 */
internal suspend fun openWasmSession(
    data: MountedGameData,
    host: GateHost,
): GateSession {
    val iwadName = data.names().firstOrNull() ?: error("no game data is mounted for the corvus gate")
    val iwad = data.read(iwadName)

    host.logger.log(LogLevel.Info, "booting the corvus gate from $iwadName")

    val engine =
        WasmEngine.start(
            moduleBytes = corvusModuleBytes(),
            files = mapOf(iwadName to iwad),
            arguments = listOf("slipgate", "-iwad", iwadName, "-nomusic"),
            host = GateHostBridge(host),
        )

    return WasmGateSession(
        engine = engine,
        host = host,
        keyBindings = HERETIC_KEYS,
        directionBindings = HERETIC_DIRECTIONS,
        pixelAspect = ID_TECH_1_PIXEL_ASPECT,
        extensionBindings = HERETIC_EXTENSION_KEYS,
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
