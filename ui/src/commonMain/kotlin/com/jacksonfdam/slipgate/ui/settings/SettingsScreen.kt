package com.jacksonfdam.slipgate.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.ui.data.LibraryController
import com.jacksonfdam.slipgate.ui.data.LibraryState
import com.jacksonfdam.slipgate.ui.data.describe
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalAccentRamp
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp
import kotlinx.coroutines.launch

/**
 * Settings, in the order the specification lists them: what the player sees first is what they change
 * most.
 *
 * Every control here reaches something the moment it moves — the tier drives the portrait shaders, the
 * tube drives the frame a gate renders through, scaling decides how that frame meets the screen. The
 * sections with nothing to drive yet say so in plain words rather than offering a switch that lies.
 *
 * Controls are described by what they do rather than how: "Sharpen the picture", not the name of a
 * pass. Implementation names belong in diagnostics.
 */
@Composable
internal fun SettingsScreen(
    controller: SettingsController,
    library: LibraryController,
    installedGates: List<GateDataStatus>,
    version: String,
    modifier: Modifier = Modifier,
    onAddMaps: (gateId: String) -> Unit = {},
    onRemoveAddOn: (gateId: String, name: String) -> Unit = { _, _ -> },
) {
    val settings = controller.settings

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Text(text = "Settings", style = TypeScale.Display, color = ColorTokens.Text)
        }

        item {
            DisplaySection(controller)
        }

        item {
            LibrarySection(controller, library)
        }

        item {
            Section(title = "Game files") {
                if (installedGates.isEmpty()) {
                    Text(
                        text = "No gates are registered in this build.",
                        style = TypeScale.Body,
                        color = ColorTokens.Muted,
                    )
                }
            }
        }

        items(installedGates) { status ->
            GateFiles(status = status, onAddMaps = onAddMaps, onRemoveAddOn = onRemoveAddOn)
        }

        item {
            Section(title = "Audio") {
                Amount("Interface sounds", settings.interfaceVolume) { level ->
                    controller.update { it.copy(interfaceVolume = level) }
                }
                Text(
                    text = "A game's own sound is mixed by the engine and follows your device volume.",
                    style = TypeScale.Body,
                    color = ColorTokens.Muted,
                )
            }
        }

        item {
            Section(title = "About") {
                Text(text = version, style = TypeScale.Data, color = ColorTokens.Muted)
                Text(
                    text = "Engine versions and licences are in Credits.",
                    style = TypeScale.Body,
                    color = ColorTokens.Muted,
                )
            }
        }
    }
}

/**
 * Where this device can reach the player's own game data.
 *
 * One field, because one address is all a player should have to carry between devices: it may be a
 * beacon that says where their server is at the moment, or the server itself when they are at home.
 * The line under it is the only report there is, so it says what answered rather than "connected".
 */
@Composable
private fun LibrarySection(
    controller: SettingsController,
    library: LibraryController,
) {
    val scope = rememberCoroutineScope()
    val address = controller.settings.libraryAddress.orEmpty()

    Section(title = "Home library") {
        Entry(
            label = "Beacon or library address",
            explanation = "Where your own files are served from. Left empty, nothing is fetched.",
            value = address,
            placeholder = "https://…",
            onChange = { typed ->
                controller.update { it.copy(libraryAddress = typed.takeIf { entered -> entered.isNotBlank() }) }
            },
            // Reached on the keyboard's own done action rather than while typing: every keystroke of a
            // half-typed hostname would be a request to somewhere that does not exist.
            onDone = { scope.launch { library.refresh(address, force = true) } },
        )
        Text(text = library.state.describe(), style = TypeScale.Label, color = ColorTokens.Muted)
        if (address.isNotBlank() && library.state !is LibraryState.Looking) {
            Text(
                text = "CHECK AGAIN",
                style = TypeScale.Label,
                color = LocalAccentRamp.current.hot,
                modifier = Modifier.clickable { scope.launch { library.refresh(address, force = true) } },
            )
        }
    }
}

/**
 * The controls that change what a frame looks like. Internal rather than private because the menu a
 * player opens mid-game offers the same ones: a tube setting is worth changing while looking at it.
 */
