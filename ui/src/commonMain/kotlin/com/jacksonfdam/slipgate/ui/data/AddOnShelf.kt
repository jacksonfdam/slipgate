package com.jacksonfdam.slipgate.ui.data

import com.jacksonfdam.slipgate.host.gamedata.AcquisitionResult
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.addOnStorageName

/**
 * Adding and removing the maps a player loads over a game they already have.
 *
 * An interface rather than the acquisition directly, because the screen that offers it should be
 * testable without a store behind it, and because removing is a store operation while adding is an
 * acquisition one — the screen should not have to know which is which.
 */
public interface AddOnShelf {
    /**
     * Installs [bytes] as an add-on of [gateId] under the name the file arrived with.
     *
     * Returns null when it landed, or the sentence explaining why it did not. A failure is a normal
     * outcome here — a player picking the wrong file is the common case, not an exception.
     */
    public suspend fun add(
        gateId: String,
        name: String,
        bytes: ByteArray,
    ): String?

    /** Removes one add-on, named as the player supplied it. */
    public suspend fun remove(
        gateId: String,
        name: String,
    )
}

/** The shelf as the app actually implements it, over the store and the acquisition it already has. */
public class StoredAddOnShelf(
    private val store: GameDataStore,
    private val acquisition: GameDataAcquisition,
) : AddOnShelf {
    override suspend fun add(
        gateId: String,
        name: String,
        bytes: ByteArray,
    ): String? =
        when (val result = acquisition.installAddOn(gateId, name, bytes)) {
            is AcquisitionResult.Stored -> null
            is AcquisitionResult.Failed -> result.message
            is AcquisitionResult.Refused -> "that file is not game data: ${result.inspection.detail}"
        }

    override suspend fun remove(
        gateId: String,
        name: String,
    ) {
        store.delete(gateId, addOnStorageName(name))
    }
}
