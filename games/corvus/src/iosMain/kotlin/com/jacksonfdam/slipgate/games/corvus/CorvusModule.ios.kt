package com.jacksonfdam.slipgate.games.corvus

import com.jacksonfdam.slipgate.games.corvus.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun corvusModuleBytes(): ByteArray = Res.readBytes("files/corvus.wasm")
