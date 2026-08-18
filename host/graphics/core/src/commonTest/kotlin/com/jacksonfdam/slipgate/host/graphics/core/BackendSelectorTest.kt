package com.jacksonfdam.slipgate.host.graphics.core

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeBackend(
    override val id: GraphicsBackendId,
    private val available: Boolean,
) : GraphicsBackend {
    override fun isAvailable(): Boolean = available

    override fun createRenderer(format: DisplayFormat): FrameRenderer =
        object : FrameRenderer {
            override val backendId: GraphicsBackendId = id

            override fun present(
                frame: PresentedFrame,
                viewport: Viewport,
            ) = Unit

            override fun close() = Unit
        }
}

class BackendSelectorTest {
    private val format =
        DisplayFormat(width = 320, height = 200, pixelFormat = PixelFormat.Indexed8)

    @Test
    fun theFirstAvailableCandidateWins() {
        val selector =
            BackendSelector(
                listOf(
                    FakeBackend(GraphicsBackendId.WebGpu, available = true),
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                ),
            )

        val selection = selector.select()

        assertEquals(GraphicsBackendId.WebGpu, selection.backend.id)
        assertFalse(selection.fellBack)
    }

    @Test
    fun anUnavailablePreferredBackendFallsBack() {
        val selector =
            BackendSelector(
                listOf(
                    FakeBackend(GraphicsBackendId.WebGpu, available = false),
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                ),
            )

        val selection = selector.select(preferred = GraphicsBackendId.WebGpu)

        assertEquals(GraphicsBackendId.Classic, selection.backend.id)
        assertTrue(selection.fellBack)
        assertEquals(listOf(GraphicsBackendId.WebGpu), selection.rejected)
    }

    @Test
    fun aPreferredBackendOutranksTheCandidateOrder() {
        val selector =
            BackendSelector(
                listOf(
                    FakeBackend(GraphicsBackendId.WebGpu, available = true),
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                ),
            )

        val selection = selector.select(preferred = GraphicsBackendId.Classic)

        assertEquals(GraphicsBackendId.Classic, selection.backend.id)
        assertFalse(selection.fellBack)
    }

    @Test
    fun availableListsOnlyWhatCanRender() {
        val selector =
            BackendSelector(
                listOf(
                    FakeBackend(GraphicsBackendId.WebGpu, available = false),
                    FakeBackend(GraphicsBackendId.Skia, available = true),
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                ),
            )

        assertEquals(
            listOf(GraphicsBackendId.Skia, GraphicsBackendId.Classic),
            selector.available(),
        )
    }

    @Test
    fun nothingAvailableIsAnError() {
        val selector =
            BackendSelector(listOf(FakeBackend(GraphicsBackendId.WebGpu, available = false)))

        assertFailsWith<NoGraphicsBackendException> { selector.select() }
    }

    @Test
    fun duplicateCandidatesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            BackendSelector(
                listOf(
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                    FakeBackend(GraphicsBackendId.Classic, available = true),
                ),
            )
        }
    }

    @Test
    fun rendererReportsItsBackend() {
        val backend = FakeBackend(GraphicsBackendId.Skia, available = true)

        assertEquals(GraphicsBackendId.Skia, backend.createRenderer(format).backendId)
    }
}
