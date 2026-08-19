package com.jacksonfdam.slipgate.host.gamedata

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Checks the inspector against a real file, which the synthetic fixtures cannot do: a genuine IWAD
 * has thousands of lumps, a real directory and its own quirks.
 *
 * No such file may live in this repository, so the test skips unless one is supplied:
 *
 * ```
 * ./gradlew :host:gamedata:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
 * ```
 */
class SuppliedIwadTest {
    private val iwad: File? =
        System
            .getenv("SLIPGATE_IWAD")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    @Test
    fun aSuppliedIwadIsRecognisedAsAGame() {
        val file = iwad ?: return run { println("skipping: set -Pslipgate.iwad to check a real file") }

        val identity = assertIs<WadInspection.Recognised>(WadInspector.inspect(file.readBytes())).identity

        assertEquals(WadKind.Iwad, identity.kind)
        assertEquals(
            true,
            identity.flavour in setOf(GameFlavour.DoomEpisodic, GameFlavour.DoomMapped),
            "a Doom IWAD was expected, got ${identity.flavour}",
        )
        assertEquals(true, identity.lumpCount > 100, "only ${identity.lumpCount} lumps")
    }

    /**
     * The accent the Doom gate asks for by number. A synthetic palette proves the arithmetic; only a
     * real one proves the number means what the gate thinks it means.
     */
    @Test
    fun theDoomAccentEntryIsRed() {
        val file = iwad ?: return run { println("skipping: set -Pslipgate.iwad to check a real file") }

        val palette = assertNotNull(paletteFrom(file.readBytes()), "the file carries no palette")
        val colour = palette[DOOM_ACCENT_ENTRY]
        val red = colour shr 16 and 0xFF
        val green = colour shr 8 and 0xFF
        val blue = colour and 0xFF

        assertEquals(true, red > 200, "entry $DOOM_ACCENT_ENTRY is not red: $red $green $blue")
        assertEquals(true, green < 80 && blue < 80, "entry $DOOM_ACCENT_ENTRY is not red: $red $green $blue")
    }
}

/** Doom's status-bar red, which is what `MarsGate` names as its accent. */
private const val DOOM_ACCENT_ENTRY = 176