@Composable
internal fun DisplaySection(controller: SettingsController) {
    val settings = controller.settings
    Section(title = "Display") {
        Choice(
            label = "Detail",
            explanation = detailExplanation(controller),
            options = listOf<QualityTier?>(null) + QualityTier.entries,
            selected = settings.qualityOverride,
            name = { tier -> tier?.name?.uppercase() ?: "AUTOMATIC" },
            onSelect = { tier -> controller.update { it.copy(qualityOverride = tier) } },
        )
        Choice(
            label = "Picture shape",
            explanation = "How a 320 by 200 frame meets your screen.",
            options = ScalingMode.entries.toList(),
            selected = settings.scaling,
            name = ::scalingName,
            onSelect = { mode -> controller.update { it.copy(scaling = mode) } },
        )
        Toggle(
            label = "Tube effect",
            explanation = "Curvature, scanlines and grille, as a television of the period had.",
            checked = settings.crt.enabled,
            onChange = { on -> controller.update { it.copy(crt = it.crt.copy(enabled = on)) } },
        )
        if (settings.crt.enabled) {
            TubeSliders(settings.crt) { crt -> controller.update { it.copy(crt = crt) } }
        }
        Toggle(
            label = "Reduced motion",
            explanation = "Holds moving backgrounds still and shortens transitions.",
            checked = settings.reducedMotion,
            onChange = { on -> controller.update { it.copy(reducedMotion = on) } },
        )
    }
}

/** One gate's data situation, as Settings reports it. */
internal data class GateDataStatus(
    val id: String,
    val title: String,
    val summary: String,
    /** The add-ons on this gate's shelf, in the order the engine will load them. */
    val addOns: List<String> = emptyList(),
    /** Whether add-ons can be loaded at all, and when they cannot, the reason a player can act on. */
    val addOnsBlockedBecause: String? = null,
)

/**
 * One gate's files: the game itself, then the maps loaded over it.
 *
 * Add-ons are only offered once the game is installed, because `-file` needs something to load over
 * and a control that cannot work should not be drawn.
 */
@Composable
private fun GateFiles(
    status: GateDataStatus,
    onAddMaps: (String) -> Unit,
    onRemoveAddOn: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = status.title, style = TypeScale.Body, color = ColorTokens.Text)
            Text(text = status.summary, style = TypeScale.Label, color = ColorTokens.Muted)
        }

        status.addOns.forEach { name ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = name, style = TypeScale.Data, color = ColorTokens.Muted)
                Text(
                    text = "REMOVE",
                    style = TypeScale.Label,
                    color = LocalAccentRamp.current.hot,
                    modifier = Modifier.clickable { onRemoveAddOn(status.id, name) },
                )
            }
        }

        when {
            status.addOnsBlockedBecause != null -> {
                Text(
                    text = status.addOnsBlockedBecause,
                    style = TypeScale.Label,
                    color = ColorTokens.Muted,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            else -> {
                Text(
                    text = "ADD MAPS…",
                    style = TypeScale.Label,
                    color = LocalAccentRamp.current.hot,
                    modifier = Modifier.padding(start = 16.dp).clickable { onAddMaps(status.id) },
                )
            }
        }
    }
}

private fun detailExplanation(controller: SettingsController): String {
    val measured = controller.measured
    val using = controller.activeTier.name.uppercase()
    return if (measured == null) {
        "Using $using. This device has not been measured yet."
    } else {
        val millis = measured.medianFrameMicros / MICROS_PER_MILLI
        "Using $using. Measured $millis.${measured.medianFrameMicros % MICROS_PER_MILLI / TENTHS} ms " +
            "a frame, which is ${measured.tier.name.uppercase()}."
    }
}

private fun scalingName(mode: ScalingMode): String =
    when (mode) {
        ScalingMode.Fit -> "AS INTENDED"
        ScalingMode.IntegerScale -> "WHOLE PIXELS"
        ScalingMode.Stretch -> "FILL SCREEN"
        ScalingMode.SharpUpscale -> "SMOOTH EDGES"
    }

@Composable
private fun TubeSliders(
    crt: CrtSettings,
    onChange: (CrtSettings) -> Unit,
) {
    Amount("Curvature", crt.curvature) { onChange(crt.copy(curvature = it)) }
    Amount("Scanlines", crt.scanlines) { onChange(crt.copy(scanlines = it)) }
    Amount("Grille", crt.grille) { onChange(crt.copy(grille = it)) }
    Amount("Glow", crt.bloom) { onChange(crt.copy(bloom = it)) }
    Amount("Corner shade", crt.vignette) { onChange(crt.copy(vignette = it)) }
}

private const val MICROS_PER_MILLI = 1000
private const val TENTHS = 100
