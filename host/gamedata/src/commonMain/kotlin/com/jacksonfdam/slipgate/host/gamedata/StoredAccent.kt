package com.jacksonfdam.slipgate.host.gamedata

/**
 * Where a gate's palette is kept once it has been read: 768 bytes beside the game data.
 *
 * The launcher re-themes as the selection moves, and reading tens of megabytes of IWAD to find
 * three quarters of a kilobyte would be felt on a phone. A gate whose data was installed before
 * this existed gets a sidecar written the first time it is asked for, so nothing needs reinstalling.
 */
public const val PALETTE_SIDECAR: String = "palette.bin"

/**
 * The accent ramp for [gate], sampled from the game's own palette, or null when the gate has no
 * data yet — the caller keeps its neutral fallback in that case.
 */
public suspend fun storedAccent(
    store: GameDataStore,
    gate: String,
): AccentExtraction? {
    val names = store.names(gate)
    if (PALETTE_SIDECAR in names) {
        return PlaypalAccent.fromPalette(store.read(gate, PALETTE_SIDECAR))
    }
    return extractAndCache(store, gate, names)
}

private suspend fun extractAndCache(
    store: GameDataStore,
    gate: String,
    names: Set<String>,
): AccentExtraction? {
    val source = names.firstOrNull { name -> name != PALETTE_SIDECAR } ?: return null
    val palette = WadPaletteReader.readPlaypal(store.read(gate, source))
    if (palette != null) {
        store.write(gate, PALETTE_SIDECAR, palette)
    }
    return palette?.let(PlaypalAccent::fromPalette)
}
