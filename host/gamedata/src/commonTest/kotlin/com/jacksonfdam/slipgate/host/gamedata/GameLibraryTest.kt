package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameLibraryTest {
    @Test
    fun `follows a beacon to the library it points at`() =
        runTest {
            val library =
                GameLibrary(
                    FakeDownload(
                        BEACON to
                            """
                            slipgate-beacon 1
                            url${'\t'}https://tunnel.example
                            key${'\t'}abc123
                            updated${'\t'}2026-08-20T18:22:41Z
                            """.trimIndent(),
                        "https://tunnel.example/manifest?key=abc123" to MANIFEST,
                    ),
                )

            val listing = library.open(BEACON)

            assertTrue(listing is LibraryListing.Open, "expected the beacon to be followed, got $listing")
            assertEquals("https://tunnel.example", listing.base)
            assertEquals("abc123", listing.key)
            assertEquals("2026-08-20T18:22:41Z", listing.publishedAt)
            assertEquals(listOf("doom.wad", "sunlust.wad"), listing.files.map { it.name })
        }

    @Test
    fun `reads a library asked for directly, with the key from the address`() =
        runTest {
            val library = GameLibrary(FakeDownload("https://nas.local:8099/manifest?key=abc123" to MANIFEST))

            val listing = library.open("https://nas.local:8099/?key=abc123")

            assertTrue(listing is LibraryListing.Open, "expected the library to answer, got $listing")
            assertEquals("https://nas.local:8099", listing.base)
            assertEquals("abc123", listing.key)
        }

    @Test
    fun `reads a library whose address already names the manifest`() =
        runTest {
            val library = GameLibrary(FakeDownload("https://nas.local:8099/manifest" to MANIFEST))

            val listing = library.open("https://nas.local:8099/manifest")

            assertTrue(listing is LibraryListing.Open, "expected the manifest to be read, got $listing")
            assertEquals("https://nas.local:8099", listing.base)
            assertNull(listing.key)
        }

    @Test
    fun `separates what a gate can boot from the maps loaded over it`() =
        runTest {
            val library = GameLibrary(FakeDownload("https://nas.local/manifest" to MANIFEST))

            val listing = library.open("https://nas.local") as LibraryListing.Open

            assertEquals(listOf("doom.wad"), listing.bootable("mars").map { it.name })
            assertEquals(listOf("sunlust.wad"), listing.addOns("mars").map { it.name })
            assertEquals(emptyList(), listing.bootable("korax"))
        }

    @Test
    fun `carries the key into every file it offers`() =
        runTest {
            val library = GameLibrary(FakeDownload("https://nas.local/manifest?key=abc123" to MANIFEST))

            val listing = library.open("https://nas.local?key=abc123") as LibraryListing.Open

            assertEquals(
                "https://nas.local/files/mars/doom.wad?key=abc123",
                listing.urlFor(listing.bootable("mars").single()),
            )
        }

    @Test
    fun `says what went wrong when nothing answers`() =
        runTest {
            val listing = GameLibrary(FakeDownload()).open("https://nas.local")

            assertTrue(listing is LibraryListing.Unreachable, "expected an unreachable library, got $listing")
            assertTrue(listing.message.isNotEmpty())
        }

    @Test
    fun `refuses an address that answers with something else`() =
        runTest {
            val library = GameLibrary(FakeDownload("https://nas.local/manifest" to "<html>hello</html>"))

            val listing = library.open("https://nas.local")

            assertTrue(listing is LibraryListing.Unreachable, "expected a refusal, got $listing")
            assertTrue(listing.message.contains("not with a library manifest"))
        }

    @Test
    fun `keeps the entries of a manifest that lost a line and drops the line`() {
        val files =
            parseManifest(
                """
                slipgate-library 1
                file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}14604584${'\t'}/files/mars/doom.wad
                file${'\t'}mars${'\t'}truncated
                note${'\t'}something a later version added
                """.trimIndent(),
            )

        assertEquals(listOf("doom.wad"), files?.map { it.name })
    }

    @Test
    fun `is not a pointer when the header is missing`() {
        assertNull(parsePointer("url${'\t'}https://tunnel.example"))
        assertNull(parseManifest("file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}1${'\t'}/files/mars/doom.wad"))
    }

    @Test
    fun `is not a pointer without somewhere to point`() {
        assertNull(parsePointer("$POINTER_HEADER\nkey${'\t'}abc123"))
    }

    private companion object {
        const val BEACON = "https://slipgate.example/beacon/deadbeefdeadbeefdeadbeef"

        val MANIFEST =
            """
            slipgate-library 1
            file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}14604584${'\t'}/files/mars/doom.wad
            file${'\t'}mars${'\t'}sunlust.wad${'\t'}addon${'\t'}18324${'\t'}/files/mars/addons/sunlust.wad
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
