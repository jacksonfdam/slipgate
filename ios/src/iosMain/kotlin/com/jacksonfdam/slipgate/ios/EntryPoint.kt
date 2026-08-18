package com.jacksonfdam.slipgate.ios

import androidx.compose.ui.window.ComposeUIViewController
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
        startKoin { modules(slipgateModules()) }
        started = true
    }
    return ComposeUIViewController { SlipgateApp() }
}
