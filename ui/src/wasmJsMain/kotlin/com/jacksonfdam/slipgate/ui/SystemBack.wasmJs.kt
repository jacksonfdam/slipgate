package com.jacksonfdam.slipgate.ui

import androidx.compose.runtime.Composable

/**
 * The browser's back button belongs to the page's history, not to a screen inside it. Claiming it
 * would take a player out of the app when they meant to leave a menu.
 */
@Composable
public actual fun SystemBack(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Nothing to hook.
}
