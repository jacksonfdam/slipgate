package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PALETTE_BYTES = 768
private const val TRIPLE = 3
private const val BYTE_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** A palette whose only saturated colours are red, so the extracted accent is unmistakable. */
private fun redPalette(): ByteArray {
    val bytes = ByteArray(PALETTE_BYTES)
    for (entry in 0 until PALETTE_BYTES / TRIPLE) {
        bytes[entry * TRIPLE] = entry.toByte()
    }
    return bytes
}

class StoredAccentTest {
    private val store = InMemoryGameDataStore()

    @Test
    fun theAccentComesFromTheGamesOwnPalette() =
        runTest {
            store.write("mars", "doom.wad", wadWithPalette(redPalette()))

            val accent = assertNotNull(storedAccent(store, "mars"))

            val red = accent.baseArgb shr RED_SHIFT and BYTE_MASK
            val green = accent.baseArgb shr GREEN_SHIFT and BYTE_MASK
            val blue = accent.baseArgb and BYTE_MASK
            assertTrue(red > green && red > blue, "the accent is not red: $red $green $blue")
        }

    /** The whole point of the sidecar: a second read must not touch the game data again. */
    @Test
    fun theFirstReadCachesThePaletteBesideTheData() =
        runTest {
            store.write("mars", "doom.wad", wadWithPalette(redPalette()))
            val first = assertNotNull(storedAccent(store, "mars"))

            assertTrue(PALETTE_SIDECAR in store.names("mars"), "no sidecar was written")
            assertEquals(PALETTE_BYTES.toLong(), store.size("mars", PALETTE_SIDECAR))

            store.delete("mars", "doom.wad")
            assertEquals(first, storedAccent(store, "mars"))
        }

    @Test
    fun aGateWithNoDataHasNoAccent() =
        runTest {
            assertNull(storedAccent(store, "mars"))
        }

    @Test
    fun dataWithoutAPaletteWritesNoSidecar() =
        runTest {
            store.write("mars", "doom.wad", "not a wad at all".encodeToByteArray())

            assertNull(storedAccent(store, "mars"))
            assertTrue(PALETTE_SIDECAR !in store.names("mars"))
        }

    @Test
    fun oneGatesPaletteDoesNotThemeAnother() =
        runTest {
            store.write("mars", "doom.wad", wadWithPalette(redPalette()))

            assertNotNull(storedAccent(store, "mars"))
            assertNull(storedAccent(store, "korax"))
        }
}

/** A WAD carrying one PLAYPAL lump, which is all the accent path reads. */
private fun wadWithPalette(palette: ByteArray): ByteArray {
    val headerBytes = 12
    val entryBytes = 16
    val wad = ByteArray(headerBytes + palette.size + entryBytes)

    "IWAD".encodeToByteArray().copyInto(wad)
    writeInt(wad, 4, 1)
    writeInt(wad, 8, headerBytes + palette.size)
    palette.copyInto(wad, headerBytes)

    val entry = headerBytes + palette.size
    writeInt(wad, entry, headerBytes)
    writeInt(wad, entry + 4, palette.size)
    "PLAYPAL".encodeToByteArray().copyInto(wad, entry + 8)
    return wad
}
