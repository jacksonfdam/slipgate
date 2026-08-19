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
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.TierDetection
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.ui.data.GameDataStage
import com.jacksonfdam.slipgate.ui.gate.GateSurface
import com.jacksonfdam.slipgate.ui.launcher.GateCard
import com.jacksonfdam.slipgate.ui.launcher.LauncherSection
import com.jacksonfdam.slipgate.ui.launcher.LauncherShell
import com.jacksonfdam.slipgate.ui.launcher.LauncherState
import com.jacksonfdam.slipgate.ui.launcher.launcherState
import com.jacksonfdam.slipgate.ui.splash.SplashScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** What the shell is showing. */
private sealed interface Stage {
    /** The cold-start splash, which doubles as the benchmark window. */
    data object Splash : Stage

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
    var stage by remember { mutableStateOf<Stage>(Stage.Splash) }
    var section by remember { mutableStateOf(LauncherSection.Gates) }
    var tier by remember { mutableStateOf<QualityTier?>(null) }
    val scope = rememberCoroutineScope()

    SlipgateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = stage) {
                is Stage.Splash -> {
                    SplashScreen(
                        onFinished = { medianMicros ->
                            tier = medianMicros?.let { TierDetection.detect(it) }
                            scope.launch {
                                stage = Stage.Choosing(launcherState(registry.gates, store))
                            }
                        },
                    )
                }

                is Stage.Stuck -> {
                    BootScreen(message = current.message, platformName = platformInfo.name)
                }

                is Stage.Choosing -> {
                    ChoosingStage(
                        state = current.state,
                        section = section,
                        onSection = { section = it },
                        onMove = { next -> stage = Stage.Choosing(next) },
                        onEnter = { card ->
                            scope.launch {
                                registry.gates
                                    .firstOrNull { gate -> gate.descriptor.id.value == card.id }
                                    ?.let { gate -> stage = openedStage(gate, resolver, store, host) }
                            }
                        },
                        statusLabel =
                            tier?.let { "${platformInfo.name} · ${it.name}" } ?: platformInfo.name,
                    )
                }

                is Stage.NeedsData -> {
                    GameDataStage(
                        gate = current.gate,
                        entry = current.entry,
                        acquisition = acquisition,
                        onInstalled = { scope.launch { stage = openedStage(current.gate, resolver, store, host) } },
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
private fun ChoosingStage(
    state: LauncherState,
    section: LauncherSection,
    onSection: (LauncherSection) -> Unit,
    onMove: (LauncherState) -> Unit,
    onEnter: (GateCard) -> Unit,
    statusLabel: String,
) {
    LauncherShell(
        state = state,
        section = section,
        onSection = onSection,
        onSelect = { index -> onMove(state.moveBy(index - state.selected)) },
        onEnter = onEnter,
        statusLabel = statusLabel,
        modifier = Modifier.fillMaxSize(),
    )
}

/** Everything a chosen gate resolves to: missing data, a running session, or the reason. */
private suspend fun openedStage(
    gate: Gate,
    resolver: BackendResolver,
    store: GameDataStore,
    host: GateHost,
): Stage {
    val gateId = gate.descriptor.id.value
    val outstanding = gate.requirements().unmet(store.names(gateId)).firstOrNull()
    if (outstanding != null) {
        return Stage.NeedsData(gate, outstanding)
    }
    return resolver
        .factoryFor(gate)
        .mapCatching { factory ->
            Stage.Playing(
                session = factory.create(store.mount(gateId), host),
                profile = gate.inputProfile(),
            ) as Stage
        }.getOrElse { failure -> Stage.Stuck(failure.message ?: "the gate did not open") }
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
