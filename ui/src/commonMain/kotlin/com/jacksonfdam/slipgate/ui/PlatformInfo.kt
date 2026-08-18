package com.jacksonfdam.slipgate.ui

import org.koin.core.module.Module

/**
 * Identity of the platform the shell is running on. Surfaced in the UI while the launcher
 * is still a placeholder, and used later to pick a graphics backend.
 */
public interface PlatformInfo {
    public val name: String
}

/** Platform bindings contributed by each target's source set. */
public expect val platformModule: Module
