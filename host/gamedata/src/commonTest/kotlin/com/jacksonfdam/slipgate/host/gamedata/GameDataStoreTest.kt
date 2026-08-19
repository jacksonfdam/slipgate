package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the contract against the in-memory store. The platform stores are the same contract over
 * a real file system, and the one on this platform is checked separately where a file system exists.
 */
class GameDataStoreTest {
    private val store = InMemoryGameDataStore()

    @Test
    fun whatWasStoredComesBack() =
        runTest {
            store.write("mars", "freedoom1.wad", byteArrayOf(1, 2, 3))

            assertContentEquals(byteArrayOf(1, 2, 3), store.read("mars", "freedoom1.wad"))
            assertEquals(3L, store.size("mars", "freedoom1.wad"))
            assertEquals(setOf("freedoom1.wad"), store.names("mars"))
        }

    @Test
    fun gatesDoNotSeeEachOthersFiles() =
        runTest {
            store.write("mars", "one.wad", byteArrayOf(1))
            store.write("serpent", "two.wad", byteArrayOf(2))

            assertEquals(setOf("one.wad"), store.names("mars"))
            assertEquals(setOf("two.wad"), store.names("serpent"))
        }

    @Test
    fun deletingOneFileLeavesTheRest() =
        runTest {
            store.write("mars", "one.wad", byteArrayOf(1))
            store.write("mars", "two.wad", byteArrayOf(2))

            store.delete("mars", "one.wad")

            assertEquals(setOf("two.wad"), store.names("mars"))
        }

    @Test
    fun deletingAGateClearsItsShelf() =
        runTest {
            store.write("mars", "one.wad", byteArrayOf(1))

            store.delete("mars")

            assertTrue(store.names("mars").isEmpty())
        }

    @Test
    fun readingSomethingAbsentSaysWhatWasMissing() =
        runTest {
            val failure = assertFailsWith<NoSuchElementException> { store.read("mars", "absent.wad") }

            assertTrue("absent.wad" in failure.message.orEmpty())
        }

    @Test
    fun aMountShowsOnlyWhatWasThereWhenItWasTaken() =
        runTest {
            store.write("mars", "one.wad", byteArrayOf(7))
            val mounted = store.mount("mars")
            store.write("mars", "two.wad", byteArrayOf(8))

            assertEquals(setOf("one.wad"), mounted.names())
            assertContentEquals(byteArrayOf(7), mounted.read("one.wad"))
            assertFailsWith<NoSuchElementException> { mounted.read("two.wad") }
        }
}
