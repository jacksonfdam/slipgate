package com.jacksonfdam.slipgate.ui

import org.koin.core.module.Module
import org.koin.dsl.module

internal val uiModule: Module = module { }

/** Every Koin module the shell needs, in the order the entry points should load them. */
public fun slipgateModules(): List<Module> = listOf(uiModule, platformModule)
