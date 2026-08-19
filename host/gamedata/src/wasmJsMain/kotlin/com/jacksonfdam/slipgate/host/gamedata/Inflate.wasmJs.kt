@file:OptIn(ExperimentalWasmJsInterop::class)
// The helper below has a JavaScript body, which static analysis cannot see reads its parameter.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsException
import kotlin.js.Promise

/**
 * Expands through the browser's own decompression stream.
 *
 * The browser has no synchronous decompressor at all, which is why the whole expect is suspending.
 */
public actual suspend fun inflateRaw(
    bytes: ByteArray,
    expectedSize: Int,
): ByteArray {
    val expanded = expand(bytes)
    if (expanded == null || expanded.size != expectedSize) {
        throw InflateException(
            "expanded to ${expanded?.size ?: 0} bytes, not the $expectedSize promised",
        )
    }
    return expanded
}

/** Returns the expanded entry, or null when this browser or this stream cannot produce one. */
private suspend fun expand(bytes: ByteArray): ByteArray? {
    if (!decompressionAvailable()) {
        throw InflateException("this browser cannot expand a compressed archive")
    }
    return try {
        inflateThroughStream(bytes.toInt8Array()).await()?.toByteArray()
    } catch (failure: JsException) {
        throw InflateException("the compressed stream is not deflate: ${failure.message}", failure)
    }
}

private fun decompressionAvailable(): Boolean = js("typeof DecompressionStream !== 'undefined'")

private fun inflateThroughStream(bytes: Int8Array): Promise<Int8Array?> =
    js(
        """(async () => {
             const compressed = new Blob([bytes]).stream();
             const expanded = compressed.pipeThrough(new DecompressionStream('deflate-raw'));
             return new Int8Array(await new Response(expanded).arrayBuffer());
           })()""",
    )
