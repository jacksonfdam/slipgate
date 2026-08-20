package com.jacksonfdam.slipgate.games.macil

import com.jacksonfdam.slipgate.games.macil.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun macilModuleBytes(): ByteArray = Res.readBytes("files/macil.wasm")
