package com.jacksonfdam.slipgate

import android.app.Application
import com.jacksonfdam.slipgate.games.corvus.CorvusGate
import com.jacksonfdam.slipgate.games.korax.KoraxGate
import com.jacksonfdam.slipgate.games.mars.MarsGate
import com.jacksonfdam.slipgate.ui.slipgateModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SlipgateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SlipgateApplication)
            modules(slipgateModules(gates = gates()))
        }
    }
}

/**
 * The gates this build ships. The entry point is the only place that names a game module, which is
 * what makes a new gate one line here rather than a change under `host`.
 */
private fun gates() = listOf(MarsGate(), CorvusGate(), KoraxGate())
