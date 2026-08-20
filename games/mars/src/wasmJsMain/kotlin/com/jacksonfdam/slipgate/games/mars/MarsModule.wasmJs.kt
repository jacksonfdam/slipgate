package com.jacksonfdam.slipgate.games.mars

import com.jacksonfdam.slipgate.games.mars.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun marsModuleBytes(): ByteArray = Res.readBytes("files/mars.wasm")
