package com.jacksonfdam.slipgate.host.backend.wasm

import com.jacksonfdam.slipgate.host.runtime.GateHost

/**
 * The slot every engine file lives in.
 *
 * One slot rather than one per savegame, because the engine decides what a slot means and the shapes
 * disagree: Doom has six numbered files, Hexen has a hub with a file per map it has visited. What the
 * host keeps is the engine's own directory, and the engine reads its own meaning back out of it.
 */
public const val ENGINE_SAVE_SLOT: String = "engine"

private const val SEPARATOR = "-s-"
private const val DASH = "-d-"
private const val ESCAPE_LENGTH = 3

/**
 * The engine's path for a file, as a name a store will keep unchanged.
 *
 * Storage reduces a name to characters every platform can write, which turns a directory separator
 * into an underscore and loses the path. Doom saves into `savegames/doom.wad/`, so losing it would
 * mean writing the file back in the wrong place.
 *
 * Escaped one character at a time rather than by replacing substrings: a dash of its own becomes an
 * escape too, so an encoded name never holds a bare dash and the decoder can never mistake part of a
 * real name for a separator. Any path either engine writes comes back exactly as it went in.
 */
public fun flattenSavePath(path: String): String =
    buildString {
        path.forEach { character ->
            when (character) {
                '/' -> append(SEPARATOR)
                '-' -> append(DASH)
                else -> append(character)
            }
        }
    }

/** The engine's path again, from the name a store kept. */
public fun expandSavePath(name: String): String =
    buildString {
        var index = 0
        while (index < name.length) {
            when {
                name.startsWith(SEPARATOR, index) -> {
                    append('/')
                    index += ESCAPE_LENGTH
                }

                name.startsWith(DASH, index) -> {
                    append('-')
                    index += ESCAPE_LENGTH
                }

                else -> {
                    append(name[index])
                    index += 1
                }
            }
        }
    }

/**
 * Everything the host kept for this gate, under the paths the engine wrote them at.
 *
 * Handed to [WasmEngine.start], which writes them into the module's filesystem before the engine
 * reads a thing — a save the engine cannot see is a save the player lost.
 */
public suspend fun keptSaves(host: GateHost): Map<String, ByteArray> =
    host.storage
        .files(ENGINE_SAVE_SLOT)
        .mapNotNull { name ->
            host.storage.read(ENGINE_SAVE_SLOT, name)?.let { bytes -> expandSavePath(name) to bytes }
        }.toMap()
