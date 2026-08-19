package com.jacksonfdam.slipgate.host.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class SilentAudioSinkTest {
    @Test
    fun everySubmittedFrameIsAccepted() {
        val sink = SilentAudioSink()

        assertEquals(64, sink.submit(ShortArray(128), frameCount = 64))
        assertEquals(64, sink.discardedFrames)
    }
}
