package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.host.backend.wasm.DirectionBindings
import com.jacksonfdam.slipgate.host.backend.wasm.EngineInstance
import com.jacksonfdam.slipgate.host.backend.wasm.WasmGateSession
import com.jacksonfdam.slipgate.host.backend.wasm.WasmHost
import com.jacksonfdam.slipgate.host.backend.wasm.keptSaves
import com.jacksonfdam.slipgate.host.backend.wasm.startEngine
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
internal val DOOM_KEYS: Map<GateAction, Int> =
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
        GateAction.Confirm to 13, // KEY_ENTER, what the menus read as a choice
    )

/**
 * The arrow keys, which are what Doom reads for travel and what its menus read for a choice. Turning
 * rather than strafing on the horizontal axis, because that is Doom's own default and a player who
 * grew up on it expects the left edge of a pad to turn them around.
 */
internal val DOOM_DIRECTIONS =
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
): GateSession = session(bootEngine(data, host), host)

/**
 * Boots the module and starts playing a demo the game data carries.
 *
 * Playback is started through the engine's own entry point after boot rather than with `-playdemo`,
 * because the command-line route hands the engine a pointer into start-up's stack frame — and this
 * port escapes that frame, which leaves the engine holding a name that is no longer there. The
 * symptom is a demo that plays perfectly and then dies at its final tic.
 *
 * [untilTheEnd] decides what the end of the demo means: the session finishes, or the engine returns
 * to its title screen, which is what an attract loop wants.
 */
internal suspend fun openWasmDemoSession(
    data: MountedGameData,
    host: GateHost,
    demo: String,
    untilTheEnd: Boolean = true,
): GateSession {
    val engine = bootEngine(data, host)
    check(engine.playDemo(demo, untilTheEnd)) { "the engine would not play the demo $demo" }
    return session(engine, host)
}

private suspend fun bootEngine(
    data: MountedGameData,
    host: GateHost,
): EngineInstance {
    // The IWAD by the name the gate asked for, not whichever file the store happens to list first:
    // the launcher caches a palette sidecar beside the game data, and mounting 768 bytes of colours
    // as an IWAD is a gate that will not boot.
    val iwadName = MARS_IWAD
    val iwad = data.read(iwadName)

    host.logger.log(LogLevel.Info, "booting the mars gate from $iwadName")

    return startEngine(
        moduleBytes = marsModuleBytes(),
        files = mapOf(iwadName to iwad),
        arguments = listOf("slipgate", "-iwad", iwadName, "-nomusic"),
        host = GateHostBridge(host),
        // Whatever the player saved last time, back in the engine's own filesystem before it looks.
        saves = keptSaves(host),
    )
}

private fun session(
    engine: EngineInstance,
    host: GateHost,
): GateSession =
    WasmGateSession(
        engine = engine,
        host = host,
        keyBindings = DOOM_KEYS,
        directionBindings = DOOM_DIRECTIONS,
        pixelAspect = ID_TECH_1_PIXEL_ASPECT,
    )

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
