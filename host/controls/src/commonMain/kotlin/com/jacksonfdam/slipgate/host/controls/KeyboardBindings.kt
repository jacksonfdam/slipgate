package com.jacksonfdam.slipgate.host.controls

import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction

/**
 * A key on the player's keyboard, named rather than numbered.
 *
 * Compose's key codes differ per platform, so the control layer speaks its own small vocabulary and
 * each platform maps its events onto it. That keeps the bindings themselves portable.
 */
public enum class ControlKey {
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    W,
    A,
    S,
    D,
    Control,
    Space,
    Comma,
    Period,
    Tab,
    Escape,
    Enter,
}

/**
 * Which key does what.
 *
 * The defaults are the ones id Tech 1 players already have in their fingers: arrows or WASD to move,
 * control to fire, space to use, comma and period to change weapon, tab for the map.
 */
public data class KeyboardBindings(
    val actions: Map<ControlKey, GateAction> =
        mapOf(
            ControlKey.Control to GateAction.Fire,
            ControlKey.Space to GateAction.Use,
            ControlKey.Comma to GateAction.PreviousWeapon,
            ControlKey.Period to GateAction.NextWeapon,
            ControlKey.Tab to GateAction.Map,
            ControlKey.Escape to GateAction.Menu,
            ControlKey.Enter to GateAction.Confirm,
        ),
    val forward: Set<ControlKey> = setOf(ControlKey.ArrowUp, ControlKey.W),
    val backward: Set<ControlKey> = setOf(ControlKey.ArrowDown, ControlKey.S),
    val left: Set<ControlKey> = setOf(ControlKey.ArrowLeft, ControlKey.A),
    val right: Set<ControlKey> = setOf(ControlKey.ArrowRight, ControlKey.D),
)

/**
 * Turns key presses into the state a session reads.
 *
 * Movement is derived from which direction keys are held rather than tracked as an axis, because a
 * keyboard has no axis: releasing one of two opposed keys has to leave the other still pressed.
 */
public class KeyboardControls(
    private val state: ControlState,
    private val bindings: KeyboardBindings = KeyboardBindings(),
) {
    private val heldKeys = mutableSetOf<ControlKey>()

    /** Returns whether the key was one the bindings claim, so a caller can leave the rest alone. */
    public fun onKeyDown(key: ControlKey): Boolean = apply(key, pressed = true)

    public fun onKeyUp(key: ControlKey): Boolean = apply(key, pressed = false)

    public fun releaseAll() {
        heldKeys.clear()
        state.releaseAll()
    }

    private fun apply(
        key: ControlKey,
        pressed: Boolean,
    ): Boolean {
        val action = bindings.actions[key]
        when {
            action != null -> {
                state.setHeld(action, pressed)
            }

            isMovement(key) -> {
                if (pressed) heldKeys += key else heldKeys -= key
                state.moveTo(movementAxis())
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun isMovement(key: ControlKey): Boolean =
        key in bindings.forward || key in bindings.backward ||
            key in bindings.left || key in bindings.right

    private fun movementAxis(): Axis2 {
        val vertical =
            (if (heldKeys.any { it in bindings.forward }) 1f else 0f) -
                (if (heldKeys.any { it in bindings.backward }) 1f else 0f)
        val horizontal =
            (if (heldKeys.any { it in bindings.right }) 1f else 0f) -
                (if (heldKeys.any { it in bindings.left }) 1f else 0f)
        return Axis2(x = horizontal, y = vertical)
    }
}
