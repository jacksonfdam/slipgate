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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

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
    installedGates: List<GateDataStatus>,
    version: String,
    modifier: Modifier = Modifier,
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = status.title, style = TypeScale.Body, color = ColorTokens.Text)
                Text(text = status.summary, style = TypeScale.Label, color = ColorTokens.Muted)
            }
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
    val title: String,
    val summary: String,
)

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
