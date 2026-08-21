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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.gamedata.ShelfFile
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.ui.data.RemoteShelfController
import com.jacksonfdam.slipgate.ui.data.RemoteShelfState
import com.jacksonfdam.slipgate.ui.data.describe
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalAccentRamp
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp
import kotlinx.coroutines.launch

/**
 * Which gate is showing its shelf, and what the player typed to narrow it.
 *
 * A holder rather than two `var`s in the screen because the list is emitted as the settings column's
 * own items: the row that opens it and the rows it opens are siblings, so the state they share cannot
 * live inside either.
 */
internal class ShelfBrowsing {
    internal var gate: String? by mutableStateOf(null)
        private set

    internal var filter: String by mutableStateOf("")
        private set

    internal fun showing(gateId: String): Boolean = gate == gateId

    /** Opens this gate's shelf, or closes it when it is the one already open. */
    internal fun toggle(gateId: String) {
        filter = ""
        gate = if (gate == gateId) null else gateId
    }

    internal fun narrow(typed: String) {
        filter = typed
    }
}

/** What a gate's file row can ask the app to do. */
public class GateFileRoutes(
    internal val onAddMaps: (gateId: String) -> Unit = {},
    internal val onRemoveAddOn: (gateId: String, name: String) -> Unit = { _, _ -> },
    internal val onAddFromShelf: (gateId: String, file: ShelfFile) -> Unit = { _, _ -> },
)

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
    remoteShelf: RemoteShelfController,
    installedGates: List<GateDataStatus>,
    version: String,
    modifier: Modifier = Modifier,
    routes: GateFileRoutes = GateFileRoutes(),
) {
    val settings = controller.settings
    val browsing = remember { ShelfBrowsing() }

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
            ShelfSection(controller, remoteShelf)
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

        installedGates.forEach { status ->
            gateFiles(status, remoteShelf.addOns(status.id), browsing, routes)
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
private fun ShelfSection(
    controller: SettingsController,
    remoteShelf: RemoteShelfController,
) {
    val scope = rememberCoroutineScope()
    val address = controller.settings.shelfAddress.orEmpty()

    Section(title = "Data shelf") {
        Entry(
            label = "Beacon or shelf address",
            explanation = "Where your own files are served from. Left empty, nothing is fetched.",
            value = address,
            placeholder = "https://…",
            onChange = { typed ->
                controller.update { it.copy(shelfAddress = typed.takeIf { entered -> entered.isNotBlank() }) }
            },
            // Reached on the keyboard's own done action rather than while typing: every keystroke of a
            // half-typed hostname would be a request to somewhere that does not exist.
            onDone = { scope.launch { remoteShelf.refresh(address, force = true) } },
        )
        Text(text = remoteShelf.state.describe(), style = TypeScale.Label, color = ColorTokens.Muted)
        if (address.isNotBlank() && remoteShelf.state !is RemoteShelfState.Looking) {
            Text(
                text = "CHECK AGAIN",
                style = TypeScale.Label,
                color = LocalAccentRamp.current.hot,
                modifier = Modifier.clickable { scope.launch { remoteShelf.refresh(address, force = true) } },
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
 * One gate's row, and the shelf underneath it when the player opened it.
 *
 * A `LazyListScope` extension rather than a composable, so the map packs are this column's own items:
 * a shelf holding a thousand of them scrolls with the screen instead of inside a box on it, and only
 * the rows on screen are ever composed.
 */
private fun LazyListScope.gateFiles(
    status: GateDataStatus,
    offered: List<ShelfFile>,
    browsing: ShelfBrowsing,
    routes: GateFileRoutes,
) {
    item(key = "gate-${status.id}") {
        GateFiles(
            status = status,
            shelfCount = offered.size,
            browsing = browsing.showing(status.id),
            onAddMaps = routes.onAddMaps,
            onRemoveAddOn = routes.onRemoveAddOn,
            onBrowseShelf = { browsing.toggle(status.id) },
        )
    }

    // Nothing to browse for a gate with no game under it: `-file` needs something to load over, and
    // the row already says so where the routes would be.
    if (!browsing.showing(status.id) || status.addOnsBlockedBecause != null) {
        return
    }

    item(key = "filter-${status.id}") {
        Entry(
            label = "On my shelf",
            explanation = "${offered.size} for this gate. Installed from here, they load like any other.",
            value = browsing.filter,
            placeholder = "narrow the list…",
            onChange = browsing::narrow,
        )
    }

    val shown = offered.filter { file -> file.name.contains(browsing.filter.trim(), ignoreCase = true) }
    items(shown, key = { file -> "${status.id}-${file.path}" }) { file ->
        ShelfAddOn(
            file = file,
            installed = file.name in status.addOns,
            onInstall = { routes.onAddFromShelf(status.id, file) },
        )
    }
    if (shown.isEmpty()) {
        item(key = "empty-${status.id}") {
            Text(
                text = "Nothing on the shelf matches that.",
                style = TypeScale.Label,
                color = ColorTokens.Muted,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/**
 * One gate's files: the game itself, then the maps loaded over it.
 *
 * Add-ons are only offered once the game is installed, because `-file` needs something to load over
 * and a control that cannot work should not be drawn.
 */
@Composable
private fun GateFiles(
    status: GateDataStatus,
    shelfCount: Int,
    browsing: Boolean,
    onAddMaps: (String) -> Unit,
    onRemoveAddOn: (String, String) -> Unit,
    onBrowseShelf: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // The shelf goes first when there is one, because a player who stacked a shelf
                    // put the maps there for this: picking the same file again on each device is the
                    // thing a shelf exists to stop.
                    if (shelfCount > 0) {
                        Text(
                            text = if (browsing) "HIDE MY SHELF" else "FROM MY SHELF ($shelfCount)",
                            style = TypeScale.Label,
                            color = LocalAccentRamp.current.hot,
                            modifier = Modifier.clickable(onClick = onBrowseShelf),
                        )
                    }
                    Text(
                        text = if (shelfCount > 0) "CHOOSE A FILE…" else "ADD MAPS…",
                        style = TypeScale.Label,
                        color = if (shelfCount > 0) ColorTokens.Muted else LocalAccentRamp.current.hot,
                        modifier = Modifier.clickable { onAddMaps(status.id) },
                    )
                }
            }
        }
    }
}

/**
 * One map pack on the player's own shelf, as a line they can install.
 *
 * A pack already installed says so instead of offering itself again: the store would refuse the
 * second copy, and a control that cannot work should not be drawn.
 */
@Composable
private fun ShelfAddOn(
    file: ShelfFile,
    installed: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = file.name, style = TypeScale.Data, color = ColorTokens.Muted)
        if (installed) {
            Text(text = "INSTALLED", style = TypeScale.Label, color = ColorTokens.Muted)
        } else {
            Text(
                text = describeSize(file),
                style = TypeScale.Label,
                color = LocalAccentRamp.current.hot,
                modifier = Modifier.clickable(onClick = onInstall),
            )
        }
    }
}

/** A size a player can act on: kilobytes for a map pack, which is what most of them are. */
private fun describeSize(file: ShelfFile): String {
    val size = file.size ?: return "INSTALL"
    return if (size < BYTES_PER_MEGABYTE) {
        "INSTALL · ${size / BYTES_PER_KILOBYTE} KB"
    } else {
        "INSTALL · ${size / BYTES_PER_MEGABYTE} MB"
    }
}

private const val BYTES_PER_KILOBYTE = 1024L
private const val BYTES_PER_MEGABYTE = 1024L * 1024L

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
