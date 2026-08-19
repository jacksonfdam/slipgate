package com.jacksonfdam.slipgate.host.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PcmRingBufferTest {
    private val ring = PcmRingBuffer(channels = 2, capacityFrames = 4)

    @Test
    fun framesComeBackInTheOrderTheyWereWritten() {
        val written = ring.write(shortArrayOf(1, 2, 3, 4), frameCount = 2)
        val destination = ShortArray(4)
        val served = ring.read(destination, frameCount = 2)

        assertEquals(2, written)
        assertEquals(2, served)
        assertContentEquals(shortArrayOf(1, 2, 3, 4), destination)
    }

    @Test
    fun aFullBufferAcceptsWhatFitsAndReportsIt() {
        val accepted = ring.write(ShortArray(12) { 7 }, frameCount = 6)

        assertEquals(4, accepted)
        assertEquals(0, ring.freeFrames)
    }

    @Test
    fun readingReleasesRoomForMore() {
        ring.write(ShortArray(8) { 1 }, frameCount = 4)
        ring.read(ShortArray(4), frameCount = 2)

        assertEquals(2, ring.freeFrames)
        assertEquals(2, ring.write(shortArrayOf(9, 9, 9, 9), frameCount = 2))
    }

    /** The wrap is where an off-by-one hides, so the sequence has to survive crossing the end. */
    @Test
    fun framesSurviveTheWrapAround() {
        ring.write(ShortArray(6) { 1 }, frameCount = 3)
        ring.read(ShortArray(6), frameCount = 3)
        ring.write(shortArrayOf(1, 2, 3, 4, 5, 6), frameCount = 3)

        val destination = ShortArray(6)
        assertEquals(3, ring.read(destination, frameCount = 3))
        assertContentEquals(shortArrayOf(1, 2, 3, 4, 5, 6), destination)
    }

    @Test
    fun anEmptyBufferServesNothingAndLeavesTheDestinationAlone() {
        val destination = ShortArray(4) { -1 }

        assertEquals(0, ring.read(destination, frameCount = 2))
        assertContentEquals(ShortArray(4) { -1 }, destination)
    }

    @Test
    fun aShortReadLeavesTheRestOfTheDestinationAlone() {
        ring.write(shortArrayOf(5, 6), frameCount = 1)
        val destination = ShortArray(4) { -1 }

        assertEquals(1, ring.read(destination, frameCount = 2))
        assertContentEquals(shortArrayOf(5, 6, -1, -1), destination)
    }

    @Test
    fun clearingDropsWhatWasBuffered() {
        ring.write(ShortArray(8) { 3 }, frameCount = 4)
        ring.clear()

        assertEquals(0, ring.availableFrames)
        assertEquals(4, ring.freeFrames)
    }
}
