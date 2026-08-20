package com.jacksonfdam.slipgate.host.gamedata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Every fixture is assembled here rather than read from a file, because no game data may live in
 * this repository. A synthetic WAD is enough: the inspector reads structure, and structure is
 * exactly what these build.
 */
class WadInspectorTest {
    @Test
    fun anEpisodicDoomIwadIsRecognised() {
        val inspection =
            WadInspector.inspect(
                syntheticWad("IWAD", listOf("PLAYPAL", "E1M1", "E2M1", "E3M1", "E4M1")),
            )

        val identity = assertIs<WadInspection.Recognised>(inspection).identity
        assertEquals(WadKind.Iwad, identity.kind)
        assertEquals(GameFlavour.DoomEpisodic, identity.flavour)
        assertEquals(4, identity.episodes)
        assertEquals(0, identity.maps)
    }

    @Test
    fun aMappedDoomIwadIsRecognised() {
        val inspection = WadInspector.inspect(syntheticWad("IWAD", listOf("PLAYPAL", "MAP01", "MAP02")))

        val identity = assertIs<WadInspection.Recognised>(inspection).identity
        assertEquals(GameFlavour.DoomMapped, identity.flavour)
        assertEquals(2, identity.maps)
    }

    /** The tint table is what separates Raven's engines from Doom's, and the map naming separates
     * the two Raven games from each other. */
    @Test
    fun theTintTableTellsRavensEnginesApartFromDooms() {
        val heretic = WadInspector.inspect(syntheticWad("IWAD", listOf("PLAYPAL", "TINTTAB", "E1M1")))
        val hexen = WadInspector.inspect(syntheticWad("IWAD", listOf("PLAYPAL", "TINTTAB", "MAP01")))

        assertEquals(
            GameFlavour.Heretic,
            assertIs<WadInspection.Recognised>(heretic).identity.flavour,
        )
        assertEquals(
            GameFlavour.Hexen,
            assertIs<WadInspection.Recognised>(hexen).identity.flavour,
        )
    }

    @Test
    fun aPatchWadIsRecognisedAsOne() {
        val inspection = WadInspector.inspect(syntheticWad("PWAD", listOf("MAP01")))

        assertEquals(WadKind.Pwad, assertIs<WadInspection.Recognised>(inspection).identity.kind)
    }

    /**
     * Rogue built Strife on Doom's engine, so nothing but its own translucency table separates the
     * two. Without this the mars gate accepts `strife1.wad` as Doom data and then cannot boot it.
     */
    @Test
    fun strifeIsToldApartFromTheDoomItLooksLike() {
        val inspection =
            WadInspector.inspect(syntheticWad("IWAD", listOf("PLAYPAL", "XLATAB", "MAP01", "MAP02")))

        assertEquals(GameFlavour.Strife, assertIs<WadInspection.Recognised>(inspection).identity.flavour)
    }

    /** A palette is what lets a file stand alone, whatever its signature claims. */
    @Test
    fun aFileWithoutAPaletteIsAnAddOnRatherThanAGame() {
        val inspection = WadInspector.inspect(syntheticWad("PWAD", listOf("MAP01", "MAP02")))

        val identity = assertIs<WadInspection.Recognised>(inspection).identity
        assertEquals(WadRole.AddOn, identity.role)
        assertNull(identity.flavour)
    }

    /**
     * Hexen's Deathkings expansion ships this way: an `IWAD` signature over what is really an add-on,
     * which the engines resolve by looking for a palette rather than believing the header.
     */
    @Test
    fun anIwadSignatureWithoutAPaletteIsStillAnAddOn() {
        val inspection = WadInspector.inspect(syntheticWad("IWAD", listOf("MAP33", "MAP34")))

        val identity = assertIs<WadInspection.Recognised>(inspection).identity
        assertEquals(WadKind.Iwad, identity.kind)
        assertEquals(WadRole.AddOn, identity.role)
    }

    /** Chex Quest ships the other way round: a whole game under a `PWAD` signature. */
    @Test
    fun aPwadSignatureWithAPaletteIsStillBootable() {
        val inspection = WadInspector.inspect(syntheticWad("PWAD", listOf("PLAYPAL", "E1M1")))

        val identity = assertIs<WadInspection.Recognised>(inspection).identity
        assertEquals(WadKind.Pwad, identity.kind)
        assertEquals(WadRole.Bootable, identity.role)
        assertEquals(GameFlavour.DoomEpisodic, identity.flavour)
    }

    /** Which maps an add-on replaces is the one thing a player wants to read off it. */
    @Test
    fun theMapsAFileHoldsAreListedInTheOrderTheEngineFindsThem() {
        val inspection =
            WadInspector.inspect(syntheticWad("PWAD", listOf("MAP03", "THINGS", "MAP01", "LINEDEFS")))

        assertEquals(
            listOf("MAP03", "MAP01"),
            assertIs<WadInspection.Recognised>(inspection).identity.mapNames,
        )
    }

    @Test
    fun somethingThatIsNotAWadIsRejected() {
        val inspection = WadInspector.inspect("this is a photograph, honestly".encodeToByteArray())

        assertEquals(RejectionReason.NotAWad, assertIs<WadInspection.Rejected>(inspection).reason)
    }

    @Test
    fun aFileShorterThanAHeaderIsRejected() {
        val inspection = WadInspector.inspect(byteArrayOf(1, 2, 3))

        assertEquals(RejectionReason.TooSmall, assertIs<WadInspection.Rejected>(inspection).reason)
    }

    @Test
    fun aDirectoryPastTheEndOfTheFileIsRejected() {
        val bytes = syntheticWad("IWAD", listOf("PLAYPAL", "MAP01"))
        writeInt(bytes, offset = 8, value = bytes.size - 4)

        val inspection = WadInspector.inspect(bytes)

        assertEquals(
            RejectionReason.DirectoryUnreadable,
            assertIs<WadInspection.Rejected>(inspection).reason,
        )
    }

    /** What a download that stopped halfway looks like from the inside. */
    @Test
    fun aLumpClaimingBytesTheFileLacksIsRejected() {
        val whole = syntheticWad("IWAD", listOf("PLAYPAL", "MAP01"))
        val directoryOffset = readInt(whole, 8)
        writeInt(whole, offset = directoryOffset + 4, value = whole.size)

        val inspection = WadInspector.inspect(whole)

        assertEquals(
            RejectionReason.LumpOutOfRange,
            assertIs<WadInspection.Rejected>(inspection).reason,
        )
    }

    @Test
    fun aWadWithNoMapsIsRejected() {
        val inspection = WadInspector.inspect(syntheticWad("IWAD", listOf("PLAYPAL", "COLORMAP")))

        assertEquals(
            RejectionReason.UnknownGame,
            assertIs<WadInspection.Rejected>(inspection).reason,
        )
    }

    @Test
    fun anEmptyDirectoryIsRejected() {
        val bytes = syntheticWad("IWAD", emptyList())

        assertEquals(
            RejectionReason.DirectoryUnreadable,
            assertIs<WadInspection.Rejected>(WadInspector.inspect(bytes)).reason,
        )
    }
}
