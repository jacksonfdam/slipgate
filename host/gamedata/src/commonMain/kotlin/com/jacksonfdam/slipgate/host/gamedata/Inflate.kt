package com.jacksonfdam.slipgate.host.gamedata

/**
 * Expands a raw deflate stream — no zlib or gzip wrapper, which is what a zip entry holds.
 *
 * Suspending because one platform's only decompressor is asynchronous: the browser expands through a
 * stream, and pretending otherwise would mean blocking the thread that draws.
 *
 * @throws InflateException when the stream is truncated or is not deflate at all.
 */
public expect suspend fun inflateRaw(
    bytes: ByteArray,
    expectedSize: Int,
): ByteArray
