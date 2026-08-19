package com.jacksonfdam.slipgate.games.mars

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
 * Doom's own key codes, from `doomkeys.h`. The engine speaks these and nothing else, so the
 * translation from Slipgate's normalised actions lives with the gate that knows them.
 */
private val DOOM_KEYS: Map<GateAction, Int> =
    mapOf(
        GateAction.Fire to 0xA3, // KEY_RCTRL
        GateAction.Use to ' '.code,
        GateAction.NextWeapon to '\''.code,
        GateAction.PreviousWeapon to ';'.code,
        GateAction.Map to 0x80 + 0x3B + 0x07, // KEY_F8, the automap in the default bindings
        GateAction.Menu to 27, // KEY_ESCAPE
    )

/**
 * The arrow keys, which are what Doom reads for travel and what its menus read for a choice. Turning
 * rather than strafing on the horizontal axis, because that is Doom's own default and a player who
 * grew up on it expects the left edge of a pad to turn them around.
 */
private val DOOM_DIRECTIONS =
    DirectionBindings(
        forward = 0xAD, // KEY_UPARROW
        backward = 0xAF, // KEY_DOWNARROW
        left = 0xAC, // KEY_LEFTARROW
        right = 0xAE, // KEY_RIGHTARROW
    )

/**
 * Boots the Doom module with the data the host mounted.
 *
 * `-nomusic` is passed because the platform layer mixes sound effects but leaves music to a later
 * measured budget; sound effects are on, and the host drains them as it steps.
 */
internal suspend fun openWasmSession(
    data: MountedGameData,
    host: GateHost,
): GateSession {
    val iwadName = data.names().firstOrNull() ?: error("no game data is mounted for the mars gate")
    val iwad = data.read(iwadName)

    host.logger.log(LogLevel.Info, "booting the mars gate from $iwadName")

    val engine =
        WasmEngine.start(
            moduleBytes = marsModuleBytes(),
            files = mapOf(iwadName to iwad),
            arguments = listOf("slipgate", "-iwad", iwadName, "-nomusic"),
            host = GateHostBridge(host),
        )

    return WasmGateSession(
        engine = engine,
        host = host,
        keyBindings = DOOM_KEYS,
        directionBindings = DOOM_DIRECTIONS,
        pixelAspect = ID_TECH_1_PIXEL_ASPECT,
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
