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
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.ui.audio.FrameBenchmark
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
    settingsController: SettingsController = koinInject(),
    interfaceAudio: InterfaceAudio = koinInject(),
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Opening) }
    var section by remember { mutableStateOf(LauncherSection.Gates) }
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

    InterfaceRuntime(interfaceAudio, settingsController)

    // A gate owns the device while it runs: the interface goes quiet rather than mixing over it.
    LaunchedEffect(stage) {
        if (stage is Stage.Playing) interfaceAudio.silence() else interfaceAudio.resume()
    }

    // What the player chose reaches the whole interface from one place: the portraits read the tier
    // and the motion setting, and a running gate reads the tube and the picture shape.
    SlipgateTheme(reducedMotion = settingsController.settings.reducedMotion) {
        CompositionLocalProvider(
            LocalQualityTier provides settingsController.activeTier,
            LocalPortraitOctaves provides settingsController.activeTier.portraitOctaves.toFloat(),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (val current = stage) {
                    is Stage.Opening -> {
                        BootScreen(message = "opening gate", platformName = platformInfo.name)
                    }

                    is Stage.Stuck -> {
                        BootScreen(message = current.message, platformName = platformInfo.name)
                    }

                    is Stage.Choosing -> {
                        RackStage(
                            state = current.state,
                            section = section,
                            settings = settingsController,
                            audio = interfaceAudio,
                            statusLabel = platformInfo.name,
                            onSection = { chosen -> section = chosen },
                            onState = { next -> stage = Stage.Choosing(next) },
                            onEnter = { card -> scope.launch { enter(registry.gateFor(card.id)) } },
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
                            crt = settingsController.settings.crt,
                            scaling = settingsController.settings.scaling,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The rack, with the sounds its interactions make.
 *
 * Cues live here rather than inside the shell because they belong to the act of choosing, not to the
 * drawing of it: a focus change tracks the direction the selection moved, entering confirms, and
 * entering a gate that cannot run is refused rather than silently ignored.
 */
@Composable
private fun RackStage(
    state: LauncherState,
    section: LauncherSection,
    settings: SettingsController,
    audio: InterfaceAudio,
    statusLabel: String,
    onSection: (LauncherSection) -> Unit,
    onState: (LauncherState) -> Unit,
    onEnter: (GateCard) -> Unit,
) {
    LauncherShell(
        settings = settings,
        state = state,
        section = section,
        onSection = { chosen ->
            audio.play(if (chosen == LauncherSection.Gates) InterfaceCue.Back else InterfaceCue.Navigate)
            onSection(chosen)
        },
        onSelect = { index ->
            audio.play(InterfaceCue.FocusChange, direction = (index - state.selected).toFloat())
            onState(state.select(index))
        },
        onEnter = { card ->
            audio.play(if (card.isPlayable) InterfaceCue.Confirm else InterfaceCue.Blocked)
            onEnter(card)
        },
        statusLabel = statusLabel,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The interface's own frame loop: it renders the audio the elapsed time owes and measures how long
 * frames actually take, because both want the same clock and neither wants a thread of its own.
 */
@Composable
private fun InterfaceRuntime(
    audio: InterfaceAudio,
    settings: SettingsController,
) {
    val benchmark = remember { FrameBenchmark() }
    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val elapsed = if (previousNanos == 0L) 0L else nanos - previousNanos
                previousNanos = nanos
                audio.volume = settings.settings.interfaceVolume
                audio.pump(elapsed / NANOS_PER_MILLI)
                benchmark.record(elapsed)
                if (settings.measured == null) {
                    benchmark.decide()?.let(settings::recordMeasurement)
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

/** The gate a card stands for. A card the registry cannot name is a bug rather than a state. */
private fun GateRegistry.gateFor(id: String): Gate = gates.first { gate -> gate.descriptor.id.value == id }

private const val NANOS_PER_MILLI = 1_000_000L
