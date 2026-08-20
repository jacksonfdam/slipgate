package com.jacksonfdam.slipgate.ui

import androidx.compose.runtime.Composable

/** iOS has no system back: the shell's own controls are the only way out, which is the platform's way. */
@Composable
public actual fun SystemBack(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Nothing to hook.
}
