package com.jacksonfdam.slipgate.ui

import androidx.compose.runtime.Composable

/**
 * The platform's own back gesture, where it has one.
 *
 * Android does: a swipe or a button that a player expects to leave whatever they are in. iOS and the
 * web do not, and their actuals do nothing rather than inventing a gesture nobody asked for.
 *
 * [enabled] decides whether this screen wants it. A screen that does not claim it lets the platform do
 * what it would have done — on Android, leave the app.
 */
@Composable
public expect fun SystemBack(
    enabled: Boolean,
    onBack: () -> Unit,
)
