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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.jacksonfdam.slipgate.host.graphics.core.TierDecision
import com.jacksonfdam.slipgate.host.graphics.core.TierDetection
import com.jacksonfdam.slipgate.host.graphics.core.TierSignals
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
import com.jacksonfdam.slipgate.ui.launcher.LocalPortraitOctaves
import com.jacksonfdam.slipgate.ui.launcher.LocalQualityTier
import com.jacksonfdam.slipgate.ui.launcher.launcherState
import com.jacksonfdam.slipgate.ui.settings.SettingsController
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
 *
 * What the player chose reaches everything from here — the portraits read the tier and the motion
 * setting, a running gate reads the tube and the picture shape, and the interface's own sounds read
 * their volume.
 */
@Composable
public fun SlipgateApp(
    platformInfo: PlatformInfo = koinInject(),
    registry: GateRegistry = koinInject(),
    resolver: BackendResolver = koinInject(),
    host: GateHost = koinInject(),
    store: GameDataStore = koinInject(),
    acquisition: GameDataAcquisition = koinInject(),
    settings: SettingsController = koinInject(),
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Splash) }
    var section by remember { mutableStateOf(LauncherSection.Gates) }
    val scope = rememberCoroutineScope()

    SlipgateTheme(reducedMotion = settings.settings.reducedMotion) {
        CompositionLocalProvider(
            LocalQualityTier provides settings.activeTier,
            LocalPortraitOctaves provides settings.activeTier.portraitOctaves.toFloat(),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (val current = stage) {
                    is Stage.Splash -> {
                        SplashStage(
                            settings = settings,
                            onReady = { rack -> stage = Stage.Choosing(rack) },
                            rack = { launcherState(registry.gates, store) },
                        )
                    }

                    is Stage.Stuck -> {
                        BootScreen(message = current.message, platformName = platformInfo.name)
                    }

                    is Stage.Choosing -> {
                        ChoosingStage(
                            state = current.state,
                            section = section,
                            settings = settings,
                            onSection = { section = it },
                            onMove = { next -> stage = Stage.Choosing(next) },
                            onEnter = { card ->
                                scope.launch {
                                    registry.gates
                                        .firstOrNull { gate -> gate.descriptor.id.value == card.id }
                                        ?.let { gate -> stage = openedStage(gate, resolver, store, host) }
                                }
                            },
                            statusLabel = "${platformInfo.name} · ${settings.activeTier.name}",
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
                        PlayingStage(current.session, current.profile, settings)
                    }
                }
            }
        }
    }
}

/**
 * The splash, which is also the benchmark window: what it measures is what Settings reports, and what
 * the portraits are drawn at until the player says otherwise.
 */
@Composable
private fun SplashStage(
    settings: SettingsController,
    onReady: (LauncherState) -> Unit,
    rack: suspend () -> LauncherState,
) {
    val scope = rememberCoroutineScope()
    SplashScreen(
        onFinished = { medianMicros ->
            medianMicros?.let { settings.recordMeasurement(decisionFor(it)) }
            scope.launch { onReady(rack()) }
        },
    )
}

/** What the splash measured, as a decision Settings can show its working from. */
private fun decisionFor(medianFrameMicros: Long): TierDecision =
    TierDecision(
        tier = TierDetection.detect(medianFrameMicros),
        medianFrameMicros = medianFrameMicros,
        signals = TierSignals(),
    )

/** A running gate, drawn through the tube and picture shape the player chose. */
@Composable
private fun PlayingStage(
    session: GateSession,
    profile: InputProfile,
    settings: SettingsController,
) {
    GateSurface(
        session = session,
        inputProfile = profile,
        crt = settings.settings.crt,
        scaling = settings.settings.scaling,
        modifier = Modifier.fillMaxSize(),
    )
}

/** The rack, and the settings the shell under it needs. */
@Composable
private fun ChoosingStage(
    state: LauncherState,
    section: LauncherSection,
    settings: SettingsController,
    onSection: (LauncherSection) -> Unit,
    onMove: (LauncherState) -> Unit,
    onEnter: (GateCard) -> Unit,
    statusLabel: String,
) {
    LauncherShell(
        state = state,
        section = section,
        settings = settings,
        onSection = onSection,
        onSelect = { index -> onMove(state.select(index)) },
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
