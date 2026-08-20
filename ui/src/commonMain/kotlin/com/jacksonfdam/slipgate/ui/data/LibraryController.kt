package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jacksonfdam.slipgate.host.gamedata.GameLibrary
import com.jacksonfdam.slipgate.host.gamedata.LibraryFile
import com.jacksonfdam.slipgate.host.gamedata.LibraryListing

/** What the shell knows about the player's own library at the moment. */
public sealed interface LibraryState {
    /** No address is configured, so there is nothing to reach and nothing to report. */
    public data object Unset : LibraryState

    public data object Looking : LibraryState

    public data class Ready(
        val listing: LibraryListing.Open,
    ) : LibraryState

    /** An address is configured and did not work out. The message is what a player is owed. */
    public data class Missing(
        val message: String,
    ) : LibraryState
}

/**
 * Asks the configured address what it holds, once, and remembers the answer for the session.
 *
 * Once rather than per screen, because the launcher opens on the rack and a gate that needs data is
 * two taps away: the list should already be there by then. Re-asked when the address changes, or when
 * the player asks — a NAS that was off when the app started is the ordinary case, not an error.
 */
public class LibraryController(
    private val library: GameLibrary,
) {
    public var state: LibraryState by mutableStateOf(LibraryState.Unset)
        private set

    private var asked: String? = null

    /** The library, when one answered. Null covers every other state, which is what a screen wants. */
    public val listing: LibraryListing.Open?
        get() = (state as? LibraryState.Ready)?.listing

    /** What this library offers [gate] as a game to boot, or nothing at all. */
    public fun bootable(gate: String): List<LibraryFile> = listing?.bootable(gate) ?: emptyList()

    /**
     * Reaches the address, unless it has already been reached and nothing has changed.
     *
     * [force] is what the button in Settings passes: the player looking at "did not answer" and
     * turning the NAS on wants the same address tried again, not to be told it was tried once.
     */
    public suspend fun refresh(
        address: String?,
        force: Boolean = false,
    ) {
        val wanted = address?.trim()?.takeIf { it.isNotEmpty() }
        if (wanted == null) {
            asked = null
            state = LibraryState.Unset
            return
        }
        if (!force && wanted == asked && state !is LibraryState.Missing) {
            return
        }

        asked = wanted
        state = LibraryState.Looking
        state =
            when (val listing = library.open(wanted)) {
                is LibraryListing.Open -> LibraryState.Ready(listing)
                is LibraryListing.Unreachable -> LibraryState.Missing(listing.message)
            }
    }
}

/** The sentence Settings shows under the address field. */
public fun LibraryState.describe(): String =
    when (this) {
        LibraryState.Unset -> {
            "Not set. Your own files stay on whichever device you put them on."
        }

        LibraryState.Looking -> {
            "Looking…"
        }

        is LibraryState.Missing -> {
            message
        }

        is LibraryState.Ready -> {
            val files = listing.files.size
            val where = listing.base
            val announced = listing.publishedAt?.let { ", announced $it" } ?: ""
            "$files ${if (files == 1) "file" else "files"} at $where$announced"
        }
    }
