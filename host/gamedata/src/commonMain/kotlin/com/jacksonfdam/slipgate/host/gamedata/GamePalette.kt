package com.jacksonfdam.slipgate.host.gamedata

private const val PALETTE_LUMP = "PLAYPAL"
private const val PALETTE_ENTRIES = 256
private const val ENTRY_BYTES = 3
private const val BYTE_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = 0xFF

/** The bytes of a palette as it is stored: 256 opaque colours, in the game's own order. */
public const val PALETTE_SIDECAR: String = "palette.bin"

/** How many bytes a stored palette occupies, which is what makes it worth keeping beside the data. */
public const val PALETTE_BYTES: Int = PALETTE_ENTRIES * ENTRY_BYTES

/**
 * The base palette out of a game's own data, as 256 opaque `0xAARRGGBB` colours.
 *
 * `PLAYPAL` holds fourteen palettes — the base one and the ones the game fades to when the player is
 * hurt or picks something up. Only the first is of any use to a menu.
 */
public fun paletteFrom(wad: ByteArray): IntArray? = readWadLump(wad, PALETTE_LUMP)?.let(::paletteOf)

/** Turns stored palette bytes back into colours. */
public fun paletteOf(bytes: ByteArray): IntArray? = if (bytes.size < PALETTE_BYTES) null else colours(bytes)

private fun colours(bytes: ByteArray): IntArray =
    IntArray(PALETTE_ENTRIES) { entry ->
        val offset = entry * ENTRY_BYTES
        val red = bytes[offset].toInt() and BYTE_MASK
        val green = bytes[offset + 1].toInt() and BYTE_MASK
        val blue = bytes[offset + 2].toInt() and BYTE_MASK
        (OPAQUE shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

/**
 * The palette a gate's launcher card and background are themed with, or null when the gate has no
 * data yet.
 *
 * Kept beside the game data as a few hundred bytes rather than read out of the IWAD each time: the
 * launcher changes its theme as the selection moves, and reading thirty megabytes to find seven
 * hundred and sixty-eight would be felt on a phone. A gate whose data was installed before the
 * sidecar existed gets one written the first time it is asked for.
 */
public suspend fun storedPalette(
    store: GameDataStore,
    gate: String,
): IntArray? {
    val names = store.names(gate)
    if (PALETTE_SIDECAR in names) {
        return paletteOf(store.read(gate, PALETTE_SIDECAR))
    }
    return extractPalette(store, gate, names)
}

private suspend fun extractPalette(
    store: GameDataStore,
    gate: String,
    names: Set<String>,
): IntArray? {
    val source = names.firstOrNull { name -> name != PALETTE_SIDECAR } ?: return null
    val palette = paletteFrom(store.read(gate, source))
    if (palette != null) {
        store.write(gate, PALETTE_SIDECAR, paletteBytes(palette))
    }
    return palette
}

/** Packs colours back into the three bytes an entry each that the game itself uses. */
public fun paletteBytes(palette: IntArray): ByteArray {
    val bytes = ByteArray(PALETTE_BYTES)
    for (entry in 0 until minOf(PALETTE_ENTRIES, palette.size)) {
        val colour = palette[entry]
        val offset = entry * ENTRY_BYTES
        bytes[offset] = (colour shr RED_SHIFT and BYTE_MASK).toByte()
        bytes[offset + 1] = (colour shr GREEN_SHIFT and BYTE_MASK).toByte()
        bytes[offset + 2] = (colour and BYTE_MASK).toByte()
    }
    return bytes
}
