package com.jacksonfdam.slipgate.host.gamedata

/** Builds a WAD whose lumps hold one byte each: the inspector reads names, not contents. */
internal fun syntheticWad(
    magic: String,
    lumps: List<String>,
): ByteArray {
    val headerBytes = 12
    val directoryOffset = headerBytes + lumps.size
    val bytes = ByteArray(directoryOffset + lumps.size * 16)

    magic.encodeToByteArray().copyInto(bytes)
    writeInt(bytes, offset = 4, value = lumps.size)
    writeInt(bytes, offset = 8, value = directoryOffset)

    lumps.forEachIndexed { index, name ->
        bytes[headerBytes + index] = 1
        val entry = directoryOffset + index * 16
        writeInt(bytes, entry, value = headerBytes + index)
        writeInt(bytes, entry + 4, value = 1)
        name.encodeToByteArray().copyInto(bytes, destinationOffset = entry + 8)
    }
    return bytes
}

internal fun writeInt(
    bytes: ByteArray,
    offset: Int,
    value: Int,
) {
    for (index in 0 until Int.SIZE_BYTES) {
        bytes[offset + index] = (value shr (index * Byte.SIZE_BITS) and 0xFF).toByte()
    }
}

internal fun readInt(
    bytes: ByteArray,
    offset: Int,
): Int {
    var value = 0
    for (index in 0 until Int.SIZE_BYTES) {
        value = value or ((bytes[offset + index].toInt() and 0xFF) shl (index * Byte.SIZE_BITS))
    }
    return value
}
