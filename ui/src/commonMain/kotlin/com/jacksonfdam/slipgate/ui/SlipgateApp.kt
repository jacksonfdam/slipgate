package com.jacksonfdam.slipgate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.mount
import com.jacksonfdam.slipgate.host.gamedata.unmet
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.ui.data.AcquisitionState
import com.jacksonfdam.slipgate.ui.data.GameDataScreen
import com.jacksonfdam.slipgate.ui.data.asState
import com.jacksonfdam.slipgate.ui.data.rememberFilePicker
import com.jacksonfdam.slipgate.ui.data.take
import com.jacksonfdam.slipgate.ui.gate.GateSurface
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** What the shell is showing. Until the launcher exists, one gate is the whole of it. */
private sealed interface Stage {
    data object Opening : Stage

    data class NeedsData(
        val gate: Gate,
        val entry: DataEntry,
    ) : Stage

    data class Playing(
        val session: GateSession,
        val profile: InputProfile,
    ) : Stage

    data class Stuck(
        val message: String,
    ) : Stage
}

/**
 * Root of the shell. Until the launcher exists it opens the first registered gate directly, asking
 * for the gate's data first when the store has none.
 */
@Composable
public fun SlipgateApp(
    platformInfo: PlatformInfo = koinInject(),
    registry: GateRegistry = koinInject(),
    resolver: BackendResolver = koinInject(),
    host: GateHost = koinInject(),
    store: GameDataStore = koinInject(),
    acquisition: GameDataAcquisition = koinInject(),
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Opening) }
    var progress by remember { mutableStateOf<AcquisitionState>(AcquisitionState.Waiting) }
    val scope = rememberCoroutineScope()

    suspend fun open() {
        val gate = registry.gates.firstOrNull()
        if (gate == null) {
            stage = Stage.Stuck("no gates registered")
            return
        }
        val gateId = gate.descriptor.id.value
        val outstanding = gate.requirements().unmet(store.names(gateId)).firstOrNull()
        stage =
            when {
                outstanding != null -> {
                    Stage.NeedsData(gate, outstanding)
                }

                else -> {
                    resolver
                        .factoryFor(gate)
                        .mapCatching { factory ->
                            Stage.Playing(
                                session = factory.create(store.mount(gateId), host),
                                profile = gate.inputProfile(),
                            )
                        }.getOrElse { failure -> Stage.Stuck(failure.message ?: "the gate did not open") }
                }
            }
    }

    LaunchedEffect(registry) { open() }

    SlipgateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = stage) {
                is Stage.Opening -> {
                    BootScreen(message = "opening gate", platformName = platformInfo.name)
                }

                is Stage.Stuck -> {
                    BootScreen(message = current.message, platformName = platformInfo.name)
                }

                is Stage.NeedsData -> {
                    val gateId = current.gate.descriptor.id.value
                    val engine = current.gate.descriptor.engine
                    // Both routes only report; the effect below is what reacts to an install, so
                    // there is one place that decides the gate is ready to open.
                    val supply =
                        rememberFilePicker { file ->
                            scope.launch {
                                progress = AcquisitionState.Working(received = 0, total = null)
                                progress = acquisition.take(gateId, engine, current.entry, file).asState()
                            }
                        }

                    GameDataScreen(
                        gateTitle = current.gate.descriptor.title,
                        engine = engine,
                        entry = current.entry,
                        state = progress,
                        onDownload = { source ->
                            scope.launch {
                                progress = AcquisitionState.Working(received = 0, total = null)
                                progress =
                                    acquisition
                                        .take(gateId, engine, current.entry, source) { received, total ->
                                            progress = AcquisitionState.Working(received, total)
                                        }.asState()
                            }
                        },
                        onSupply = supply,
                        modifier = Modifier.fillMaxSize(),
                    )

                    LaunchedEffect(progress) {
                        if (progress is AcquisitionState.Installed) {
                            open()
                        }
                    }
                }

                is Stage.Playing -> {
                    GateSurface(
                        session = current.session,
                        inputProfile = current.profile,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BootScreen(
    message: String,
    platformName: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SLIPGATE",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = platformName,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
