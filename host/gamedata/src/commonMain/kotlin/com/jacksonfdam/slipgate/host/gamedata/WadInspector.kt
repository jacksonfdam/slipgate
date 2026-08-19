package com.jacksonfdam.slipgate.host.gamedata

private const val HEADER_BYTES = 12
private const val DIRECTORY_ENTRY_BYTES = 16
private const val LUMP_NAME_BYTES = 8
private const val MAGIC_BYTES = 4
private const val PALETTE_LUMP = "PLAYPAL"

// Raven's engines carry a translucency table Doom has no use for, which is the cheapest way to tell
// the two families apart without trusting a filename.
private const val RAVEN_LUMP = "TINTTAB"

// A directory larger than this is not a game, and reading it would only waste a phone's memory.
private const val MAX_LUMPS = 65_536

private const val BYTE_MASK = 0xFF
private const val EPISODE_LIMIT = 9
private const val MAP_LIMIT = 99
private const val HEX = 16

/**
 * Decides what a supplied file is by reading it, never by its name.
 *
 * A user's IWAD can be called anything, and a file called `doom2.wad` can be anything, so the
 * contents are the only trustworthy evidence. The whole file is taken as bytes because mounting one
 * puts every byte inside the engine's memory anyway; nothing is saved by streaming it here.
 */
public object WadInspector {
    public fun inspect(bytes: ByteArray): WadInspection {
        val kind = if (bytes.size >= HEADER_BYTES) kindOf(bytes) else null
        return when {
            bytes.size < HEADER_BYTES -> rejected(RejectionReason.TooSmall, "the file is ${bytes.size} bytes")
            kind == null -> rejected(RejectionReason.NotAWad, "the file starts with ${signature(bytes)}")
            else -> readDirectory(bytes, kind)
        }
    }

    private fun kindOf(bytes: ByteArray): WadKind? =
        when (bytes.decodeToString(0, MAGIC_BYTES)) {
            "IWAD" -> WadKind.Iwad
            "PWAD" -> WadKind.Pwad
            else -> null
        }

    /** Hexadecimal, because a file that is not a WAD holds no text worth quoting back. */
    private fun signature(bytes: ByteArray): String =
        (0 until MAGIC_BYTES).joinToString(" ") { index ->
            (bytes[index].toInt() and BYTE_MASK).toString(HEX).padStart(2, '0')
        }

    private fun readDirectory(
        bytes: ByteArray,
        kind: WadKind,
    ): WadInspection {
        val lumpCount = readInt(bytes, MAGIC_BYTES)
        val directoryOffset = readInt(bytes, MAGIC_BYTES + Int.SIZE_BYTES)

        if (!directoryFits(bytes.size, lumpCount, directoryOffset)) {
            return rejected(
                RejectionReason.DirectoryUnreadable,
                "$lumpCount lumps listed at $directoryOffset in a ${bytes.size} byte file",
            )
        }

        val names = mutableSetOf<String>()
        val truncated = collectNames(bytes, directoryOffset, lumpCount, names)

        return if (truncated == null) {
            identify(kind, names, lumpCount)
        } else {
            rejected(RejectionReason.LumpOutOfRange, truncated)
        }
    }

    private fun directoryFits(
        size: Int,
        lumpCount: Int,
        offset: Int,
    ): Boolean {
        if (lumpCount <= 0 || lumpCount > MAX_LUMPS) {
            return false
        }
        val end = offset.toLong() + lumpCount.toLong() * DIRECTORY_ENTRY_BYTES
        return offset >= HEADER_BYTES && end <= size
    }

    /**
     * Collects every lump name, and returns a description of the first lump that reaches past the
     * end of the file — which is what a download that stopped halfway looks like from the inside.
     */
    private fun collectNames(
        bytes: ByteArray,
        directoryOffset: Int,
        lumpCount: Int,
        names: MutableSet<String>,
    ): String? {
        for (index in 0 until lumpCount) {
            val entry = directoryOffset + index * DIRECTORY_ENTRY_BYTES
            val position = readInt(bytes, entry)
            val size = readInt(bytes, entry + Int.SIZE_BYTES)
            if (position < 0 || size < 0 || position.toLong() + size > bytes.size) {
                return "lump $index claims $size bytes at $position"
            }
            names += readName(bytes, entry + 2 * Int.SIZE_BYTES)
        }
        return null
    }

    private fun identify(
        kind: WadKind,
        names: Set<String>,
        lumpCount: Int,
    ): WadInspection {
        val episodes = (1..EPISODE_LIMIT).count { "E${it}M1" in names }
        val maps = (1..MAP_LIMIT).count { "MAP${it.toString().padStart(2, '0')}" in names }
        val raven = RAVEN_LUMP in names
        val flavour =
            when {
                raven && maps > 0 -> GameFlavour.Hexen
                raven && episodes > 0 -> GameFlavour.Heretic
                maps > 0 -> GameFlavour.DoomMapped
                episodes > 0 -> GameFlavour.DoomEpisodic
                else -> null
            }

        return when {
            PALETTE_LUMP !in names -> {
                rejected(RejectionReason.NoPalette, "the file has no $PALETTE_LUMP lump")
            }

            flavour == null -> {
                rejected(RejectionReason.UnknownGame, "the file holds no maps this app can run")
            }

            else -> {
                WadInspection.Recognised(
                    WadIdentity(
                        kind = kind,
                        flavour = flavour,
                        lumpCount = lumpCount,
                        episodes = episodes,
                        maps = maps,
                    ),
                )
            }
        }
    }

    private fun rejected(
        reason: RejectionReason,
        detail: String,
    ): WadInspection = WadInspection.Rejected(reason, detail)

    /** WAD integers are little-endian and signed, as written by the tools of the time. */
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

    /** Lump names are eight bytes, uppercase, padded with zeros rather than terminated by one. */
    private fun readName(
        bytes: ByteArray,
        offset: Int,
    ): String {
        val end = (offset until offset + LUMP_NAME_BYTES).firstOrNull { bytes[it] == 0.toByte() }
        return bytes.decodeToString(offset, end ?: (offset + LUMP_NAME_BYTES)).uppercase()
    }
}
