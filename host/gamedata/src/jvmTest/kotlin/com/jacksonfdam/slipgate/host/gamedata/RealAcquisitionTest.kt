package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Runs the whole route a player takes when they accept the free replacement: download an archive,
 * unpack the IWAD, inspect it, store it. Every piece is real — the network, the compressor, the file.
 *
 * It reaches the network, so it never runs unless both a URL and an entry are supplied:
 *
 * ```
 * ./gradlew :host:gamedata:jvmTest \
 *   -Pslipgate.downloadUrl=https://example.org/freedoom.zip -Pslipgate.archiveEntry=freedoom1.wad
 * ```
 *
 * Whatever the inspector recognises is accepted, rather than only Doom: every gate's free replacement
 * takes this same route, and the point of the harness is the route rather than the game at the end of
 * it. What the file turned out to be is printed, because that is the interesting half of the result.
 */
class RealAcquisitionTest {
    private val url: String? = System.getenv("SLIPGATE_DOWNLOAD_URL")?.takeIf { it.isNotBlank() }
    private val entry: String? = System.getenv("SLIPGATE_ARCHIVE_ENTRY")?.takeIf { it.isNotBlank() }

    @Test
    fun aFreeReplacementArrivesAsSomethingAGateCouldBoot() =
        runTest {
            val address = url
            val wanted = entry
            if (address == null || wanted == null) {
                println("skipping: set -Pslipgate.downloadUrl and -Pslipgate.archiveEntry")
                return@runTest
            }
            val store = InMemoryGameDataStore()
            var lastReceived = 0L

            val result =
                GameDataAcquisition(store).acquire(
                    AcquisitionRequest(
                        gate = "mars",
                        name = "doom.wad",
                        url = address,
                        accepts = GameFlavour.entries.toSet(),
                        archiveEntry = wanted,
                    ),
                ) { received, _ -> lastReceived = received }

            val stored = assertIs<AcquisitionResult.Stored>(result)
            println("acquired ${stored.identity.flavour}: ${stored.identity}")
            assertEquals(WadKind.Iwad, stored.identity.kind)
            assertTrue(
                stored.identity.episodes > 0 || stored.identity.maps > 0,
                "no episodes and no maps in ${stored.identity}",
            )
            assertTrue(lastReceived > 0, "no progress was reported")
            assertTrue(store.size("mars", "doom.wad") > 1_000_000, "the stored file is suspiciously small")
        }
}
