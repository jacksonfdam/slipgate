package com.jacksonfdam.slipgate.host.controls

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The keys the control layer knows, as Compose reports them.
 *
 * A table rather than a `when`, because Compose names a key rather than numbering it: the mapping is
 * a lookup and there is nothing per-platform about it, which is exactly why [ControlKey] exists as
 * its own vocabulary. Both control keys appear, and both enters, because a player's hand does not
 * care which side of the keyboard it reached for.
 */
private val controlKeys: Map<Key, ControlKey> =
    mapOf(
        Key.DirectionUp to ControlKey.ArrowUp,
        Key.DirectionDown to ControlKey.ArrowDown,
        Key.DirectionLeft to ControlKey.ArrowLeft,
        Key.DirectionRight to ControlKey.ArrowRight,
        Key.W to ControlKey.W,
        Key.A to ControlKey.A,
        Key.S to ControlKey.S,
        Key.D to ControlKey.D,
        Key.CtrlLeft to ControlKey.Control,
        Key.CtrlRight to ControlKey.Control,
        Key.Spacebar to ControlKey.Space,
        Key.Comma to ControlKey.Comma,
        Key.Period to ControlKey.Period,
        Key.Tab to ControlKey.Tab,
        Key.Escape to ControlKey.Escape,
        Key.Enter to ControlKey.Enter,
        Key.NumPadEnter to ControlKey.Enter,
    )

internal fun controlKeyFor(key: Key): ControlKey? = controlKeys[key]

/**
 * Sends a physical keyboard to a running gate.
 *
 * The surface takes focus as soon as it exists, because a keyboard that has to be clicked into first
 * is a keyboard that does not work — on the web especially, where the page starts with focus nowhere.
 * Losing focus releases everything held: a key the player was holding when they switched away is not
 * still held when they come back, and the engine would otherwise walk them into a wall.
 *
 * Keys the bindings do not claim are passed on rather than swallowed, so the shell keeps whatever it
 * wants from the keyboard.
 */
@Composable
public fun Modifier.gateKeyboard(
    state: ControlState,
    bindings: KeyboardBindings = KeyboardBindings(),
): Modifier {
    val controls = remember(state, bindings) { KeyboardControls(state, bindings) }
    val requester = remember { FocusRequester() }

    DisposableEffect(controls) {
        onDispose { controls.releaseAll() }
    }

    return this
        .focusRequester(requester)
        .onFocusChanged { focus -> if (!focus.isFocused) controls.releaseAll() }
        .focusable()
        .onPreviewKeyEvent { event -> controls.handle(event) }
        .also { RequestFocusOnce(requester) }
}

/** Focus is asked for once, when the surface appears. */
@Composable
private fun RequestFocusOnce(requester: FocusRequester) {
    DisposableEffect(requester) {
        runCatching { requester.requestFocus() }
        onDispose { }
    }
}

/** Whether the layer took the event, which is what tells Compose to stop passing it on. */
internal fun KeyboardControls.handle(event: KeyEvent): Boolean {
    val key = controlKeyFor(event.key) ?: return false
    return when (event.type) {
        KeyEventType.KeyDown -> onKeyDown(key)
        KeyEventType.KeyUp -> onKeyUp(key)
        else -> false
    }
}
