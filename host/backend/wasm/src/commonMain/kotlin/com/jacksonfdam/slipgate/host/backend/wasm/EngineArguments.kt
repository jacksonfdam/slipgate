package com.jacksonfdam.slipgate.host.backend.wasm

/**
 * The command line every gate starts its engine with.
 *
 * All three engines here descend from the same one and read the same switches, so the line is built
 * once rather than assembled three times with three chances to get the order wrong.
 *
 * `-nomusic` is passed because the platform layer mixes sound effects but leaves music to a later
 * measured budget; sound effects are on, and the host drains them as it steps.
 *
 * `-file` comes last because everything after it is read as a filename until the next switch — a
 * switch placed after the add-on list would be swallowed as one of them.
 */
public fun engineArguments(
    iwadName: String,
    addOns: List<String> = emptyList(),
): List<String> =
    buildList {
        add("slipgate")
        add("-iwad")
        add(iwadName)
        add("-nomusic")
        if (addOns.isNotEmpty()) {
            add("-file")
            addAll(addOns)
        }
    }
