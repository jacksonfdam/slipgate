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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.audio.synth.InterfaceCue
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
import com.jacksonfdam.slipgate.ui.audio.InterfaceAudio
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

private const val NANOS_PER_MILLI = 1_000_000L

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
    audio: InterfaceAudio = koinInject(),
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Splash) }
    var section by remember { mutableStateOf(LauncherSection.Gates) }
    val scope = rememberCoroutineScope()

    InterfaceVoice(audio, settings)

    // A gate owns the device while it runs: the interface goes quiet rather than mixing over it.
    LaunchedEffect(stage) {
        if (stage is Stage.Playing) audio.silence() else audio.resume()
    }

    SlipgateTheme(reducedMotion = settings.settings.reducedMotion) {
        CompositionLocalProvider(
            LocalQualityTier provides settings.activeTier,
            LocalPortraitOctaves provides settings.activeTier.portraitOctaves.toFloat(),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (val current = stage) {
                    is Stage.Splash -> {
                        SplashScreen(
                            onFinished = { medianMicros ->
                                medianMicros?.let { settings.recordMeasurement(decisionFor(it)) }
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
                            settings = settings,
                            audio = audio,
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
                            onInstalled = {
                                scope.launch { stage = openedStage(current.gate, resolver, store, host) }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is Stage.Playing -> {
                        GateSurface(
                            session = current.session,
                            inputProfile = current.profile,
                            crt = settings.settings.crt,
                            scaling = settings.settings.scaling,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** What the splash measured, as a decision Settings can show its working from. */
private fun decisionFor(medianFrameMicros: Long): TierDecision =
    TierDecision(
        tier = TierDetection.detect(medianFrameMicros),
        medianFrameMicros = medianFrameMicros,
        signals = TierSignals(),
    )

/**
 * The interface's own voice: it renders the audio elapsed time owes, from the frame clock.
 *
 * Paid for by elapsed time rather than by frames, the same way the engine's mixer earns its frames, so
 * a slow frame owes more and a fast one less.
 */
@Composable
private fun InterfaceVoice(
    audio: InterfaceAudio,
    settings: SettingsController,
) {
    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val elapsed = if (previousNanos == 0L) 0L else nanos - previousNanos
                previousNanos = nanos
                audio.volume = settings.settings.interfaceVolume
                audio.pump(elapsed / NANOS_PER_MILLI)
            }
        }
    }
}

/**
 * The rack, with the sounds its interactions make.
 *
 * Cues live here rather than in the shell because they belong to the act of choosing, not to the
 * drawing of it: a focus change tracks the direction the selection moved, entering confirms, and
 * entering a gate that cannot run is refused rather than silently ignored.
 */
@Composable
private fun ChoosingStage(
    state: LauncherState,
    section: LauncherSection,
    settings: SettingsController,
    audio: InterfaceAudio,
    onSection: (LauncherSection) -> Unit,
    onMove: (LauncherState) -> Unit,
    onEnter: (GateCard) -> Unit,
    statusLabel: String,
) {
    LauncherShell(
        state = state,
        section = section,
        settings = settings,
        onSection = { chosen ->
            audio.play(if (chosen == LauncherSection.Gates) InterfaceCue.Back else InterfaceCue.Navigate)
            onSection(chosen)
        },
        onSelect = { index ->
            audio.play(InterfaceCue.FocusChange, direction = (index - state.selected).toFloat())
            onMove(state.select(index))
        },
        onEnter = { card ->
            audio.play(if (card.isPlayable) InterfaceCue.Confirm else InterfaceCue.Blocked)
            onEnter(card)
        },
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

/** The gate a card stands for. A card the registry cannot name is a bug rather than a state. */
private fun GateRegistry.gateFor(id: String): Gate = gates.first { gate -> gate.descriptor.id.value == id }
