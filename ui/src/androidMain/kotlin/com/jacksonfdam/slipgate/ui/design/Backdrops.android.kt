package com.jacksonfdam.slipgate.ui.design

internal actual suspend fun backdropBytes(path: String): ByteArray? {
    val loader = Backdrops::class.java.classLoader ?: return null
    return loader.getResourceAsStream(path)?.use { it.readBytes() }
}
