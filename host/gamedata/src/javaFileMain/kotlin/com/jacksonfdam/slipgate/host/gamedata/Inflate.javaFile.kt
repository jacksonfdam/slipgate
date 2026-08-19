package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.DataFormatException
import java.util.zip.Inflater

public actual suspend fun inflateRaw(
    bytes: ByteArray,
    expectedSize: Int,
): ByteArray =
    withContext(Dispatchers.Default) {
        // `true` is what makes it raw: the entry carries no zlib header for the inflater to read.
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val expanded = ByteArray(expectedSize)
            var written = 0
            while (written < expectedSize && !inflater.finished()) {
                val produced = inflater.inflate(expanded, written, expectedSize - written)
                if (produced == 0 && inflater.needsInput()) {
                    throw InflateException("the compressed stream ended after $written of $expectedSize bytes")
                }
                written += produced
            }
            if (written != expectedSize) {
                throw InflateException("expanded to $written bytes, not the $expectedSize promised")
            }
            expanded
        } catch (malformed: DataFormatException) {
            throw InflateException("the compressed stream is not deflate", malformed)
        } finally {
            inflater.end()
        }
    }
