package com.jacksonfdam.slipgate.games.korax

/**
 * The engine module, read from the gate's own build output.
 *
 * It ships with the gate rather than being downloaded, because it is code rather than game data:
 * GPLv2 code, built from the sources `tooling/engine-build/SOURCES.lock` pins.
 *
 * How it is read differs by platform because how it is packaged differs: Compose resources carry it
 * on iOS and the JVM, and Android reads it as a plain java resource, which is what an AAR carries.
 * One committed file either way — a second copy of a binary in the repository would be worse.
 */
internal expect suspend fun koraxModuleBytes(): ByteArray
