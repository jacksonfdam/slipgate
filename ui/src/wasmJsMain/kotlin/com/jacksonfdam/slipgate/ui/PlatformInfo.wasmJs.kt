package com.jacksonfdam.slipgate.ui

import org.koin.core.module.Module
import org.koin.dsl.module

private class WasmJsPlatformInfo : PlatformInfo {
    override val name: String = "Web (wasmJs)"
}

public actual val platformModule: Module =
    module {
        single<PlatformInfo> { WasmJsPlatformInfo() }
    }
