@file:OptIn(ExperimentalWasmJsInterop::class)
// The helper below has a JavaScript body, which static analysis cannot see reads its parameter.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
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
        // A fetch the browser refuses outright — a cross-origin one, most of all — rejects with a
        // JavaScript TypeError, which reaches Kotlin as an exception it has no type for. Caught as
        // broadly as that costs, because uncaught it escapes the whole flow and leaves the screen on
        // a progress bar that will never move.
        @Suppress("TooGenericExceptionCaught")
        val fetched =
            try {
                fetchBytes(url).await()
            } catch (failure: Throwable) {
                // The browser does not say why it refused, and its own wording is no use to anybody:
                // what a person can act on is that the file's host has to allow this page to read it.
                throw DataDownloadException(
                    "the browser refused the download; the file's host does not allow it from this page",
                    failure,
                )
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
