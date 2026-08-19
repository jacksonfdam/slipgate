package com.jacksonfdam.slipgate.ui.data

import com.jacksonfdam.slipgate.host.gamedata.AcquisitionRequest
import com.jacksonfdam.slipgate.host.gamedata.AcquisitionResult
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.host.gamedata.GameFlavour
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataSource

/**
 * Which data layouts an engine can boot.
 *
 * Decided from the engine's name because the layouts belong to `host/gamedata` and the gate contract
 * belongs to `host/runtime`: a gate naming a flavour would point the dependency the wrong way. The
 * table is small and each entry is a fact about the engine rather than a preference.
 */
public fun acceptedFlavours(engine: String): Set<GameFlavour> =
    when (engine) {
        "Doom" -> setOf(GameFlavour.DoomEpisodic, GameFlavour.DoomMapped)
        "Heretic" -> setOf(GameFlavour.Heretic)
        "Hexen" -> setOf(GameFlavour.Hexen)
        else -> GameFlavour.entries.toSet()
    }

/**
 * Turns a result into the sentence a player reads.
 *
 * A refusal explains what the file was, because "that did not work" tells someone who just waited for
 * a download nothing they can act on.
 */
public fun AcquisitionResult.asState(): AcquisitionState =
    when (this) {
        is AcquisitionResult.Stored -> {
            AcquisitionState.Installed
        }

        is AcquisitionResult.Failed -> {
            AcquisitionState.Problem(message)
        }

        is AcquisitionResult.Refused -> {
            AcquisitionState.Problem("that file is not game data: ${inspection.detail}")
        }
    }

/** Downloads what [source] offers for [entry] and reports where it got to. */
public suspend fun GameDataAcquisition.take(
    gate: String,
    engine: String,
    entry: DataEntry,
    source: DataSource.FreeDownload,
    onProgress: (Long, Long?) -> Unit,
): AcquisitionResult =
    acquire(
        AcquisitionRequest(
            gate = gate,
            name = entry.key,
            url = source.url,
            accepts = acceptedFlavours(engine),
            archiveEntry = source.archiveEntry,
        ),
        onProgress = onProgress,
    )

/** Installs a file the player supplied for [entry]. */
public suspend fun GameDataAcquisition.take(
    gate: String,
    engine: String,
    entry: DataEntry,
    file: PickedFile,
): AcquisitionResult =
    install(
        gate = gate,
        name = entry.key,
        bytes = file.bytes,
        accepts = acceptedFlavours(engine),
    )
