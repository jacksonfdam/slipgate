package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jacksonfdam.slipgate.host.gamedata.RemoteShelf
import com.jacksonfdam.slipgate.host.gamedata.ShelfFile
import com.jacksonfdam.slipgate.host.gamedata.ShelfListing

/** What the shell knows about the player's own shelf at the moment. */
public sealed interface RemoteShelfState {
    /** No address is configured, so there is nothing to reach and nothing to report. */
    public data object Unset : RemoteShelfState

    public data object Looking : RemoteShelfState

    public data class Ready(
        val listing: ShelfListing.Open,
    ) : RemoteShelfState

    /** An address is configured and did not work out. The message is what a player is owed. */
    public data class Missing(
        val message: String,
    ) : RemoteShelfState
}

/**
 * Asks the configured address what it holds, once, and remembers the answer for the session.
 *
 * Once rather than per screen, because the launcher opens on the rack and a gate that needs data is
 * two taps away: the list should already be there by then. Re-asked when the address changes, or when
 * the player asks — a NAS that was off when the app started is the ordinary case, not an error.
 */
public class RemoteShelfController(
    private val shelf: RemoteShelf,
) {
    public var state: RemoteShelfState by mutableStateOf(RemoteShelfState.Unset)
        private set

    private var asked: String? = null

    /** The library, when one answered. Null covers every other state, which is what a screen wants. */
    public val listing: ShelfListing.Open?
        get() = (state as? RemoteShelfState.Ready)?.listing

    /** What this library offers [gate] as a game to boot, or nothing at all. */
    public fun bootable(gate: String): List<ShelfFile> = listing?.bootable(gate) ?: emptyList()

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
            state = RemoteShelfState.Unset
            return
        }
        if (!force && wanted == asked && state !is RemoteShelfState.Missing) {
            return
        }

        asked = wanted
        state = RemoteShelfState.Looking
        state =
            when (val listing = shelf.open(wanted)) {
                is ShelfListing.Open -> RemoteShelfState.Ready(listing)
                is ShelfListing.Unreachable -> RemoteShelfState.Missing(listing.message)
            }
    }
}

/** The sentence Settings shows under the address field. */
public fun RemoteShelfState.describe(): String =
    when (this) {
        RemoteShelfState.Unset -> {
            "Not set. Your own files stay on whichever device you put them on."
        }

        RemoteShelfState.Looking -> {
            "Looking…"
        }

        is RemoteShelfState.Missing -> {
            message
        }

        is RemoteShelfState.Ready -> {
            val files = listing.files.size
            val where = listing.base
            val announced = listing.publishedAt?.let { ", announced $it" } ?: ""
            "$files ${if (files == 1) "file" else "files"} at $where$announced"
        }
    }
