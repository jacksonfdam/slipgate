package com.jacksonfdam.slipgate.host.backend.wasm

/**
 * The command line a gate starts its engine with.
 *
 * Every engine here descends from the same one and reads the same switches, so the line is built
 * once rather than assembled four times with four chances to get the order wrong.
 *
 * `-nomusic` is passed because the platform layer mixes sound effects but leaves music to a later
 * measured budget; sound effects are on, and the host drains them as it steps.
 *
 * [switches] is what one engine needs and the others do not. Strife is the only user so far: its
 * `showintro` defaults to on and gates `I_InitGraphics`, which the host needs to have run before it
 * escapes start-up, so the Strife gate passes `-nograph`. It goes before `-file` for the reason
 * below, and a gate that needs nothing extra passes nothing.
 *
 * `-file` comes last because everything after it is read as a filename until the next switch — a
 * switch placed after the add-on list would be swallowed as one of them.
 */
public fun engineArguments(
    iwadName: String,
    addOns: List<String> = emptyList(),
    switches: List<String> = emptyList(),
): List<String> =
    buildList {
        add("slipgate")
        add("-iwad")
        add(iwadName)
        add("-nomusic")
        addAll(switches)
        if (addOns.isNotEmpty()) {
            add("-file")
            addAll(addOns)
        }
    }
