package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Asks through the system document picker.
 *
 * The delegate is remembered rather than created at the call: UIKit holds it weakly, and a delegate
 * that is collected between the picker opening and the player choosing takes the answer with it.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
public actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val delegate = remember { PickerDelegate() }
    delegate.onPicked = onPicked

    return {
        val picker =
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(publicItemType()))
        picker.delegate = delegate
        rootViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate :
    NSObject(),
    UIDocumentPickerDelegateProtocol {
    var onPicked: ((PickedFile) -> Unit)? = null

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        // The picker hands over a URL the app may read only while the access is held.
        val accessible = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
            }
            onPicked?.invoke(PickedFile(name = url.lastPathComponent ?: "supplied.wad", bytes = bytes))
        } finally {
            if (accessible) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

/** Any file at all: a WAD has no registered type, and a filter that hides the player's own file is
 * worse than inspecting one they chose in error. */
private fun publicItemType(): UTType = UTTypeItem

private fun rootViewController(): UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
