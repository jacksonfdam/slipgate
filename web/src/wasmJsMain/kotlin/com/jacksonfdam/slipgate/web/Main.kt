package com.jacksonfdam.slipgate.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jacksonfdam.slipgate.games.mars.MarsGate
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
 * The Doom gate reaches the web now that the module runs on the browser's own WebAssembly engine
 * rather than on Chasm, which publishes nothing for wasmJs. The test pattern stays: it needs no game
 * data, which makes it the one gate that can be entered on a first visit.
 */
private fun gates() = listOf(MarsGate(), TestPatternGate())
