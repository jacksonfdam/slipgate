package com.jacksonfdam.slipgate.ui

import android.os.Build
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.androidGameDataStore
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
    }
