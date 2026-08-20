package com.jacksonfdam.slipgate.games.macil

private const val MODULE_PATH = "files/macil.wasm"

internal actual suspend fun macilModuleBytes(): ByteArray {
    val loader = MacilGate::class.java.classLoader ?: error("the gate has no class loader")
    return loader.getResourceAsStream(MODULE_PATH)?.use { it.readBytes() }
        ?: error("the engine module is missing from the build; expected $MODULE_PATH")
}
