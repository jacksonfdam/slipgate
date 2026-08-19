package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredSavesTest {
    private val store = InMemoryGameDataStore()
    private val saves = StoredSaves(store, "mars")

    @Test
    fun whatWasSavedComesBack() =
        runTest {
            saves.write("slot0", "doomsav0.dsg", byteArrayOf(1, 2, 3))

            assertContentEquals(byteArrayOf(1, 2, 3), saves.read("slot0", "doomsav0.dsg"))
            assertEquals(listOf("slot0"), saves.slots())
            assertEquals(listOf("doomsav0.dsg"), saves.files("slot0"))
        }

    @Test
    fun anEmptySlotReadsAsNothingRatherThanFailing() =
        runTest {
            assertNull(saves.read("slot0", "doomsav0.dsg"))
            assertEquals(emptyList(), saves.slots())
        }

    @Test
    fun slotsDoNotSeeEachOthersFiles() =
        runTest {
            saves.write("slot0", "map.dsg", byteArrayOf(1))
            saves.write("slot1", "map.dsg", byteArrayOf(2))

            assertEquals(listOf("slot0", "slot1"), saves.slots())
            assertContentEquals(byteArrayOf(1), saves.read("slot0", "map.dsg"))
            assertContentEquals(byteArrayOf(2), saves.read("slot1", "map.dsg"))
        }

    @Test
    fun aHubSaveKeepsEveryFileInOneSlot() =
        runTest {
            saves.write("slot0", "hex1.hxs", byteArrayOf(1))
            saves.write("slot0", "hex2.hxs", byteArrayOf(2))

            assertEquals(listOf("hex1.hxs", "hex2.hxs"), saves.files("slot0"))
        }

    @Test
    fun deletingASlotTakesEveryFileInIt() =
        runTest {
            saves.write("slot0", "one.dsg", byteArrayOf(1))
            saves.write("slot0", "two.dsg", byteArrayOf(2))
            saves.write("slot1", "three.dsg", byteArrayOf(3))

            saves.delete("slot0")

            assertEquals(listOf("slot1"), saves.slots())
            assertEquals(emptyList(), saves.files("slot0"))
        }

    @Test
    fun deletingOneFileLeavesTheSlot() =
        runTest {
            saves.write("slot0", "one.dsg", byteArrayOf(1))
            saves.write("slot0", "two.dsg", byteArrayOf(2))

            saves.delete("slot0", "one.dsg")

            assertEquals(listOf("two.dsg"), saves.files("slot0"))
        }

    @Test
    fun savesSurviveTheGameDataBeingCleared() =
        runTest {
            store.write("mars", "freedoom1.wad", byteArrayOf(9))
            saves.write("slot0", "one.dsg", byteArrayOf(1))

            store.delete("mars")

            assertEquals(listOf("slot0"), saves.slots())
        }

    @Test
    fun aSlotNameCarryingTheSeparatorStillSplitsBack() =
        runTest {
            saves.write("odd__slot", "one.dsg", byteArrayOf(1))

            assertEquals(listOf("odd_slot"), saves.slots())
            assertEquals(listOf("one.dsg"), saves.files("odd__slot"))
        }
}
