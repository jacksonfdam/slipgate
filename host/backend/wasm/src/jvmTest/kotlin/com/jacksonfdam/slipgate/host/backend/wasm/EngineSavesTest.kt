package com.jacksonfdam.slipgate.host.backend.wasm

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The names the engine's paths become, and back again.
 *
 * Storage reduces a name to what every platform can write, which would turn `savegames/doom.wad/` into
 * one flat word and put the file back in the wrong place. This mapping is what survives that, so it is
 * worth pinning: every path either engine writes has to come back exactly as it went in.
 */
class EngineSavesTest {
    @Test
    fun aDoomSavePathRoundTrips() {
        val path = "savegames/doom.wad/doomsav0.dsg"

        val flattened = flattenSavePath(path)

        assertEquals("savegames-s-doom.wad-s-doomsav0.dsg", flattened)
        assertEquals(path, expandSavePath(flattened))
    }

    @Test
    fun aHexenHubSavePathRoundTrips() {
        val path = "savegames/hexen.wad/hex1/map03.hxs"

        assertEquals(path, expandSavePath(flattenSavePath(path)))
    }

    /** A name holding the escape itself: the encoder has to survive its own vocabulary. */
    @Test
    fun aNameThatLooksLikeTheEscapeRoundTrips() {
        val path = "savegames/my-s-wad-d-file.wad/doomsav1.dsg"

        assertEquals(path, expandSavePath(flattenSavePath(path)))
    }

    @Test
    fun aNameWithUnderscoresIsLeftAlone() {
        val path = "savegames/my_wad.wad/doomsav1.dsg"

        assertEquals("savegames-s-my_wad.wad-s-doomsav1.dsg", flattenSavePath(path))
        assertEquals(path, expandSavePath(flattenSavePath(path)))
    }

    @Test
    fun aConfigAtTheRootRoundTrips() {
        assertEquals("default.cfg", expandSavePath(flattenSavePath("default.cfg")))
    }
}
