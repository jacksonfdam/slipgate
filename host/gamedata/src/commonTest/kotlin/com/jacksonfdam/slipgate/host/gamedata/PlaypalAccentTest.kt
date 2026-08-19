package com.jacksonfdam.slipgate.host.gamedata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaypalAccentTest {
    @Test
    fun picksTheMostSaturatedClusterAboveTheLuminanceFloor() {
        // Mostly greys, a strong red block, a small dim blue block.
        val palette = ByteArray(768)
        for (entry in 0 until 200) fill(palette, entry, 90, 90, 90)
        for (entry in 200 until 240) fill(palette, entry, 200, 24, 16)
        for (entry in 240 until 256) fill(palette, entry, 10, 10, 40)

        val accent = assertNotNull(PlaypalAccent.fromPalette(palette))
        val red = accent.baseArgb shr 16 and 0xFF
        val green = accent.baseArgb shr 8 and 0xFF
        val blue = accent.baseArgb and 0xFF
        assertTrue(red > green && red > blue, "expected a red accent, got ${accent.baseArgb.toString(16)}")
    }

    @Test
    fun rampIsOrderedDimToHot() {
        val palette = ByteArray(768)
        for (entry in 0 until 256) fill(palette, entry, 40, 180, 90)

        val accent = assertNotNull(PlaypalAccent.fromPalette(palette))
        val dimGreen = accent.dimArgb shr 8 and 0xFF
        val baseGreen = accent.baseArgb shr 8 and 0xFF
        val hotGreen = accent.hotArgb shr 8 and 0xFF
        assertTrue(dimGreen < baseGreen && baseGreen < hotGreen)
    }

    @Test
    fun anAllGreyPaletteYieldsNothing() {
        val palette = ByteArray(768)
        for (entry in 0 until 256) fill(palette, entry, 120, 120, 120)

        assertNull(PlaypalAccent.fromPalette(palette))
    }

    @Test
    fun readsThePaletteOutOfAWad() {
        val palette = ByteArray(768)
        for (entry in 0 until 256) fill(palette, entry, 220, 140, 20)
        val wad = wadWithPlaypal(palette)

        val fromWad = assertNotNull(PlaypalAccent.fromWad(wad))
        assertEquals(PlaypalAccent.fromPalette(palette), fromWad)
    }

    @Test
    fun aWadWithoutThePaletteYieldsNothing() {
        assertNull(PlaypalAccent.fromWad(syntheticWad("IWAD", listOf("E1M1", "TEXTURE1"))))
    }

    @Test
    fun garbageIsRejectedQuietly() {
        assertNull(PlaypalAccent.fromWad(ByteArray(3)))
        assertNull(PlaypalAccent.fromWad("not a wad at all".encodeToByteArray()))
    }

    private fun fill(
        palette: ByteArray,
        entry: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        palette[entry * 3] = red.toByte()
        palette[entry * 3 + 1] = green.toByte()
        palette[entry * 3 + 2] = blue.toByte()
    }

    private fun wadWithPlaypal(palette: ByteArray): ByteArray {
        val headerBytes = 12
        val directoryOffset = headerBytes + palette.size
        val bytes = ByteArray(directoryOffset + 16)
        "IWAD".encodeToByteArray().copyInto(bytes)
        writeInt(bytes, offset = 4, value = 1)
        writeInt(bytes, offset = 8, value = directoryOffset)
        palette.copyInto(bytes, destinationOffset = headerBytes)
        writeInt(bytes, directoryOffset, value = headerBytes)
        writeInt(bytes, directoryOffset + 4, value = palette.size)
        "PLAYPAL".encodeToByteArray().copyInto(bytes, destinationOffset = directoryOffset + 8)
        return bytes
    }
}
