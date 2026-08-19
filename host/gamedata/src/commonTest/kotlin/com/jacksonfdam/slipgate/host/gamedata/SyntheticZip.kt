package com.jacksonfdam.slipgate.host.gamedata

private const val LOCAL_HEADER_BYTES = 30
private const val DIRECTORY_ENTRY_BYTES = 46
private const val END_RECORD_BYTES = 22

/**
 * Builds an archive whose entries are stored rather than compressed.
 *
 * Stored entries need no compressor here, which keeps the fixture honest: it exercises the reader's
 * arithmetic — offsets, lengths, the backwards search for the index — without a second implementation
 * of deflate sitting in the tests. The compressed path is covered where a real compressor exists.
 */
internal fun syntheticZip(
    entries: List<Pair<String, ByteArray>>,
    comment: String = "",
): ByteArray {
    val locals = mutableListOf<ByteArray>()
    val directory = mutableListOf<ByteArray>()
    var offset = 0

    entries.forEach { (name, content) ->
        val nameBytes = name.encodeToByteArray()
        val local = ByteArray(LOCAL_HEADER_BYTES + nameBytes.size + content.size)
        writeInt(local, 0, 0x04034b50)
        writeShort(local, 18, content.size)
        writeShort(local, 22, content.size)
        writeShort(local, 26, nameBytes.size)
        nameBytes.copyInto(local, LOCAL_HEADER_BYTES)
        content.copyInto(local, LOCAL_HEADER_BYTES + nameBytes.size)
        locals += local

        val entry = ByteArray(DIRECTORY_ENTRY_BYTES + nameBytes.size)
        writeInt(entry, 0, 0x02014b50)
        writeInt(entry, 20, content.size)
        writeInt(entry, 24, content.size)
        writeShort(entry, 28, nameBytes.size)
        writeInt(entry, 42, offset)
        nameBytes.copyInto(entry, DIRECTORY_ENTRY_BYTES)
        directory += entry

        offset += local.size
    }

    val directoryBytes = directory.fold(ByteArray(0)) { all, entry -> all + entry }
    val commentBytes = comment.encodeToByteArray()
    val end = ByteArray(END_RECORD_BYTES + commentBytes.size)
    writeInt(end, 0, 0x06054b50)
    writeShort(end, 8, entries.size)
    writeShort(end, 10, entries.size)
    writeInt(end, 12, directoryBytes.size)
    writeInt(end, 16, offset)
    writeShort(end, 20, commentBytes.size)
    commentBytes.copyInto(end, END_RECORD_BYTES)

    return locals.fold(ByteArray(0)) { all, local -> all + local } + directoryBytes + end
}

private fun writeShort(
    bytes: ByteArray,
    offset: Int,
    value: Int,
) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = (value shr 8 and 0xFF).toByte()
}
