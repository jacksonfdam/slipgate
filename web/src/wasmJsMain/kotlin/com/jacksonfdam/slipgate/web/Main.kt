package com.jacksonfdam.slipgate.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jacksonfdam.slipgate.host.runtime.TestPatternGate
import com.jacksonfdam.slipgate.ui.SlipgateApp
import com.jacksonfdam.slipgate.ui.slipgateModules
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(slipgateModules(gates = gates())) }
    ComposeViewport { SlipgateApp() }
}

/**
 * The gates this build ships.
 *
 * The Doom gate is absent on web, and not by choice: it runs on Chasm, which publishes nothing for
 * wasmJs, so a browser needs a driver built on its own WebAssembly engine. Until that exists this
 * build ships the test pattern, which exercises the same session, framebuffer and palette path.
 */
private fun gates() = listOf(TestPatternGate())
