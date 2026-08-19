package com.jacksonfdam.slipgate.ui.data

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks through the system document picker.
 *
 * Any type is accepted rather than a WAD filter: the format has no registered type, providers report
 * whatever they like, and a filter that hides the player's own file is worse than inspecting one they
 * chose in error.
 */
@Composable
public actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val picked = withContext(Dispatchers.IO) { read(context, uri) }
                    if (picked != null) {
                        onPicked(picked)
                    }
                }
            }
        }
    return { launcher.launch(arrayOf("*/*")) }
}

private fun read(
    context: android.content.Context,
    uri: Uri,
): PickedFile? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedFile(name = displayName(context, uri), bytes = bytes)
}

/** The provider's own name for the file, falling back to the last path segment it exposed. */
private fun displayName(
    context: android.content.Context,
    uri: Uri,
): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            if (!name.isNullOrBlank()) {
                return name
            }
        }
    }
    return uri.lastPathSegment ?: "supplied.wad"
}
