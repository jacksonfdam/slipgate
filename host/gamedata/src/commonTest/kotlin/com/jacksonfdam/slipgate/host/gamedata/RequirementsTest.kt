package com.jacksonfdam.slipgate.host.gamedata

import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataRequirements
import com.jacksonfdam.slipgate.host.runtime.DataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val NEEDS_AN_IWAD =
    DataRequirements(
        entries =
            listOf(
                DataEntry(
                    key = "doom.wad",
                    displayName = "Doom IWAD",
                    sources = listOf(DataSource.UserSupplied),
                ),
            ),
    )

private val WANTS_VOICES =
    DataRequirements(
        entries =
            listOf(
                DataEntry(
                    key = "strife1.wad",
                    displayName = "Strife IWAD",
                    sources = listOf(DataSource.UserSupplied),
                ),
                DataEntry(
                    key = "voices.wad",
                    displayName = "Strife voices",
                    sources = listOf(DataSource.UserSupplied),
                    optional = true,
                ),
            ),
    )

class RequirementsTest {
    @Test
    fun anEmptyShelfLeavesEverythingOutstanding() {
        assertEquals(listOf("doom.wad"), NEEDS_AN_IWAD.unmet(emptySet()).map { it.key })
    }

    @Test
    fun theStoredFileSatisfiesTheRequirement() {
        assertTrue(NEEDS_AN_IWAD.unmet(setOf("doom.wad")).isEmpty())
    }

    @Test
    fun anotherFileDoesNotStandInForTheOneNeeded() {
        assertEquals(1, NEEDS_AN_IWAD.unmet(setOf("freedoom1.wad")).size)
    }

    @Test
    fun aGateThatNeedsNothingIsAlwaysSatisfied() {
        assertTrue(DataRequirements(entries = emptyList()).unmet(emptySet()).isEmpty())
    }

    /** Strife's voices: the game runs subtitled without them, so their absence holds nothing up. */
    @Test
    fun anOptionalFileNeverHoldsAGateShut() {
        assertTrue(WANTS_VOICES.unmet(setOf("strife1.wad")).isEmpty())
    }

    @Test
    fun anOptionalFileIsStillOfferedWhileItIsMissing() {
        assertEquals(listOf("voices.wad"), WANTS_VOICES.absent(setOf("strife1.wad")).map { it.key })
    }

    @Test
    fun aRequiredFileIsBothUnmetAndAbsent() {
        assertEquals(listOf("strife1.wad"), WANTS_VOICES.unmet(emptySet()).map { it.key })
        assertEquals(listOf("strife1.wad", "voices.wad"), WANTS_VOICES.absent(emptySet()).map { it.key })
    }

    @Test
    fun nothingIsAbsentOnceEverythingIsStored() {
        assertTrue(WANTS_VOICES.absent(setOf("strife1.wad", "voices.wad")).isEmpty())
    }

    @Test
    fun aStoreAnswersForItsOwnGate() =
        runTest {
            val store = InMemoryGameDataStore()
            store.write("serpent", "doom.wad", byteArrayOf(1))

            assertFalse(store.satisfies("mars", NEEDS_AN_IWAD), "another gate's file counted")

            store.write("mars", "doom.wad", byteArrayOf(1))
            assertTrue(store.satisfies("mars", NEEDS_AN_IWAD))
        }
}
