@file:OptIn(ExperimentalWasmJsInterop::class)
// The helper below has a JavaScript body, which static analysis cannot see reads its parameter.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsException
import kotlin.js.Promise

/** Fetches with the browser's own client. */
public actual fun platformDataDownload(): DataDownload = FetchDownload()

internal class FetchDownload : DataDownload {
    /**
     * Progress is reported once, when the whole file has arrived. Reading the response as a stream
     * would report more often, but every chunk would have to cross into WebAssembly to be counted,
     * and a progress bar is not worth that.
     */
    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray {
        val fetched =
            try {
                fetchBytes(url).await()
            } catch (failure: JsException) {
                throw DataDownloadException("the download did not finish: ${failure.message}", failure)
            }
        val bytes = (fetched ?: throw DataDownloadException("the file arrived empty")).toByteArray()
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return bytes
    }
}

private fun fetchBytes(url: String): Promise<Int8Array?> =
    js(
        """(async () => {
             const response = await fetch(url);
             if (!response.ok) { throw new Error('the server answered ' + response.status); }
             return new Int8Array(await response.arrayBuffer());
           })()""",
    )
