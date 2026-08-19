@file:OptIn(ExperimentalWasmJsInterop::class)

package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Asks through a file input the page creates and discards.
 *
 * A browser will only open a file dialogue from inside a real user gesture, which is why the input is
 * created and clicked in the same turn rather than kept around between openings.
 */
@Composable
public actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val chosen: ChosenFile? = chooseFile().await()
            if (chosen != null) {
                onPicked(PickedFile(name = chosen.name, bytes = chosen.bytes.toByteArray()))
            }
        }
    }
}

private external interface ChosenFile : JsAny {
    val name: String
    val bytes: Int8Array
}

private fun chooseFile(): Promise<ChosenFile?> =
    js(
        """(() => new Promise((resolve) => {
             const input = document.createElement('input');
             input.type = 'file';
             input.style.display = 'none';
             document.body.appendChild(input);
             const finish = (value) => {
               if (input.parentNode) { input.parentNode.removeChild(input); }
               resolve(value);
             };
             input.addEventListener('change', async () => {
               const file = input.files && input.files[0];
               if (!file) { finish(null); return; }
               const bytes = new Int8Array(await file.arrayBuffer());
               finish({ name: file.name, bytes: bytes });
             });
             input.addEventListener('cancel', () => finish(null));
             input.click();
           }))()""",
    )
