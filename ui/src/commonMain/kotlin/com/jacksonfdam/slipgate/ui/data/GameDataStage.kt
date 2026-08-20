package com.jacksonfdam.slipgate.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.Gate
import kotlinx.coroutines.launch

/**
 * Owns the business of getting one gate its data: the screen, the two routes onto it, and what
 * happens when either succeeds.
 *
 * The shell above it only needs to know that the data arrived, which is why [onInstalled] is the
 * whole of its interface. Both routes only report progress; the effect below reacts to an install, so
 * one place decides the gate is ready.
 */
@Composable
internal fun GameDataStage(
    gate: Gate,
    entry: DataEntry,
    acquisition: GameDataAcquisition,
    remoteShelf: RemoteShelfController,
    onInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gateId = gate.descriptor.id.value
    val engine = gate.descriptor.engine
    val scope = rememberCoroutineScope()
    var progress by remember(gateId) { mutableStateOf<AcquisitionState>(AcquisitionState.Waiting) }

    val supply =
        rememberFilePicker { file ->
            scope.launch {
                progress = AcquisitionState.Working(received = 0, total = null)
                progress = acquisition.take(gateId, engine, entry, file).asState()
            }
        }

    GameDataScreen(
        gateTitle = gate.descriptor.title,
        engine = engine,
        entry = entry,
        state = progress,
        onDownload = { source ->
            scope.launch {
                progress = AcquisitionState.Working(received = 0, total = null)
                progress =
                    acquisition
                        .take(gateId, engine, entry, source) { received, total ->
                            progress = AcquisitionState.Working(received, total)
                        }.asState()
            }
        },
        onSupply = supply,
        modifier = modifier,
        // Only what the shelf filed under this gate, and only what a gate could boot from: a map
        // pack offered where the game belongs would be refused after a download rather than before.
        shelfFiles = remoteShelf.bootable(gateId),
        onShelf = { file ->
            val url = remoteShelf.listing?.urlFor(file)
            if (url != null) {
                scope.launch {
                    progress = AcquisitionState.Working(received = 0, total = null)
                    progress =
                        acquisition
                            .takeFromShelf(gateId, engine, entry, url) { received, total ->
                                progress = AcquisitionState.Working(received, total)
                            }.asState()
                }
            }
        },
    )

    LaunchedEffect(progress) {
        if (progress is AcquisitionState.Installed) {
            onInstalled()
        }
    }
}
