package com.jacksonfdam.slipgate.ui

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIDevice

private class IosPlatformInfo : PlatformInfo {
    override val name: String =
        "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
}

public actual val platformModule: Module =
    module {
        single<PlatformInfo> { IosPlatformInfo() }
    }
