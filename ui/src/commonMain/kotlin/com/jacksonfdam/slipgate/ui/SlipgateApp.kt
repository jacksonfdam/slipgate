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
import com.jacksonfdam.slipgate.ui.data.GameDataStage
import com.jacksonfdam.slipgate.ui.gate.GateSurface
import com.jacksonfdam.slipgate.ui.launcher.LauncherScreen
import com.jacksonfdam.slipgate.ui.launcher.LauncherState
import com.jacksonfdam.slipgate.ui.launcher.launcherState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** What the shell is showing. */
private sealed interface Stage {
    data object Opening : Stage

    /** The rack. Where the app starts and, once a session can be left, where it returns to. */
    data class Choosing(
        val state: LauncherState,
    ) : Stage

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
 * Root of the shell: the rack of gates, whatever a chosen gate still needs, and then the gate itself.
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
    val scope = rememberCoroutineScope()

    suspend fun enter(gate: Gate) {
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

    suspend fun showRack(selected: String? = null) {
        val rack = launcherState(registry.gates, store)
        stage = Stage.Choosing(selected?.let(rack::select) ?: rack)
    }

    LaunchedEffect(registry) { showRack() }

    SlipgateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = stage) {
                is Stage.Opening -> {
                    BootScreen(message = "opening gate", platformName = platformInfo.name)
                }

                is Stage.Stuck -> {
                    BootScreen(message = current.message, platformName = platformInfo.name)
                }

                is Stage.Choosing -> {
                    LauncherScreen(
                        state = current.state,
                        onSelect = { index ->
                            stage =
                                Stage.Choosing(current.state.moveBy(index - current.state.selected))
                        },
                        onEnter = { card ->
                            scope.launch {
                                registry.gates
                                    .firstOrNull { gate -> gate.descriptor.id.value == card.id }
                                    ?.let { gate -> enter(gate) }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is Stage.NeedsData -> {
                    GameDataStage(
                        gate = current.gate,
                        entry = current.entry,
                        acquisition = acquisition,
                        onInstalled = { scope.launch { enter(current.gate) } },
                        modifier = Modifier.fillMaxSize(),
                    )
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
