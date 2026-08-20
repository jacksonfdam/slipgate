package com.jacksonfdam.slipgate.host.graphics.backend.skia

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The painter's contract, which the launcher depends on for its fallback: a gate with a portrait gets
 * one, and a gate without gets nothing rather than an empty shader that would draw black.
 */
class ScenePainterTest {
    @Test
    fun everyGateWithAPortraitGetsAPainter() {
        assertNotNull(portraitPainter("mars"), "the mars portrait did not compile")
        assertNotNull(portraitPainter("corvus"), "the corvus portrait did not compile")
        assertNotNull(portraitPainter("korax"), "the korax portrait did not compile")
    }

    @Test
    fun aGateWithoutAPortraitGetsNone() {
        assertNull(portraitPainter("testpattern"))
    }
}
