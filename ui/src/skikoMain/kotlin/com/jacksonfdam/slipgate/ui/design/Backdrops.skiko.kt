package com.jacksonfdam.slipgate.ui.design

import com.jacksonfdam.slipgate.ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun backdropBytes(path: String): ByteArray? =
    try {
        Res.readBytes(path)
    } catch (_: MissingResourceException) {
        null
    }
