package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.host.runtime.AccentSource
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataSource
import com.jacksonfdam.slipgate.host.runtime.GateArtwork
import com.jacksonfdam.slipgate.host.runtime.GateDescriptor
import com.jacksonfdam.slipgate.host.runtime.GateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a card says out loud.
 *
 * Worth pinning because the glyphs and the paint say nothing: since the pad and the covers stopped
 * being words, this string is the whole of what somebody listening gets. A card that announced only
 * its name would leave them tapping a gate that cannot be entered.
 */
class SpokenCardTest {
    @Test
    fun aReadyGateSaysSo() {
        val spoken = spoken(card(GateAvailability.Installed))

        assertEquals("Mars, Doom, ready to play", spoken)
    }

    @Test
    fun aGateMissingDataSaysWhatIsMissing() {
        val spoken = spoken(card(GateAvailability.NeedsData(entry)))

        assertTrue(spoken.startsWith("Mars, Doom, needs Doom IWAD"), spoken)
    }

    @Test
    fun aGateOnlyThePlayerCanFeedSaysThatToo() {
        val spoken = spoken(card(GateAvailability.UserSuppliedOnly(entry)))

        assertTrue(spoken.endsWith("which only you can supply"), spoken)
    }

    private val entry =
        DataEntry(
            key = "doom.wad",
            displayName = "Doom IWAD",
            sources = listOf(DataSource.UserSupplied),
        )

    private fun card(availability: GateAvailability) =
        GateCard(
            descriptor =
                GateDescriptor(
                    id = GateId("mars"),
                    title = "Mars",
                    engine = "Doom",
                    artwork = GateArtwork(coverKey = "mars/cover"),
                    accent = AccentSource.PaletteEntry(index = 176),
                ),
            availability = availability,
        )
}
