package com.jacksonfdam.slipgate.ui.credits

/** One block of the scroller: a heading and the lines under it. */
public data class CreditEntry(
    val heading: String,
    val lines: List<String>,
    /** Licence text and version strings are machine-written, so they are set in the mono face. */
    val monospaced: Boolean = false,
)

/**
 * Everything this project owes, in the order it owes it, and the licences that oblige it.
 *
 * Held as data rather than composed inline so a test can assert the obligations are present: the
 * GPLv2 notice and the engines' authorship are requirements, and a screen that quietly lost one would
 * be a licence violation nobody noticed.
 */
public fun credits(): List<CreditEntry> = authorship() + engines() + licences()

private fun authorship(): List<CreditEntry> =
    listOf(
        CreditEntry(
            heading = "Slipgate",
            lines =
                listOf(
                    "A launcher for the engines that made the genre, and a host for them.",
                    "Interface, host and tooling: Jackson F. de A. Mafra.",
                ),
        ),
        CreditEntry(
            heading = "Architecture",
            lines =
                listOf(
                    "mood, by Charlie Tapping — the architecture this host is modelled on.",
                    "Chasm, by Charlie Tapping — the WebAssembly runtime the gates execute in.",
                ),
        ),
    )

private fun engines(): List<CreditEntry> =
    listOf(
        CreditEntry(
            heading = "Engines",
            lines =
                listOf(
                    "Chocolate Doom — the port every gate in this build is derived from.",
                    "id Software — Doom, and the engine every gate here descends from.",
                    "Raven Software — Heretic and Hexen.",
                ),
        ),
        CreditEntry(
            heading = "Freely licensed game data",
            lines =
                listOf(
                    "Freedoom — the free replacement offered for Doom, under a three-clause BSD licence.",
                    "Blasphemer — the free replacement offered for Heretic.",
                ),
        ),
    )

/** The obligations: GPLv2 for the engines, the host's own terms, and what this app does not ship. */
private fun licences(): List<CreditEntry> =
    listOf(
        CreditEntry(
            heading = "Engine licence",
            monospaced = true,
            lines =
                listOf(
                    "Chocolate Doom and the Raven engines are distributed under the GNU General Public " +
                        "License, version 2, and so are the WebAssembly modules built from them.",
                    "This program is free software; you can redistribute it and/or modify it under the " +
                        "terms of the GNU General Public License as published by the Free Software " +
                        "Foundation; either version 2 of the License, or (at your option) any later version.",
                    "This program is distributed in the hope that it will be useful, but WITHOUT ANY " +
                        "WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A " +
                        "PARTICULAR PURPOSE. See the GNU General Public License for more details.",
                    "Source for the engine modules, and the exact revisions they were built from, are in " +
                        "tooling/engine-build/SOURCES.lock in this project's repository.",
                    "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html",
                ),
        ),
        CreditEntry(
            heading = "Host licence",
            monospaced = true,
            lines =
                listOf(
                    "The host — everything in this project that is not an engine — is dual licensed under " +
                        "MIT and Apache 2.0, at your option.",
                    "Where the boundary between host and engine lies for licensing purposes is discussed " +
                        "in LICENSE-NOTES.md, which says plainly that the question is contested rather " +
                        "than settled.",
                ),
        ),
        CreditEntry(
            heading = "No game data",
            lines =
                listOf(
                    "No commercial game data is included with this app, in its releases, or in its build " +
                        "caches. What you play is what you supplied.",
                ),
        ),
    )
