package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Checks the same contract as the in-memory test, but over a real file system, where the parts that
 * can actually go wrong are: folders that do not exist yet, a partial file left behind, and a name
 * that tries to leave its folder.
 */
class FileGameDataStoreTest {
    private val root: File =
        File.createTempFile("slipgate", "store").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
    private val store = FileGameDataStore(root)

    @AfterTest
    fun tidy() {
        root.deleteRecursively()
    }

    @Test
    fun whatWasStoredComesBackFromDisk() =
        runTest {
            store.write("mars", "freedoom1.wad", byteArrayOf(1, 2, 3))

            assertContentEquals(byteArrayOf(1, 2, 3), store.read("mars", "freedoom1.wad"))
            assertEquals(3L, store.size("mars", "freedoom1.wad"))
            assertEquals(setOf("freedoom1.wad"), store.names("mars"))
        }

    /** A finished write leaves the file and nothing else; a half-written one must not be mistaken
     * for game data. */
    @Test
    fun noPartialFileSurvivesAFinishedWrite() =
        runTest {
            store.write("mars", "freedoom1.wad", ByteArray(1024))

            val leftovers = File(root, "mars").listFiles().orEmpty().map { it.name }
            assertEquals(listOf("freedoom1.wad"), leftovers)
        }

    @Test
    fun aNameThatTriesToEscapeIsStoredInsideTheFolder() =
        runTest {
            store.write("mars", "../../escaped.wad", byteArrayOf(9))

            val stored = File(root, "mars").listFiles().orEmpty().single()
            assertTrue(stored.name.endsWith("escaped.wad"), "stored as ${stored.name}")
            assertEquals(File(root, "mars"), stored.parentFile)
        }

    @Test
    fun aGateWithNothingStoredHasNoFiles() =
        runTest {
            assertTrue(store.names("mars").isEmpty())
            assertFailsWith<NoSuchElementException> { store.read("mars", "absent.wad") }
        }

    @Test
    fun deletingAGateRemovesItsFolder() =
        runTest {
            store.write("mars", "one.wad", byteArrayOf(1))

            store.delete("mars")

            assertTrue(!File(root, "mars").exists())
        }
}
