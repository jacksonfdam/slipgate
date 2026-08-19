package com.jacksonfdam.slipgate.host.gamedata

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
