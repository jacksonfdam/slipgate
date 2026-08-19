package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.openWebGameDataStore
import com.jacksonfdam.slipgate.ui.settings.LocalStorageSettingsStore
import com.jacksonfdam.slipgate.ui.settings.SettingsStore
import org.koin.core.module.Module
import org.koin.dsl.module

private class WasmJsPlatformInfo : PlatformInfo {
    override val name: String = "Web (wasmJs)"
}

public actual val platformModule: Module =
    module {
        single<GameDataStore> { openWebGameDataStore() }
        single<SettingsStore> { LocalStorageSettingsStore() }
        single<PlatformInfo> { WasmJsPlatformInfo() }
    }
