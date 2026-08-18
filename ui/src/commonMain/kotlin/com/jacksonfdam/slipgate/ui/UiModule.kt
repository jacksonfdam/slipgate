package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.graphics.backend.classic.ClassicBackend
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelector
import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackend
import com.jacksonfdam.slipgate.host.runtime.BackendId
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.TestPatternGate
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun uiModule(acceleratedBackends: List<GraphicsBackend>): Module =
    module {
        // The entry point owns gate registration: nothing under host may name a game module.
        single { GateRegistry(gates = listOf(TestPatternGate())) }
        single { BackendResolver(supported = listOf(BackendId.Wasm)) }
        // Accelerated candidates come from the platform, in preference order. The classic path is
        // appended here rather than by each entry point, because "it always works, so it goes
        // last" is a policy of the shell and not of any one platform.
        single { BackendSelector(candidates = acceleratedBackends + ClassicBackend()) }
        single<GateHost> { PlaceholderGateHost() }
    }

/**
 * Every Koin module the shell needs, in the order the entry points should load them.
 *
 * [acceleratedBackends] are the shader-capable rendering paths the platform managed to bring up,
 * most preferred first. An empty list is normal and means the classic path renders.
 */
public fun slipgateModules(acceleratedBackends: List<GraphicsBackend> = emptyList()): List<Module> =
    listOf(uiModule(acceleratedBackends), platformModule)
