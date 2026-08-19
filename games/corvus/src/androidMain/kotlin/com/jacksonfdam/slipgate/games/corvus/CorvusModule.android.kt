package com.jacksonfdam.slipgate.games.corvus

private const val MODULE_PATH = "files/corvus.wasm"

internal actual suspend fun corvusModuleBytes(): ByteArray {
    val loader = CorvusGate::class.java.classLoader ?: error("the gate has no class loader")
    return loader.getResourceAsStream(MODULE_PATH)?.use { it.readBytes() }
        ?: error("the engine module is missing from the build; expected $MODULE_PATH")
}
