package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.gamedata.DocumentsGameDataStore
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIDevice

private class IosPlatformInfo : PlatformInfo {
    override val name: String =
        "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
}

public actual val platformModule: Module =
    module {
        single<GameDataStore> { DocumentsGameDataStore() }
        single<PlatformInfo> { IosPlatformInfo() }
    }
