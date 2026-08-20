package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.gamedata.DocumentsGameDataStore
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.ui.settings.DefaultsSettingsStore
import com.jacksonfdam.slipgate.ui.settings.SettingsStore
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
        single<SettingsStore> { DefaultsSettingsStore() }
        single<PlatformInfo> { IosPlatformInfo() }
        // Which engine backends this platform can run, in preference order.
        single { BackendResolver(supported = listOf(BackendId.Wasm)) }
    }
