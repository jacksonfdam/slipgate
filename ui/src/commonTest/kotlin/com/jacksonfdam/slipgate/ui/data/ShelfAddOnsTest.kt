package com.jacksonfdam.slipgate.ui.data

import com.jacksonfdam.slipgate.host.gamedata.DataDownload
import com.jacksonfdam.slipgate.host.gamedata.DataDownloadException
import com.jacksonfdam.slipgate.host.gamedata.DownloadProgress
import com.jacksonfdam.slipgate.host.gamedata.RemoteShelf
import com.jacksonfdam.slipgate.host.gamedata.ShelfFile
import com.jacksonfdam.slipgate.host.gamedata.WadRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the controller offers a gate from the player's own shelf.
 *
 * The map packs were always in the index and never read: Settings went straight to the file picker
 * while a connected shelf was listing hundreds of them. These pin both halves of the listing being
 * reachable, and that fetching one goes to the address the shelf gave with the key it asked for.
 */
class ShelfAddOnsTest {
    @Test
    fun `offers the maps a shelf filed under a gate, apart from what boots it`() =
        runTest {
            val controller = RemoteShelfController(RemoteShelf(FakeDownload(INDEX_URL to INDEX)))
            controller.refresh(SHELF)

            assertEquals(listOf("doom.wad"), controller.bootable("mars").map { it.name })
            assertEquals(listOf("av.wad", "sunlust.wad"), controller.addOns("mars").map { it.name })
        }

    @Test
    fun `offers nothing for a gate the shelf holds no maps for`() =
        runTest {
            val controller = RemoteShelfController(RemoteShelf(FakeDownload(INDEX_URL to INDEX)))
            controller.refresh(SHELF)

            assertEquals(emptyList(), controller.addOns("korax"))
        }

    @Test
    fun `offers nothing at all until a shelf has answered`() =
        runTest {
            val controller = RemoteShelfController(RemoteShelf(FakeDownload()))

            assertEquals(emptyList(), controller.addOns("mars"))
            assertNull(controller.fetch(aFile()))
        }

    @Test
    fun `fetches a map pack from the address the shelf gave it`() =
        runTest {
            val pack = "PWAD-ish bytes"
            val controller =
                RemoteShelfController(
                    RemoteShelf(
                        FakeDownload(
                            INDEX_URL to INDEX,
                            "$SHELF/addons/mars/sunlust.wad" to pack,
                        ),
                    ),
                )
            controller.refresh(SHELF)
            val file = controller.addOns("mars").single { it.name == "sunlust.wad" }

            val bytes = controller.fetch(file)

            assertEquals(pack, bytes?.decodeToString())
        }

    @Test
    fun `lets a shelf that stopped answering fail on the screen that asked`() =
        runTest {
            // The index arrives, the file does not: a NAS turned off between opening Settings and
            // tapping a pack is the ordinary case, and the sentence belongs where the tap was.
            val controller = RemoteShelfController(RemoteShelf(FakeDownload(INDEX_URL to INDEX)))
            controller.refresh(SHELF)
            val file = controller.addOns("mars").first()

            val failure =
                try {
                    controller.fetch(file)
                    null
                } catch (refused: DataDownloadException) {
                    refused
                }

            assertTrue(failure != null, "expected the fetch to fail rather than answer")
        }

    private fun aFile() =
        ShelfFile(
            gate = "mars",
            name = "sunlust.wad",
            role = WadRole.AddOn,
            size = 1,
            path = "/addons/mars/sunlust.wad",
        )

    private companion object {
        const val SHELF = "https://nas.local:8600"
        const val INDEX_URL = "$SHELF/shelf.index"

        val INDEX =
            """
            slipgate-shelf 1
            file${'\t'}mars${'\t'}doom.wad${'\t'}game${'\t'}14604584${'\t'}/mars/doom.wad
            file${'\t'}mars${'\t'}av.wad${'\t'}addon${'\t'}2048${'\t'}/addons/mars/av.wad
            file${'\t'}mars${'\t'}sunlust.wad${'\t'}addon${'\t'}18324${'\t'}/addons/mars/sunlust.wad
            file${'\t'}corvus${'\t'}wizard.wad${'\t'}addon${'\t'}4096${'\t'}/addons/corvus/wizard.wad
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
