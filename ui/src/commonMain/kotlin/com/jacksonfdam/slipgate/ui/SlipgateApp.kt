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
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.jacksonfdam.slipgate.ui.data.AddOnShelf
import com.jacksonfdam.slipgate.ui.data.GameDataStage
import com.jacksonfdam.slipgate.ui.data.StoredAddOnShelf
import com.jacksonfdam.slipgate.ui.data.rememberFilePicker
import com.jacksonfdam.slipgate.ui.gate.GateMenu
import com.jacksonfdam.slipgate.ui.gate.GateMenuButton
import com.jacksonfdam.slipgate.ui.gate.GateSurface
import com.jacksonfdam.slipgate.ui.gate.LaunchWarp
import com.jacksonfdam.slipgate.ui.launcher.GateCard
import com.jacksonfdam.slipgate.ui.launcher.LauncherSection
import com.jacksonfdam.slipgate.ui.launcher.LauncherShell
import com.jacksonfdam.slipgate.ui.launcher.LauncherState
import com.jacksonfdam.slipgate.ui.launcher.LocalPortraitOctaves
import com.jacksonfdam.slipgate.ui.launcher.LocalQualityTier
import com.jacksonfdam.slipgate.ui.launcher.launcherState
import com.jacksonfdam.slipgate.ui.settings.SettingsController
import com.jacksonfdam.slipgate.ui.splash.SplashScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val NANOS_PER_MILLI = 1_000_000L

/** What the shell is showing. */
internal sealed interface Stage {
    /** The cold-start splash, which doubles as the benchmark window. */
    data object Splash : Stage

    /** The rack. Where the app starts and, once a session can be left, where it returns to. */
    data class Choosing(
        val state: LauncherState,
    ) : Stage

    /**
     * The warp, over the rack the player just left. It lasts as long as the motion tokens allow the
     * launch transition, and the session opens behind it — which is the point: the one animation with
     * real time budgeted for it is also the one that has work to hide.
     */
    data class Launching(
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
                shelf = StoredAddOnShelf(store, acquisition),
                settings = settings,
                audio = audio,
            )
        }

    // The interface's voice: cues, and the bed in the focused gate's key. A gate paused behind its
    // own menu is not playing, so the shell may be heard again — and an app that has gone off screen
    // says nothing at all.
    val playing = stage as? Stage.Playing
    val inForeground = rememberInForeground()
    InterfaceVoice(
        audio = audio,
        settings = settings,
        inForeground = inForeground,
        quiet =
            interfaceQuiet(
                inForeground = inForeground,
                gateRunning = playing != null,
                gateMenuOpen = playing?.menuOpen == true,
            ),
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

/** What the shell needs beyond the gates themselves, kept together so a stage reads in one breath. */
private class Shell(
    val gates: Gates,
    val acquisition: GameDataAcquisition,
    val shelf: AddOnShelf,
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
                shelf = shell.shelf,
                onSection = onSection,
                onMove = { next -> onStage(Stage.Choosing(next)) },
                onEnter = { card -> scope.launch { shell.enter(card, stage.state, onStage) } },
                // The rack is read once rather than watched, so installing or removing an add-on has
                // to say so; otherwise Settings lists a shelf that is no longer there.
                onShelfChanged = { scope.launch { onStage(shell.gates.reread(stage.state.selected)) } },
                statusLabel = "$platformName · ${shell.settings.activeTier.name}",
            )
        }

        is Stage.Launching -> {
            LaunchingStage(stage.state, section, shell, platformName)
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
            // Back opens the menu over the game, and closes it again: the same thing the PAUSE button
            // does, on the gesture a player already has. Leaving the gate stays a deliberate choice.
            SystemBack(enabled = true) { onStage(stage.copy(menuOpen = !stage.menuOpen)) }
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
 * The rack the player just left, with the warp closing over it.
 *
 * The rack is still drawn because the warp is what ends it: a screen that replaced it would lose the
 * card the player was looking at, which is the thing being pulled toward.
 */
@Composable
private fun LaunchingStage(
    state: LauncherState,
    section: LauncherSection,
    shell: Shell,
    platformName: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ChoosingStage(
            state = state,
            section = section,
            settings = shell.settings,
            audio = shell.audio,
            shelf = shell.shelf,
            onSection = {},
            onMove = {},
            onEnter = {},
            // Every other control here is inert for the same reason: this rack is a picture of the
            // one the player left, and the warp is already closing over it.
            onShelfChanged = {},
            statusLabel = platformName,
        )
        LaunchWarp()
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
    inForeground: Boolean,
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
    // Restarted rather than left running when the app goes off screen: the frame clock keeps firing
    // there, and a loop that keeps rendering audio nobody can hear is a loop that keeps a phone awake.
    LaunchedEffect(inForeground) {
        if (!inForeground) {
            return@LaunchedEffect
        }
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
 *
 * It stops for the same reason when the window loses focus — the player took a call, pulled the
 * notification shade down, switched tabs — and when the app leaves the screen altogether, which is
 * the case focus alone does not answer for. An engine stepping into a screen nobody is looking at
 * costs a phone its battery and the player the monsters that reached them while they were away.
 */
@Composable
private fun PlayingStage(
    stage: Stage.Playing,
    settings: SettingsController,
    onMenu: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val inForeground = rememberInForeground()
    Box(modifier = Modifier.fillMaxSize()) {
        GateSurface(
            session = stage.session,
            inputProfile = stage.profile,
            crt = settings.settings.crt,
            scaling = settings.settings.scaling,
            paused =
                gatePaused(
                    menuOpen = stage.menuOpen,
                    windowFocused = focused,
                    inForeground = inForeground,
                ),
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
    shelf: AddOnShelf,
    onSection: (LauncherSection) -> Unit,
    onMove: (LauncherState) -> Unit,
    onEnter: (GateCard) -> Unit,
    onShelfChanged: () -> Unit,
    statusLabel: String,
) {
    val scope = rememberCoroutineScope()

    // Which gate the picker is for. Held across the pick because the file arrives from the platform
    // long after the button that asked for it has gone.
    var addingTo by remember { mutableStateOf<String?>(null) }
    val pickMaps =
        rememberFilePicker { file ->
            val gateId = addingTo ?: return@rememberFilePicker
            addingTo = null
            scope.launch {
                val problem = shelf.add(gateId, file.name, file.bytes)
                audio.play(if (problem == null) InterfaceCue.Confirm else InterfaceCue.Blocked)
                onShelfChanged()
            }
        }

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
        onAddMaps = { gateId ->
            addingTo = gateId
            pickMaps()
        },
        onRemoveAddOn = { gateId, name ->
            scope.launch {
                shelf.remove(gateId, name)
                audio.play(InterfaceCue.Back)
                onShelfChanged()
            }
        },
        statusLabel = statusLabel,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Launches a gate: the warp goes up, the session opens behind it, and the shell moves on when both are
 * done. Whichever is slower is what the player waits for, and neither is hidden behind the other.
 */
private suspend fun Shell.enter(
    card: GateCard,
    from: LauncherState,
    onStage: (Stage) -> Unit,
) {
    val gate = gates.registry.gates.firstOrNull { entry -> entry.descriptor.id.value == card.id } ?: return
    coroutineScope {
        onStage(Stage.Launching(from))
        val opened = async { gates.openedStage(gate) }
        delay(settings.launchDurationMillis.toLong())
        onStage(opened.await())
    }
}
