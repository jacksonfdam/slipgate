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
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.host.runtime.SessionSaves
import com.jacksonfdam.slipgate.ui.audio.InterfaceAudio
import com.jacksonfdam.slipgate.ui.audio.ambientKeyOf
import com.jacksonfdam.slipgate.ui.data.GameDataStage
import com.jacksonfdam.slipgate.ui.gate.GateMenu
import com.jacksonfdam.slipgate.ui.gate.GateMenuButton
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
        val title: String,
        val menuOpen: Boolean = false,
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
    hosts: SessionHosts = koinInject(),
    store: GameDataStore = koinInject(),
    acquisition: GameDataAcquisition = koinInject(),
    settings: SettingsController = koinInject(),
    audio: InterfaceAudio = koinInject(),
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Splash) }
    var section by remember { mutableStateOf(LauncherSection.Gates) }
    val shell =
        remember(registry, resolver, hosts, store, acquisition, settings, audio) {
            Shell(
                gates = Gates(registry, resolver, hosts, store),
                acquisition = acquisition,
                settings = settings,
                audio = audio,
            )
        }

    // The interface's voice: cues, and the bed in the focused gate's key. A gate paused behind its
    // own menu is not playing, so the shell may be heard again.
    val playing = stage as? Stage.Playing
    InterfaceVoice(
        audio = audio,
        settings = settings,
        quiet = playing != null && !playing.menuOpen,
        focused = (stage as? Stage.Choosing)?.state?.current,
    )

    SlipgateTheme(reducedMotion = settings.settings.reducedMotion) {
        CompositionLocalProvider(
            LocalQualityTier provides settings.activeTier,
            LocalPortraitOctaves provides settings.activeTier.portraitOctaves.toFloat(),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                StageSurface(
                    stage = stage,
                    section = section,
                    shell = shell,
                    platformName = platformInfo.name,
                    onStage = { next -> stage = next },
                    onSection = { chosen -> section = chosen },
                )
            }
        }
    }
}

/** What it takes to open a gate: the registry to find it, and the three services it runs on. */
private class Gates(
    val registry: GateRegistry,
    val resolver: BackendResolver,
    val hosts: SessionHosts,
    val store: GameDataStore,
)

/** What the shell needs beyond the gates themselves, kept together so a stage reads in one breath. */
private class Shell(
    val gates: Gates,
    val acquisition: GameDataAcquisition,
    val settings: SettingsController,
    val audio: InterfaceAudio,
)

/** Whichever stage the shell is on, drawn with what that stage needs. */
@Composable
private fun StageSurface(
    stage: Stage,
    section: LauncherSection,
    shell: Shell,
    platformName: String,
    onStage: (Stage) -> Unit,
    onSection: (LauncherSection) -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (stage) {
        is Stage.Splash -> {
            SplashStage(
                settings = shell.settings,
                onReady = { rack -> onStage(Stage.Choosing(rack)) },
                rack = { launcherState(shell.gates.registry.gates, shell.gates.store) },
            )
        }

        is Stage.Stuck -> {
            BootScreen(message = stage.message, platformName = platformName)
        }

        is Stage.Choosing -> {
            ChoosingStage(
                state = stage.state,
                section = section,
                settings = shell.settings,
                audio = shell.audio,
                onSection = onSection,
                onMove = { next -> onStage(Stage.Choosing(next)) },
                onEnter = { card ->
                    scope.launch {
                        shell.gates.registry.gates
                            .firstOrNull { gate -> gate.descriptor.id.value == card.id }
                            ?.let { gate -> onStage(shell.gates.openedStage(gate)) }
                    }
                },
                statusLabel = "$platformName · ${shell.settings.activeTier.name}",
            )
        }

        is Stage.NeedsData -> {
            GameDataStage(
                gate = stage.gate,
                entry = stage.entry,
                acquisition = shell.acquisition,
                onInstalled = { scope.launch { onStage(shell.gates.openedStage(stage.gate)) } },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is Stage.Playing -> {
            PlayingStage(
                stage = stage,
                settings = shell.settings,
                onMenu = { open -> onStage(stage.copy(menuOpen = open)) },
                onLeave = {
                    scope.launch {
                        // Before closing: what the engine wrote is only the player's once it is out of
                        // the module, and the module goes with the session.
                        (stage.session as? SessionSaves)?.keepSaves()
                        stage.session.close()
                        onSection(LauncherSection.Gates)
                        onStage(Stage.Choosing(launcherState(shell.gates.registry.gates, shell.gates.store)))
                    }
                },
            )
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
    quiet: Boolean,
    focused: GateCard?,
) {
    LaunchedEffect(quiet) {
        if (quiet) audio.silence() else audio.resume()
    }

    // The bed follows the selection: the focused gate's palette gives it a root, its identity a mode,
    // and the tier decides how many voices it may use.
    LaunchedEffect(focused?.id, focused?.accent, settings.activeTier) {
        focused?.let { card -> audio.setAmbientKey(ambientKeyOf(card)) }
        audio.setAmbientVoices(settings.activeTier.ambientVoices)
    }
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
 * A running gate, drawn through the tube and picture shape the player chose, with the menu that
 * leaves it.
 *
 * The gate stops stepping while the menu is open rather than playing on behind it, and the frame it
 * stopped on stays on screen: what a player left is what they come back to.
 */
@Composable
private fun PlayingStage(
    stage: Stage.Playing,
    settings: SettingsController,
    onMenu: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        GateSurface(
            session = stage.session,
            inputProfile = stage.profile,
            crt = settings.settings.crt,
            scaling = settings.settings.scaling,
            paused = stage.menuOpen,
            modifier = Modifier.fillMaxSize(),
        )
        if (stage.menuOpen) {
            GateMenu(
                gateTitle = stage.title,
                settings = settings,
                onResume = { onMenu(false) },
                onLeave = onLeave,
            )
        } else {
            GateMenuButton(
                onOpen = { onMenu(true) },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
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
private suspend fun Gates.openedStage(gate: Gate): Stage {
    val gateId = gate.descriptor.id.value
    val outstanding = gate.requirements().unmet(store.names(gateId)).firstOrNull()
    if (outstanding != null) {
        return Stage.NeedsData(gate, outstanding)
    }
    return resolver
        .factoryFor(gate)
        .mapCatching { factory ->
            Stage.Playing(
                session = factory.create(store.mount(gateId), hosts.forGate(gateId)),
                profile = gate.inputProfile(),
                title = gate.descriptor.title,
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
