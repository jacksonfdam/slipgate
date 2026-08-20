package com.jacksonfdam.slipgate.ui

import android.os.Build
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.androidGameDataStore
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.ui.settings.AndroidSettingsStore
import com.jacksonfdam.slipgate.ui.settings.SettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private class AndroidPlatformInfo : PlatformInfo {
    override val name: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

public actual val platformModule: Module =
    module {
        single<PlatformInfo> { AndroidPlatformInfo() }
        single<GameDataStore> { androidGameDataStore(androidContext()) }
        single<SettingsStore> { AndroidSettingsStore(androidContext()) }
        // Which engine backends this platform can run, in preference order.
        single { BackendResolver(supported = listOf(BackendId.Wasm)) }
    }
