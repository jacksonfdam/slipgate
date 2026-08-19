package com.jacksonfdam.slipgate.host.gamedata

private const val END_OF_DIRECTORY_SIGNATURE = 0x06054b50
private const val DIRECTORY_ENTRY_SIGNATURE = 0x02014b50
private const val LOCAL_HEADER_SIGNATURE = 0x04034b50

private const val END_OF_DIRECTORY_BYTES = 22
private const val DIRECTORY_ENTRY_BYTES = 46
private const val LOCAL_HEADER_BYTES = 30

// The end record sits at the very end unless the archive carries a comment, which may be 64 KB.
private const val MAX_COMMENT_BYTES = 0xFFFF

private const val STORED = 0
private const val DEFLATED = 8

private const val BYTE_MASK = 0xFF

// Offsets of the fields this reader uses, counted from the start of each record.
private const val END_ENTRY_COUNT = 10
private const val END_DIRECTORY_OFFSET = 16
private const val ENTRY_COMPRESSION = 10
private const val ENTRY_COMPRESSED_SIZE = 20
private const val ENTRY_SIZE = 24
private const val ENTRY_NAME_LENGTH = 28
private const val ENTRY_EXTRA_LENGTH = 30
private const val ENTRY_COMMENT_LENGTH = 32
private const val ENTRY_HEADER_OFFSET = 42
private const val LOCAL_NAME_LENGTH = 26
private const val LOCAL_EXTRA_LENGTH = 28

/** Something in the archive did not make sense, with enough detail to tell which part. */
public class ZipException(
    message: String,
) : Exception(message)

/** One file inside an archive, as the central directory describes it. */
public data class ZipEntry(
    val name: String,
    val compressedSize: Int,
    val size: Int,
    val compression: Int,
    private val headerOffset: Int,
) {
    internal fun dataOffset(bytes: ByteArray): Int {
        if (readInt(bytes, headerOffset) != LOCAL_HEADER_SIGNATURE) {
            throw ZipException("$name has no local header where the directory said")
        }
        val nameLength = readShort(bytes, headerOffset + LOCAL_NAME_LENGTH)
        val extraLength = readShort(bytes, headerOffset + LOCAL_EXTRA_LENGTH)
        return headerOffset + LOCAL_HEADER_BYTES + nameLength + extraLength
    }
}

/**
 * Reads a zip archive held in memory.
 *
 * Only what a release archive actually uses is implemented: stored and deflated entries, read from
 * the central directory. Freedoom and Blasphemer both ship their WADs this way, so this is the last
 * step between a download and something a gate can boot.
 */
public class ZipArchive(
    private val bytes: ByteArray,
) {
    public val entries: List<ZipEntry> = readDirectory()

    /** The first entry whose name ends with [suffix], ignoring case and any folders inside. */
    public fun find(suffix: String): ZipEntry? = entries.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }

    /** Expands [entry] and returns its bytes. */
    public suspend fun read(entry: ZipEntry): ByteArray {
        val start = entry.dataOffset(bytes)
        if (start + entry.compressedSize > bytes.size) {
            throw ZipException("${entry.name} claims ${entry.compressedSize} bytes the archive lacks")
        }
        val compressed = bytes.copyOfRange(start, start + entry.compressedSize)
        return when (entry.compression) {
            STORED -> compressed
            DEFLATED -> inflateRaw(compressed, entry.size)
            else -> throw ZipException("${entry.name} uses compression method ${entry.compression}")
        }
    }

    private fun readDirectory(): List<ZipEntry> {
        val end = findEndOfDirectory()
        val count = readShort(bytes, end + END_ENTRY_COUNT)
        var cursor = readInt(bytes, end + END_DIRECTORY_OFFSET)

        return (0 until count).map {
            if (cursor + DIRECTORY_ENTRY_BYTES > bytes.size ||
                readInt(bytes, cursor) != DIRECTORY_ENTRY_SIGNATURE
            ) {
                throw ZipException("the archive's file list is damaged at byte $cursor")
            }
            val nameLength = readShort(bytes, cursor + ENTRY_NAME_LENGTH)
            val extraLength = readShort(bytes, cursor + ENTRY_EXTRA_LENGTH)
            val commentLength = readShort(bytes, cursor + ENTRY_COMMENT_LENGTH)
            val entry =
                ZipEntry(
                    name =
                        bytes.decodeToString(
                            cursor + DIRECTORY_ENTRY_BYTES,
                            cursor + DIRECTORY_ENTRY_BYTES + nameLength,
                        ),
                    compression = readShort(bytes, cursor + ENTRY_COMPRESSION),
                    compressedSize = readInt(bytes, cursor + ENTRY_COMPRESSED_SIZE),
                    size = readInt(bytes, cursor + ENTRY_SIZE),
                    headerOffset = readInt(bytes, cursor + ENTRY_HEADER_OFFSET),
                )
            cursor += DIRECTORY_ENTRY_BYTES + nameLength + extraLength + commentLength
            entry
        }
    }

    /** Searched backwards, because the record it looks for sits after everything else. */
    private fun findEndOfDirectory(): Int {
        if (bytes.size < END_OF_DIRECTORY_BYTES) {
            throw ZipException("the file is ${bytes.size} bytes, too small to be an archive")
        }
        val earliest = maxOf(0, bytes.size - END_OF_DIRECTORY_BYTES - MAX_COMMENT_BYTES)
        for (offset in bytes.size - END_OF_DIRECTORY_BYTES downTo earliest) {
            if (readInt(bytes, offset) == END_OF_DIRECTORY_SIGNATURE) {
                return offset
            }
        }
        throw ZipException("the file has no archive index; it may not be a zip at all")
    }
}

/** Zip stores its numbers little-endian, like the WADs it tends to carry. */
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

private fun readShort(
    bytes: ByteArray,
    offset: Int,
): Int = (bytes[offset].toInt() and BYTE_MASK) or ((bytes[offset + 1].toInt() and BYTE_MASK) shl Byte.SIZE_BITS)
