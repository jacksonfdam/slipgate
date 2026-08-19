package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.host.runtime.AccentSource
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.GateArtwork
import com.jacksonfdam.slipgate.host.runtime.GateDescriptor
import com.jacksonfdam.slipgate.host.runtime.GateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun card(
    id: String,
    availability: GateAvailability = GateAvailability.Installed,
) = GateCard(
    descriptor =
        GateDescriptor(
            id = GateId(id),
            title = id,
            engine = "Doom",
            artwork = GateArtwork(coverKey = "$id/cover"),
            accent = AccentSource.Fixed(argb = 0xFFFF0000.toInt()),
        ),
    availability = availability,
    accentArgb = 0xFFFF0000.toInt(),
)

private val NEEDED =
    DataEntry(key = "doom.wad", displayName = "Doom IWAD", sources = listOf(DataSource.UserSupplied))

class LauncherStateTest {
    private val rack = LauncherState(cards = listOf(card("mars"), card("corvus"), card("korax")))

    @Test
    fun theFirstCardIsSelectedToBeginWith() {
        assertEquals("mars", rack.current?.id)
    }

    @Test
    fun movingForwardWalksTheRack() {
        assertEquals("corvus", rack.next().current?.id)
        assertEquals(
            "korax",
            rack
                .next()
                .next()
                .current
                ?.id,
        )
    }

    /** A rack that comes round again never traps a player who overshoots. */
    @Test
    fun theRackWrapsAtEachEnd() {
        assertEquals(
            "mars",
            rack
                .next()
                .next()
                .next()
                .current
                ?.id,
        )
        assertEquals("korax", rack.previous().current?.id)
    }

    @Test
    fun aLargeStepStillLandsOnACard() {
        assertEquals("corvus", rack.moveBy(7).current?.id)
        assertEquals("korax", rack.moveBy(-7).current?.id)
    }

    @Test
    fun selectingByIdLandsOnThatCard() {
        assertEquals("korax", rack.select("korax").current?.id)
    }

    @Test
    fun selectingSomethingAbsentChangesNothing() {
        assertEquals(rack, rack.select("quake"))
    }

    @Test
    fun anEmptyRackHasNothingToSelect() {
        val empty = LauncherState(cards = emptyList())

        assertEquals(null, empty.current)
        assertEquals(empty, empty.next())
    }

    @Test
    fun installingDataChangesOnlyThatCard() {
        val waiting =
            LauncherState(
                cards =
                    listOf(
                        card("mars", GateAvailability.NeedsData(NEEDED)),
                        card("korax", GateAvailability.UserSuppliedOnly(NEEDED)),
                    ),
            )

        val installed = waiting.withAvailability("mars", GateAvailability.Installed)

        assertTrue(installed.cards.first().isPlayable)
        assertFalse(installed.cards.last().isPlayable)
    }
}
