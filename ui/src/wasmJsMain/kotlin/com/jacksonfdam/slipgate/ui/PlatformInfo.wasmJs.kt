package com.jacksonfdam.slipgate.ui

import kotlinx.browser.window
import org.koin.core.module.Module
import org.koin.dsl.module

private class WasmJsPlatformInfo : PlatformInfo {
    override val name: String = "Web ${window.navigator.userAgent.substringBefore(' ')}"
}

public actual val platformModule: Module =
    module {
        single<PlatformInfo> { WasmJsPlatformInfo() }
    }
