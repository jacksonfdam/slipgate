package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.games.mars.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The engine module, read from the gate's own resources.
 *
 * It ships with the gate rather than being downloaded, because it is code rather than game data:
 * GPLv2 code, built from the sources `tooling/engine-build/SOURCES.lock` pins.
 */
@OptIn(ExperimentalResourceApi::class)
internal suspend fun marsModuleBytes(): ByteArray = Res.readBytes("files/mars.wasm")
