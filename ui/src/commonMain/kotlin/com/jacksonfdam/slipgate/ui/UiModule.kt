package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.graphics.backend.classic.ClassicBackend
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelector
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
        // Candidates are listed in preference order; the classic path always works and so
        // always comes last. The shader backends are appended as they land.
        single { BackendSelector(candidates = listOf(ClassicBackend())) }
        single<GateHost> { PlaceholderGateHost() }
    }

/** Every Koin module the shell needs, in the order the entry points should load them. */
public fun slipgateModules(): List<Module> = listOf(uiModule, platformModule)
