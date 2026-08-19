package com.jacksonfdam.slipgate.host.gamedata

private const val MAX_NAME_LENGTH = 64
private val SAFE_CHARACTERS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '-', '_')

/**
 * Reduces a name a player's file arrived with to one every platform can store.
 *
 * A picked file can be called anything at all, including things that mean something to a file
 * system: separators, parent directories, a leading dot, a name longer than a path allows. Storing
 * such a name verbatim is how a file ends up written outside the folder it was meant for, so the
 * name is rebuilt from the characters that are safe everywhere rather than checked against a list of
 * the ones that are not.
 */
public fun safeStorageName(name: String): String {
    val kept = name.map { if (it in SAFE_CHARACTERS) it else '_' }.joinToString("").trimStart('.')
    val trimmed = kept.takeLast(MAX_NAME_LENGTH)
    return trimmed.ifBlank { "data" }
}
