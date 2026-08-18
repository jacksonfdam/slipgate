package com.jacksonfdam.slipgate.host.graphics.backend.skia

import android.os.Build
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.runtime.DisplayFormat

/**
 * AGSL arrived in Android 13. Below that there is no runtime shader at all, so this backend does
 * not exist rather than existing and failing — the classic path is the answer on those devices.
 */
public actual fun skiaBackend(): GraphicsBackend? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AgslBackend() else null

internal class AgslBackend : GraphicsBackend {
    override val id: GraphicsBackendId = GraphicsBackendId.Skia

    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun createRenderer(format: DisplayFormat): ComposeFrameRenderer = AgslFrameRenderer(format)
}
