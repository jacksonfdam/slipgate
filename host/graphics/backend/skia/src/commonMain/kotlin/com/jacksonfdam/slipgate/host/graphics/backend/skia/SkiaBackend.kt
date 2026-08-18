package com.jacksonfdam.slipgate.host.graphics.backend.skia

import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend

internal const val PALETTE_SHADER = "palette_indexed"
internal const val CRT_SHADER = "crt"
internal const val PALETTE_ENTRIES = 256
internal const val PALETTE_HEIGHT = 1

/**
 * The Skia runtime effect backend, or null where runtime effects do not exist.
 *
 * iOS and web always have them, because Compose draws through Skia there. Android has them from
 * API 33 as AGSL; below that the classic path is the answer rather than a placeholder.
 */
public expect fun skiaBackend(crt: CrtSettings = CrtSettings.Default): GraphicsBackend?

internal fun shaderSource(name: String): String = requireNotNull(skslSources[name]) { "missing shader $name" }

internal fun paletteShaderSource(): String = shaderSource(PALETTE_SHADER)

internal fun crtShaderSource(): String = shaderSource(CRT_SHADER)
