package com.jacksonfdam.slipgate.host.controls

import com.jacksonfdam.slipgate.host.runtime.ActionSet
import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.InputFrame

/**
 * What the player is currently doing, as a single mutable place.
 *
 * Every control surface — a virtual pad, a keyboard, a gamepad — writes here, and the session reads
 * one frame per step. That is what keeps two hands on a keyboard and a thumb on a screen from
 * fighting over the same state.
 */
public class ControlState {
    private var held = ActionSet.Empty
    private var movement = Axis2.Zero
    private var look = Axis2.Zero
    private val extensions = mutableMapOf<String, Float>()

    public fun press(action: GateAction) {
        held += action
    }

    public fun release(action: GateAction) {
        held -= action
    }

    public fun setHeld(
        action: GateAction,
        pressed: Boolean,
    ) {
        if (pressed) press(action) else release(action)
    }

    public fun moveTo(axis: Axis2) {
        movement = axis.clamped()
    }

    public fun lookTo(axis: Axis2) {
        look = axis.clamped()
    }

    /** Engine-specific controls a gate declared in its input profile. */
    public fun setExtension(
        key: String,
        value: Float,
    ) {
        extensions[key] = value
    }

    /** Everything the player is doing, as one frame. */
    public fun frame(): InputFrame =
        InputFrame(
            movement = movement,
            look = look,
            actions = held,
            extensions = extensions.toMap(),
        )

    /** Forgets held state, which is what a session change or a lost window should do. */
    public fun releaseAll() {
        held = ActionSet.Empty
        movement = Axis2.Zero
        look = Axis2.Zero
        extensions.clear()
    }
}

private fun Axis2.clamped(): Axis2 = Axis2(x = x.coerceIn(-1f, 1f), y = y.coerceIn(-1f, 1f))
