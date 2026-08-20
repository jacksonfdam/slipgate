package com.jacksonfdam.slipgate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
public actual fun SystemBack(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
