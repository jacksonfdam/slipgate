package com.jacksonfdam.slipgate.host.gamedata

/** Pulls the raw 768-byte `PLAYPAL` palette out of a WAD, trusting nothing about it. */
internal object WadPaletteReader {
    private const val HEADER_BYTES = 12
    private const val MAGIC_BYTES = 4
    private const val DIRECTORY_ENTRY_BYTES = 16
    private const val LUMP_NAME_BYTES = 8
    private const val PALETTE_LUMP = "PLAYPAL"
    private const val BYTE_MASK = 0xFF
    internal const val PALETTE_BYTES = 768

    fun readPlaypal(bytes: ByteArray): ByteArray? {
        val directory = directoryOf(bytes) ?: return null
        return findPalette(bytes, directory[0], directory[1])
    }

    /** Returns [offset, lumpCount] when the header and directory are plausible. */
    private fun directoryOf(bytes: ByteArray): IntArray? {
        if (bytes.size < HEADER_BYTES) return null
        val magic = bytes.decodeToString(0, MAGIC_BYTES)
        val lumpCount = readLittleInt(bytes, MAGIC_BYTES)
        val directoryOffset = readLittleInt(bytes, MAGIC_BYTES + Int.SIZE_BYTES)
        val directoryEnd = directoryOffset.toLong() + lumpCount.toLong() * DIRECTORY_ENTRY_BYTES
        val plausible =
            (magic == "IWAD" || magic == "PWAD") &&
                lumpCount > 0 &&
                directoryOffset >= 0 &&
                directoryEnd <= bytes.size
        return if (plausible) intArrayOf(directoryOffset, lumpCount) else null
    }

    private fun findPalette(
        bytes: ByteArray,
        directoryOffset: Int,
        lumpCount: Int,
    ): ByteArray? {
        for (index in 0 until lumpCount) {
            val entry = directoryOffset + index * DIRECTORY_ENTRY_BYTES
            if (lumpName(bytes, entry) != PALETTE_LUMP) continue
            return paletteAt(bytes, entry)
        }
        return null
    }

    private fun paletteAt(
        bytes: ByteArray,
        entry: Int,
    ): ByteArray? {
        val offset = readLittleInt(bytes, entry)
        val size = readLittleInt(bytes, entry + Int.SIZE_BYTES)
        val valid = offset >= 0 && size >= PALETTE_BYTES && offset.toLong() + size <= bytes.size
        return if (valid) bytes.copyOfRange(offset, offset + PALETTE_BYTES) else null
    }

    private fun lumpName(
        bytes: ByteArray,
        entry: Int,
    ): String {
        val nameStart = entry + 2 * Int.SIZE_BYTES
        var end = nameStart
        while (end < nameStart + LUMP_NAME_BYTES && bytes[end] != 0.toByte()) end += 1
        return bytes.decodeToString(nameStart, end)
    }

    private fun readLittleInt(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        var value = 0
        for (index in 0 until Int.SIZE_BYTES) {
            value = value or ((bytes[offset + index].toInt() and BYTE_MASK) shl (index * Byte.SIZE_BITS))
        }
        return value
    }
}
