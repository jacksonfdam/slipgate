package com.jacksonfdam.slipgate.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jacksonfdam.slipgate.host.graphics.backend.webgpu.WebGpuBackend
import com.jacksonfdam.slipgate.host.graphics.backend.webgpu.WebGpuProbe
import com.jacksonfdam.slipgate.ui.SlipgateApp
import com.jacksonfdam.slipgate.ui.slipgateModules
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

private const val GPU_CANVAS_ID = "slipgate-gpu"

// The game canvas sits above the Compose container: Compose for web clears its canvas to opaque
// white, so it cannot composite over anything. Pointer events pass straight through, so the shell
// still receives input. Anything Compose draws is hidden while a surface-owning backend renders,
// which is why this arrangement is provisional — see docs/specification and the pull request.
private const val GPU_CANVAS_STYLE =
    "position:fixed; inset:0; width:100%; height:100%; z-index:2; pointer-events:none;" +
        " background:#0b0b0d;"
private const val SHELL_CONTAINER_ID = "slipgate-shell"
private const val SHELL_CONTAINER_STYLE = "position:fixed; inset:0; z-index:1;"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val canvas = gpuCanvas()
    // WebGPU needs an adapter and a device before it can say whether it works, so the probe
    // happens before the shell starts. By selection time the answer is already known.
    MainScope().launch {
        val backend =
            when (val probe = WebGpuBackend.probe(canvas)) {
                is WebGpuProbe.Ready -> {
                    probe.backend
                }

                is WebGpuProbe.Unavailable -> {
                    println("Slipgate: WebGPU unavailable (${probe.reason}); using the classic path")
                    canvas.remove()
                    null
                }
            }
        startKoin { modules(slipgateModules(acceleratedBackends = listOfNotNull(backend))) }
        ComposeViewport(shellContainer()) { SlipgateApp() }
    }
}

/**
 * A canvas of Slipgate's own for a backend that owns its surface.
 *
 * Compose gets its own container rather than the document body, because attaching it to the body
 * clears everything already there — including this canvas.
 */
private fun gpuCanvas(): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.id = GPU_CANVAS_ID
    canvas.setAttribute("style", GPU_CANVAS_STYLE)
    val body = document.body
    if (body != null) {
        body.insertBefore(canvas, body.firstChild)
    }
    return canvas
}

/** Container Compose renders into, stacked above the game canvas. */
private fun shellContainer(): HTMLElement {
    val container = document.createElement("div") as HTMLElement
    container.id = SHELL_CONTAINER_ID
    container.setAttribute("style", SHELL_CONTAINER_STYLE)
    document.body?.appendChild(container)
    return container
}
