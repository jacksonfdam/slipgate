package com.jacksonfdam.slipgate.host.gamedata

private const val HEADER_BYTES = 12
private const val DIRECTORY_ENTRY_BYTES = 16
private const val LUMP_NAME_BYTES = 8
private const val MAGIC_BYTES = 4
private const val BYTE_MASK = 0xFF

/**
 * Reads one lump out of a WAD held in memory.
 *
 * The inspector already walks the directory to decide what a file is; this reads a named lump out of
 * the same structure, which is what the launcher needs to theme itself from a game's own palette
 * without booting the engine that owns it.
 *
 * Returns null when the file is not a readable WAD or holds no such lump, because both are ordinary
 * answers rather than failures: not every IWAD carries every lump.
 */
public fun readWadLump(
    bytes: ByteArray,
    name: String,
): ByteArray? {
    val directory = directoryOf(bytes)
    val wanted = name.uppercase()
    val entry =
        directory?.let { found ->
            (0 until found.count)
                .map { index -> found.offset + index * DIRECTORY_ENTRY_BYTES }
                .firstOrNull { entry -> lumpName(bytes, entry + 2 * Int.SIZE_BYTES) == wanted }
        }

    return entry?.let { at ->
        val position = readInt(bytes, at)
        val size = readInt(bytes, at + Int.SIZE_BYTES)
        val readable = position >= 0 && size > 0 && position.toLong() + size <= bytes.size
        if (readable) bytes.copyOfRange(position, position + size) else null
    }
}

/** Where the lump directory is and how long, or null when this is not a WAD worth reading. */
private class Directory(
    val offset: Int,
    val count: Int,
)

private fun directoryOf(bytes: ByteArray): Directory? {
    if (bytes.size < HEADER_BYTES || bytes.decodeToString(0, MAGIC_BYTES) !in setOf("IWAD", "PWAD")) {
        return null
    }
    val count = readInt(bytes, MAGIC_BYTES)
    val offset = readInt(bytes, MAGIC_BYTES + Int.SIZE_BYTES)
    val end = offset.toLong() + count.toLong() * DIRECTORY_ENTRY_BYTES
    val readable = count > 0 && offset >= HEADER_BYTES && end <= bytes.size
    return if (readable) Directory(offset = offset, count = count) else null
}

private fun lumpName(
    bytes: ByteArray,
    offset: Int,
): String {
    val end = (offset until offset + LUMP_NAME_BYTES).firstOrNull { bytes[it] == 0.toByte() }
    return bytes.decodeToString(offset, end ?: (offset + LUMP_NAME_BYTES)).uppercase()
}

private fun readInt(
    bytes: ByteArray,
    offset: Int,
): Int {
    var value = 0
    for (index in 0 until Int.SIZE_BYTES) {
        value = value or ((bytes[offset + index].toInt() and BYTE_MASK) shl (index * Byte.SIZE_BITS))
    }
    return value
}
