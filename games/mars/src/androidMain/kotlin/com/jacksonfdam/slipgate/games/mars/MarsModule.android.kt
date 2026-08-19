package com.jacksonfdam.slipgate.games.mars

private const val MODULE_PATH = "files/mars.wasm"

internal actual suspend fun marsModuleBytes(): ByteArray {
    val loader = MarsGate::class.java.classLoader ?: error("the gate has no class loader")
    return loader.getResourceAsStream(MODULE_PATH)?.use { it.readBytes() }
        ?: error("the engine module is missing from the build; expected $MODULE_PATH")
}
