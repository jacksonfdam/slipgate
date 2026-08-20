package com.jacksonfdam.slipgate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Whether the shell is still on screen.
 *
 * A backgrounded app is not a stopped app. The frame clock keeps handing out frames on Android
 * whether or not anything is drawn from them, so everything paid for by a frame — the interface's own
 * voice, a session being stepped — plays on into a phone that has gone in a pocket. Window focus is
 * not the same question: a notification shade takes the focus without taking the app off screen.
 *
 * Read this before making a sound, and pause whatever makes one when it goes false.
 */
@Composable
internal fun rememberInForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * Whether a running gate should stop stepping.
 *
 * Three reasons, and each one is a player who is not playing: the menu is open over the game, the
 * window lost the focus to something in front of it, or the app is off screen entirely. The last one
 * is the one focus cannot answer on its own — a platform is free to call a backgrounded window
 * focused, and a game stepping behind a locked screen still burns a battery and still lets the
 * monsters reach the player.
 */
internal fun gatePaused(
    menuOpen: Boolean,
    windowFocused: Boolean,
    inForeground: Boolean,
): Boolean = menuOpen || !windowFocused || !inForeground

/**
 * Whether the interface's own voice should hold still.
 *
 * It yields to a playing gate, which owns the device while it runs, and it yields to the app being
 * off screen — nothing the shell has to say is worth saying to a pocket.
 */
internal fun interfaceQuiet(
    inForeground: Boolean,
    gateRunning: Boolean,
    gateMenuOpen: Boolean,
): Boolean = !inForeground || (gateRunning && !gateMenuOpen)
