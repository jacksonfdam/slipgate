package com.jacksonfdam.slipgate.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.jacksonfdam.slipgate.games.corvus.CorvusGate
import com.jacksonfdam.slipgate.games.korax.KoraxGate
import com.jacksonfdam.slipgate.games.mars.MarsGate
import com.jacksonfdam.slipgate.ui.SlipgateApp
import com.jacksonfdam.slipgate.ui.slipgateModules
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

private var started = false

/**
 * Entry point the Xcode target calls. Koin is started once here rather than in Swift, so the
 * three platforms wire the same modules in the same order.
 */
public fun mainViewController(): UIViewController {
    if (!started) {
        startKoin { modules(slipgateModules(gates = gates())) }
        started = true
    }
    return ComposeUIViewController { SlipgateApp() }
}

/**
 * The gates this build ships. The entry point is the only place that names a game module, which is
 * what makes a new gate one line here rather than a change under `host`.
 */
private fun gates() = listOf(MarsGate(), CorvusGate(), KoraxGate())
