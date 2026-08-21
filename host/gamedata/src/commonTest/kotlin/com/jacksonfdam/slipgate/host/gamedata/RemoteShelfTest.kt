package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteShelfTest {
    @Test
    fun `follows a beacon to the shelf it points at`() =
        runTest {
            val shelf =
                RemoteShelf(
                    FakeDownload(
                        BEACON to
                            """
                            slipgate-beacon 1
                            url${'\t'}https://tunnel.example
                            key${'\t'}abc123
                            updated${'\t'}2026-08-20T18:22:41Z
                            """.trimIndent(),
                        "https://tunnel.example/shelf.index?key=abc123" to INDEX,
                    ),
                )

            val listing = shelf.open(BEACON)

            assertTrue(listing is ShelfListing.Open, "expected the beacon to be followed, got $listing")
            assertEquals("https://tunnel.example", listing.base)
            assertEquals("abc123", listing.key)
            assertEquals("2026-08-20T18:22:41Z", listing.publishedAt)
            assertEquals(listOf("doom.wad", "sunlust.wad", "hexdd.wad"), listing.files.map { it.name })
        }

    @Test
    fun `reads a shelf asked for directly with the key from the address`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local:8099/shelf.index?key=abc123" to INDEX))

            val listing = shelf.open("https://nas.local:8099/?key=abc123")

            assertTrue(listing is ShelfListing.Open, "expected the shelf to answer, got $listing")
            assertEquals("https://nas.local:8099", listing.base)
            assertEquals("abc123", listing.key)
        }

    @Test
    fun `reads a shelf whose address already names the index`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local:8099/shelf.index" to INDEX))

            val listing = shelf.open("https://nas.local:8099/shelf.index")

            assertTrue(listing is ShelfListing.Open, "expected the index to be read, got $listing")
            assertEquals("https://nas.local:8099", listing.base)
            assertNull(listing.key)
        }

    @Test
    fun `separates what a gate can boot from the maps loaded over it`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local/shelf.index" to INDEX))

            val listing = shelf.open("https://nas.local") as ShelfListing.Open

            assertEquals(listOf("doom.wad"), listing.bootable("mars").map { it.name })
            assertEquals(listOf("sunlust.wad", "hexdd.wad"), listing.addOns("mars").map { it.name })
            assertEquals(emptyList(), listing.bootable("korax"))
        }

    @Test
    fun `offers a map pack the shelf sorted for a gate to that gate`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local/shelf.index" to INDEX))

            val listing = shelf.open("https://nas.local") as ShelfListing.Open

            assertEquals(listOf("sunlust.wad", "hexdd.wad"), listing.addOns("mars").map { it.name })
            assertEquals(listOf("hexdd.wad"), listing.addOns("korax").map { it.name })
        }

    @Test
    fun `carries the key into every file it offers`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local/shelf.index?key=abc123" to INDEX))

            val listing = shelf.open("https://nas.local?key=abc123") as ShelfListing.Open

            assertEquals(
                "https://nas.local/mars/doom.wad?key=abc123",
                listing.urlFor(listing.bootable("mars").single()),
            )
        }

    @Test
    fun `says what went wrong when nothing answers`() =
        runTest {
            val listing = RemoteShelf(FakeDownload()).open("https://nas.local")

            assertTrue(listing is ShelfListing.Unreachable, "expected an unreachable shelf, got $listing")
            assertTrue(listing.message.isNotEmpty())
        }

    @Test
    fun `refuses an address that answers with something else`() =
        runTest {
            val shelf = RemoteShelf(FakeDownload("https://nas.local/shelf.index" to "<html>hello</html>"))

            val listing = shelf.open("https://nas.local")

            assertTrue(listing is ShelfListing.Unreachable, "expected a refusal, got $listing")
            assertTrue(listing.message.contains("not with a shelf index"))
        }

    @Test
    fun `keeps the entries of an index that lost a line and drops the line`() {
        val files =
            parseIndex(
                """
                slipgate-shelf 1
                file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}14604584${'\t'}/mars/doom.wad
                file${'\t'}mars${'\t'}truncated
                note${'\t'}something a later version added
                """.trimIndent(),
            )

        assertEquals(listOf("doom.wad"), files?.map { it.name })
    }

    @Test
    fun `is not a pointer when the header is missing`() {
        assertNull(parsePointer("url${'\t'}https://tunnel.example"))
        assertNull(parseIndex("file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}1${'\t'}/mars/doom.wad"))
    }

    @Test
    fun `is not a pointer without somewhere to point`() {
        assertNull(parsePointer("$POINTER_HEADER\nkey${'\t'}abc123"))
    }

    private companion object {
        const val BEACON = "https://slipgate.example/beacon/deadbeefdeadbeefdeadbeef"

        val INDEX =
            """
            slipgate-shelf 1
            file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}14604584${'\t'}/mars/doom.wad
            file${'\t'}mars${'\t'}sunlust.wad${'\t'}addon${'\t'}18324${'\t'}/addons/mars/sunlust.wad
            file${'\t'}${'\t'}hexdd.wad${'\t'}addon${'\t'}4440584${'\t'}/addons/hexdd.wad
            """.trimIndent()
    }
}

/** Answers the URLs it was given and refuses everything else, so a test needs no server. */
private class FakeDownload(
    vararg answers: Pair<String, String>,
) : DataDownload {
    private val answers = answers.toMap()

    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray {
        val body = answers[url] ?: throw DataDownloadException("nothing is serving $url")
        val bytes = body.encodeToByteArray()
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return bytes
    }
}
