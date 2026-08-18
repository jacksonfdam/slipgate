package com.jacksonfdam.slipgate

import android.app.Application
import com.jacksonfdam.slipgate.ui.slipgateModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SlipgateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SlipgateApplication)
            modules(slipgateModules())
        }
    }
}
