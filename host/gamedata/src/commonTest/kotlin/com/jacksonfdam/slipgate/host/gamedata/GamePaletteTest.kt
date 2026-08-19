package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Doom's own status-bar red sits here, which is why a gate asks for this entry by number. */
private const val DOOM_RED_ENTRY = 176

private fun playpal(): ByteArray {
    val bytes = ByteArray(PALETTE_BYTES)
    for (entry in 0 until 256) {
        val offset = entry * 3
        bytes[offset] = entry.toByte()
        bytes[offset + 1] = (255 - entry).toByte()
        bytes[offset + 2] = 0x40
    }
    return bytes
}

class GamePaletteTest {
    @Test
    fun aPaletteIsReadOutOfAWad() {
        val wad = wadWith("PLAYPAL" to playpal())

        val palette = assertNotNull(paletteFrom(wad))
        assertEquals(256, palette.size)
        assertTrue(palette.all { colour -> colour ushr 24 == 0xFF }, "entries must be opaque")
    }

    @Test
    fun anEntryKeepsItsColour() {
        val palette = assertNotNull(paletteFrom(wadWith("PLAYPAL" to playpal())))

        val expected =
            (0xFF shl 24) or (DOOM_RED_ENTRY shl 16) or ((255 - DOOM_RED_ENTRY) shl 8) or 0x40
        assertEquals(expected, palette[DOOM_RED_ENTRY])
    }

    @Test
    fun aWadWithoutAPaletteHasNone() {
        assertNull(paletteFrom(wadWith("E1M1" to byteArrayOf(1, 2, 3))))
    }

    @Test
    fun somethingThatIsNotAWadHasNone() {
        assertNull(paletteFrom("a photograph".encodeToByteArray()))
    }

    @Test
    fun aTruncatedPaletteIsRefused() {
        assertNull(paletteFrom(wadWith("PLAYPAL" to ByteArray(100))))
    }

    @Test
    fun packingAndReadingAPaletteRoundTrips() {
        val palette = assertNotNull(paletteFrom(wadWith("PLAYPAL" to playpal())))

        assertEquals(palette.toList(), assertNotNull(paletteOf(paletteBytes(palette))).toList())
    }

    /** The whole point of the sidecar: a launcher must not read a whole IWAD to theme a card. */
    @Test
    fun theFirstReadWritesASidecarBesideTheData() =
        runTest {
            val store = InMemoryGameDataStore()
            store.write("mars", "doom.wad", wadWith("PLAYPAL" to playpal()))

            val palette = assertNotNull(storedPalette(store, "mars"))

            assertTrue(PALETTE_SIDECAR in store.names("mars"), "no sidecar was written")
            assertEquals(PALETTE_BYTES.toLong(), store.size("mars", PALETTE_SIDECAR))
            assertEquals(palette.toList(), assertNotNull(storedPalette(store, "mars")).toList())
        }

    @Test
    fun aGateWithNoDataHasNoPalette() =
        runTest {
            assertNull(storedPalette(InMemoryGameDataStore(), "mars"))
        }

    @Test
    fun dataWithoutAPaletteWritesNoSidecar() =
        runTest {
            val store = InMemoryGameDataStore()
            store.write("mars", "doom.wad", wadWith("E1M1" to byteArrayOf(1)))

            assertNull(storedPalette(store, "mars"))
            assertTrue(PALETTE_SIDECAR !in store.names("mars"))
        }
}

/** A WAD carrying whatever lumps a test needs, with real contents rather than placeholders. */
private fun wadWith(vararg lumps: Pair<String, ByteArray>): ByteArray {
    val headerBytes = 12
    val entryBytes = 16
    val contents = lumps.sumOf { (_, bytes) -> bytes.size }
    val directoryOffset = headerBytes + contents
    val wad = ByteArray(directoryOffset + lumps.size * entryBytes)

    "IWAD".encodeToByteArray().copyInto(wad)
    writeInt(wad, 4, lumps.size)
    writeInt(wad, 8, directoryOffset)

    var position = headerBytes
    lumps.forEachIndexed { index, (name, bytes) ->
        bytes.copyInto(wad, position)
        val entry = directoryOffset + index * entryBytes
        writeInt(wad, entry, position)
        writeInt(wad, entry + 4, bytes.size)
        name.encodeToByteArray().copyInto(wad, entry + 8)
        position += bytes.size
    }
    return wad
}
