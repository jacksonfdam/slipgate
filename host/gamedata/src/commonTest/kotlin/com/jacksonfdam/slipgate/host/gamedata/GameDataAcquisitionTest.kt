package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val DOOM = setOf(GameFlavour.DoomEpisodic, GameFlavour.DoomMapped)

private fun request(
    archiveEntry: String? = null,
    gate: String = "mars",
) = AcquisitionRequest(
    gate = gate,
    name = "doom.wad",
    url = "https://example.invalid/data",
    accepts = DOOM,
    archiveEntry = archiveEntry,
)

class GameDataAcquisitionTest {
    private val store = InMemoryGameDataStore()

    @Test
    fun aDownloadedIwadIsStored() =
        runTest {
            val bytes = syntheticWad("IWAD", listOf("PLAYPAL", "E1M1"))
            val acquisition = GameDataAcquisition(store, download = ServesOnce(bytes))

            val result = acquisition.acquire(request())

            assertEquals(GameFlavour.DoomEpisodic, assertIs<AcquisitionResult.Stored>(result).identity.flavour)
            assertContentEquals(bytes, store.read("mars", "doom.wad"))
        }

    @Test
    fun progressIsReportedWhileDownloading() =
        runTest {
            val bytes = syntheticWad("IWAD", listOf("PLAYPAL", "MAP01"))
            val acquisition = GameDataAcquisition(store, download = ServesOnce(bytes))
            val seen = mutableListOf<Pair<Long, Long?>>()

            acquisition.acquire(request()) { received, total ->
                seen += received to total
            }

            assertEquals(bytes.size.toLong() to bytes.size.toLong(), seen.last())
        }

    /** Nothing unvalidated reaches the store, which is what keeps a shelf bootable. */
    @Test
    fun somethingThatIsNotAWadIsNeverStored() =
        runTest {
            val acquisition =
                GameDataAcquisition(store, download = ServesOnce("a photograph".encodeToByteArray()))

            val result = acquisition.acquire(request())

            assertEquals(RejectionReason.NotAWad, assertIs<AcquisitionResult.Refused>(result).inspection.reason)
            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun aPatchIsRefusedBecauseAGateCannotBootFromOne() =
        runTest {
            val acquisition =
                GameDataAcquisition(store, download = ServesOnce(syntheticWad("PWAD", listOf("PLAYPAL", "MAP01"))))

            val result = acquisition.acquire(request())

            assertTrue("patch" in assertIs<AcquisitionResult.Failed>(result).message)
            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun anotherGamesDataIsRefusedWithBothNames() =
        runTest {
            val hexen = syntheticWad("IWAD", listOf("PLAYPAL", "TINTTAB", "MAP01"))

            val result =
                GameDataAcquisition(store, download = ServesOnce(hexen))
                    .acquire(request())

            val message = assertIs<AcquisitionResult.Failed>(result).message
            assertTrue("Hexen" in message, message)
            assertTrue("Doom" in message, message)
        }

    @Test
    fun aDownloadThatFailsSaysSoAndStoresNothing() =
        runTest {
            val acquisition = GameDataAcquisition(store, download = Fails("the server answered 404"))

            val result = acquisition.acquire(request())

            assertEquals("the server answered 404", assertIs<AcquisitionResult.Failed>(result).message)
            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun anIwadInsideAnArchiveIsUnpackedAndStored() =
        runTest {
            val iwad = syntheticWad("IWAD", listOf("PLAYPAL", "E1M1"))
            val archive = syntheticZip(listOf("freedoom-0.13.0/freedoom1.wad" to iwad))
            val acquisition = GameDataAcquisition(store, download = ServesOnce(archive))

            val result = acquisition.acquire(request(archiveEntry = "freedoom1.wad"))

            assertIs<AcquisitionResult.Stored>(result)
            assertContentEquals(iwad, store.read("mars", "doom.wad"))
        }

    @Test
    fun anArchiveWithoutTheWantedFileSaysSo() =
        runTest {
            val archive = syntheticZip(listOf("readme.txt" to "nothing here".encodeToByteArray()))
            val acquisition = GameDataAcquisition(store, download = ServesOnce(archive))

            val result = acquisition.acquire(request(archiveEntry = "freedoom1.wad"))

            assertTrue("freedoom1.wad" in assertIs<AcquisitionResult.Failed>(result).message)
            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun somethingThatIsNotAnArchiveIsReportedAsSuch() =
        runTest {
            val acquisition =
                GameDataAcquisition(store, download = ServesOnce("not a zip at all".encodeToByteArray()))

            val result = acquisition.acquire(request(archiveEntry = "freedoom1.wad"))

            assertIs<AcquisitionResult.Failed>(result)
            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun aSuppliedFileTakesTheSamePath() =
        runTest {
            val acquisition = GameDataAcquisition(store, download = Fails("no download should happen"))

            val result =
                acquisition.install("mars", "doom.wad", syntheticWad("IWAD", listOf("PLAYPAL", "E1M1")), DOOM)

            assertIs<AcquisitionResult.Stored>(result)
            assertEquals(setOf("doom.wad"), store.names("mars"))
        }
}

private class ServesOnce(
    private val bytes: ByteArray,
) : DataDownload {
    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray {
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return bytes
    }
}

private class Fails(
    private val why: String,
) : DataDownload {
    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray = throw DataDownloadException(why)
}
