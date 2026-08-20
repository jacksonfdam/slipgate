package com.jacksonfdam.slipgate.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackdropsTest {
    @Test
    fun everyPaintedGateHasItsOwnBackdropAndCover() {
        for (gate in listOf("mars", "chthon", "corvus", "korax", "macil")) {
            assertEquals("bg_$gate", Backdrops.forGate(gate))
            assertEquals("cover_$gate", Backdrops.coverFor(gate))
        }
    }

    @Test
    fun anUnpaintedGateFallsBackToTheSelectBackdropAndNoCover() {
        assertEquals(Backdrops.SELECT, Backdrops.forGate("testpattern"))
        assertNull(Backdrops.coverFor("testpattern"))
    }

    @Test
    fun noFocusedGateMeansTheSelectBackdrop() {
        assertEquals(Backdrops.SELECT, Backdrops.forGate(null))
    }
}
