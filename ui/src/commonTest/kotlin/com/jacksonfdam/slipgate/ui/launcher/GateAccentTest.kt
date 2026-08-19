package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.host.runtime.AccentSource
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DOOM_RED = 0xFFFF0000.toInt()

class GateAccentTest {
    @Test
    fun aFixedAccentIsUsedAsItIs() {
        assertEquals(DOOM_RED, accentArgbOf(AccentSource.Fixed(DOOM_RED), palette = null))
    }

    @Test
    fun aPaletteAccentComesFromTheGamesOwnPalette() {
        val palette = IntArray(256) { entry -> 0xFF000000.toInt() or entry }

        assertEquals(0xFF0000B0.toInt(), accentArgbOf(AccentSource.PaletteEntry(index = 176), palette))
    }

    /** A gate with nothing installed still has to draw, which is what the fallback is for. */
    @Test
    fun aPaletteAccentWithoutDataFallsBack() {
        assertEquals(
            FALLBACK_ACCENT_ARGB,
            accentArgbOf(AccentSource.PaletteEntry(index = 176), palette = null),
        )
    }

    @Test
    fun anEntryOutsideThePaletteFallsBack() {
        assertEquals(
            FALLBACK_ACCENT_ARGB,
            accentArgbOf(AccentSource.PaletteEntry(index = 999), palette = IntArray(256)),
        )
    }
}
