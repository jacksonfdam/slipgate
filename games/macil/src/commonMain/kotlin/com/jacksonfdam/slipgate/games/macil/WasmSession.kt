package com.jacksonfdam.slipgate.games.macil

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
 * Strife's key codes, from `doomkeys.h` and the defaults `M_BindStrifeControls` sets.
 *
 * The shared keys are Doom's, because the input layer is the same one.
 */
internal val STRIFE_KEYS: Map<GateAction, Int> =
    mapOf(
        GateAction.Fire to 0xA3, // KEY_RCTRL
        GateAction.Use to ' '.code,
        GateAction.NextWeapon to '\''.code,
        GateAction.PreviousWeapon to ';'.code,
        GateAction.Map to 9, // KEY_TAB
        GateAction.Menu to 27, // KEY_ESCAPE
        GateAction.Confirm to 13, // KEY_ENTER
    )

/**
 * The controls Strife has that Doom never did, at the codes `M_BindStrifeControls` gives them.
 *
 * Strife overrides several defaults the Raven games use, so these are not Heretic's numbers with a
 * few added: `key_invleft` and `key_invright` are the bracket pair for Heretic and Insert and Delete
 * here. Copying Corvus's table would compile and then bind the wrong keys.
 */
internal val STRIFE_EXTENSION_KEYS: Map<String, Int> =
    mapOf(
        MACIL_INVENTORY_PREVIOUS to 0x80 + 0x52, // KEY_INS, key_invleft
        MACIL_INVENTORY_NEXT to 0x80 + 0x53, // KEY_DEL, key_invright
        MACIL_INVENTORY_USE to 13, // KEY_ENTER, key_invuse
        MACIL_INVENTORY_DROP to 0x7F, // KEY_BACKSPACE, key_invdrop
        MACIL_USE_HEALTH to 'h'.code, // key_usehealth
        MACIL_QUERY to 'q'.code, // key_invquery
        MACIL_MISSION to 'w'.code, // key_mission
        MACIL_INVENTORY_POPUP to 'z'.code, // key_invpop
        MACIL_JUMP to 'a'.code, // key_jump
        MACIL_LOOK_UP to 0x80 + 0x49, // KEY_PGUP, key_lookup
        MACIL_LOOK_DOWN to 0x80 + 0x51, // KEY_PGDN, key_lookdown
    )

/**
 * The arrow keys, which are what Strife reads for travel and what its menus read for a choice.
 * Turning rather than strafing on the horizontal axis, the same as the other gates and for the same
 * reason: it is the engine's own default.
 */
private val STRIFE_DIRECTIONS =
    DirectionBindings(
        forward = 0xAD, // KEY_UPARROW
        backward = 0xAF, // KEY_DOWNARROW
        left = 0xAC, // KEY_LEFTARROW
        right = 0xAE, // KEY_RIGHTARROW
    )

/**
 * Turns off Strife's introduction, which the gate cannot boot through.
 *
 * `showintro` defaults to on and is what gates `I_InitGraphics()` in both `initStartup` and
 * `D_DoomLoop`. The host escapes start-up inside the engine's first `I_StartFrame`, by which point
 * graphics must already be up — so with the intro on, the gate never opens. Clearing it sends
 * `initStartup` to `TXT_Init()`, which the platform's absent text screen declines, and the engine
 * carries on to the graphics it would otherwise have skipped.
 */
private val STRIFE_SWITCHES = listOf("-nograph")

/** Boots the Strife module with the data the host mounted, and any add-ons stored beside it. */
internal suspend fun openWasmSession(
    data: MountedGameData,
    host: GateHost,
): GateSession {
    // The IWAD by the name the gate asked for, not whichever file the store happens to list first:
    // the launcher caches a palette sidecar beside the game data, and mounting 768 bytes of colours
    // as an IWAD is a gate that will not boot.
    val iwadName = MACIL_IWAD
    val iwad = data.read(iwadName)

    // The voices are an optional requirement rather than an add-on, so they are named here rather
    // than found: the engine reads voices.wad by name from its own filesystem, and it is not a patch
    // to load over anything.
    val voices = MACIL_VOICES.takeIf { it in data.names() }
    val addOns = data.addOnNames()

    host.logger.log(LogLevel.Info, "booting the macil gate from $iwadName")
    if (voices == null) {
        host.logger.log(LogLevel.Info, "no $MACIL_VOICES on the shelf; the game will be subtitled and silent")
    }
    if (addOns.isNotEmpty()) {
        host.logger.log(LogLevel.Info, "loading ${addOns.size} add-on(s) over it: ${addOns.joinToString()}")
    }

    val engine =
        startEngine(
            moduleBytes = macilModuleBytes(),
            files =
                buildMap {
                    put(iwadName, iwad)
                    if (voices != null) {
                        put(voices, data.read(voices))
                    }
                    addOns.forEach { name -> put(name, data.read(name)) }
                },
            arguments = engineArguments(iwadName, addOns, STRIFE_SWITCHES),
            host = GateHostBridge(host),
            // Whatever the player saved last time, back in the engine's own filesystem before it looks.
            saves = keptSaves(host),
        )

    return WasmGateSession(
        engine = engine,
        host = host,
        keyBindings = STRIFE_KEYS,
        directionBindings = STRIFE_DIRECTIONS,
        pixelAspect = ID_TECH_1_PIXEL_ASPECT,
        extensionBindings = STRIFE_EXTENSION_KEYS,
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
