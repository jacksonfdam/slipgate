package com.jacksonfdam.slipgate.games.korax

import com.jacksonfdam.slipgate.games.korax.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun koraxModuleBytes(): ByteArray = Res.readBytes("files/korax.wasm")
