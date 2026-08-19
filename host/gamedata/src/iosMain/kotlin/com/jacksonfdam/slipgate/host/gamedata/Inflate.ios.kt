package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.uByteVar
import platform.zlib.z_stream

// A negative window size is zlib's way of saying the stream has no header, which is what a zip entry
// holds. The magnitude is the largest window deflate allows.
private const val RAW_WINDOW_BITS = -15

@OptIn(ExperimentalForeignApi::class)
public actual suspend fun inflateRaw(
    bytes: ByteArray,
    expectedSize: Int,
): ByteArray {
    val expanded = ByteArray(expectedSize)
    val complaint = expand(bytes, expanded, expectedSize)
    if (complaint != null) {
        throw InflateException(complaint)
    }
    return expanded
}

/** Returns what went wrong, or null when [destination] holds the whole expanded entry. */
@OptIn(ExperimentalForeignApi::class)
private fun expand(
    bytes: ByteArray,
    destination: ByteArray,
    expectedSize: Int,
): String? {
    if (bytes.isEmpty()) {
        return "there is nothing to expand"
    }
    memScoped {
        val stream = alloc<z_stream>()
        val started = inflateInit2(stream.ptr, RAW_WINDOW_BITS) == Z_OK
        try {
            var status = Z_OK
            if (started) {
                bytes.usePinned { source ->
                    destination.usePinned { target ->
                        stream.next_in = source.addressOf(0).reinterpret<uByteVar>()
                        stream.avail_in = bytes.size.toUInt()
                        stream.next_out = target.addressOf(0).reinterpret<uByteVar>()
                        stream.avail_out = expectedSize.toUInt()
                        status = inflate(stream.ptr, Z_FINISH)
                    }
                }
            }
            return when {
                status != Z_STREAM_END -> {
                    "zlib stopped with $status after ${stream.total_out} bytes"
                }

                stream.total_out.toInt() != expectedSize -> {
                    "expanded to ${stream.total_out} bytes, not the $expectedSize promised"
                }

                else -> {
                    null
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}
