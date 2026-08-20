package com.jacksonfdam.slipgate.games.korax

private const val MODULE_PATH = "files/korax.wasm"

internal actual suspend fun koraxModuleBytes(): ByteArray {
    val loader = KoraxGate::class.java.classLoader ?: error("the gate has no class loader")
    return loader.getResourceAsStream(MODULE_PATH)?.use { it.readBytes() }
        ?: error("the engine module is missing from the build; expected $MODULE_PATH")
}
