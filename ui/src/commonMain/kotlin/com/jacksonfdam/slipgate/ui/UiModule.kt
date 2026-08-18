package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.TestPatternGate
import org.koin.core.module.Module
import org.koin.dsl.module

internal val uiModule: Module =
    module {
        // The entry point owns gate registration: nothing under host may name a game module.
        single { GateRegistry(gates = listOf(TestPatternGate())) }
        single { BackendResolver(supported = listOf(BackendId.Wasm)) }
        single<GateHost> { PlaceholderGateHost() }
    }

/** Every Koin module the shell needs, in the order the entry points should load them. */
public fun slipgateModules(): List<Module> = listOf(uiModule, platformModule)
