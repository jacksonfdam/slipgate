package com.jacksonfdam.slipgate.host.runtime

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestPatternGateTest {
    @Test
    fun gateNeedsNoData() {
        val gate = TestPatternGate()
        assertTrue(gate.requirements().entries.isEmpty())
    }

    @Test
    fun gateOffersEveryBackend() {
        val gate = TestPatternGate()
        assertEquals(BackendId.entries.toSet(), gate.sessionFactories().keys)
    }

    @Test
    fun sessionFillsTheFramebuffer() =
        runTest {
            val session = openSession()
            val result = session.step(InputFrame.Idle, elapsedMillis = 16)

            assertTrue(result.frameRendered)
            assertEquals(SessionStatus.Running, result.status)
            assertEquals(session.display.frameSizeBytes, session.framebuffer().size)
            assertTrue(session.framebuffer().any { it != 0.toByte() })
        }

    @Test
    fun paletteCoversEveryIndex() =
        runTest {
            val palette = assertNotNull(openSession().palette())

            assertEquals(256, palette.size)
            assertTrue(palette.all { it ushr 24 == 0xFF })
        }

    @Test
    fun steppingIsDeterministic() =
        runTest {
            val first = openSession()
            val second = openSession()

            repeat(4) {
                first.step(InputFrame.Idle, elapsedMillis = 33)
                second.step(InputFrame.Idle, elapsedMillis = 33)
            }

            assertContentEquals(first.framebuffer(), second.framebuffer())
            assertContentEquals(first.snapshot(), second.snapshot())
        }

    @Test
    fun timeAdvancesThePattern() =
        runTest {
            val session = openSession()
            session.step(InputFrame.Idle, elapsedMillis = 0)
            val firstFrame = session.framebuffer().copyOf()

            session.step(InputFrame.Idle, elapsedMillis = 500)

            assertTrue(!firstFrame.contentEquals(session.framebuffer()))
        }

    @Test
    fun closedSessionStopsRendering() =
        runTest {
            val session = openSession()
            session.close()

            val result = session.step(InputFrame.Idle, elapsedMillis = 16)

            assertEquals(SessionStatus.Finished, result.status)
            assertTrue(!result.frameRendered)
        }

    private suspend fun openSession(): GateSession {
        val gate = TestPatternGate()
        val factory = assertNotNull(gate.sessionFactories()[BackendId.Wasm])
        return factory.create(MountedGameData.Empty, FakeGateHost())
    }
}
