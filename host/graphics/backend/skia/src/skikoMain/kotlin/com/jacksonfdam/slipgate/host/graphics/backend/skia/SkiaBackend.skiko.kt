package com.jacksonfdam.slipgate.host.graphics.backend.skia

import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat

/** Compose draws through Skia on iOS and web, so runtime effects are always available. */
public actual fun skiaBackend(crt: CrtSettings): GraphicsBackend? = SkikoBackend(crt)

internal class SkikoBackend(
    private val crt: CrtSettings,
) : GraphicsBackend {
    override val id: GraphicsBackendId = GraphicsBackendId.Skia

    override fun isAvailable(): Boolean = true

    override fun createRenderer(format: DisplayFormat): ComposeFrameRenderer = SkikoFrameRenderer(format, crt)
}
