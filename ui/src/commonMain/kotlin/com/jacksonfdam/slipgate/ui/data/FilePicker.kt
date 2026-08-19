package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.Composable

/**
 * Remembers a way to ask the player for a file, and calls [onPicked] when they choose one.
 *
 * Returns the launcher rather than taking a button, so the screen decides what the control looks
 * like. Cancelling is silent: a player who changes their mind has not caused an error.
 */
@Composable
public expect fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit
