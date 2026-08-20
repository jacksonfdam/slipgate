package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.credits.CreditsScreen
import com.jacksonfdam.slipgate.ui.data.RemoteShelfController
import com.jacksonfdam.slipgate.ui.design.Backdrops
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalAccentRamp
import com.jacksonfdam.slipgate.ui.design.SlipgateWordmark
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.rememberBackdrop
import com.jacksonfdam.slipgate.ui.settings.GateDataStatus
import com.jacksonfdam.slipgate.ui.settings.SettingsController
import com.jacksonfdam.slipgate.ui.settings.SettingsScreen

/**
 * The launcher shell from the layout spec: rail, stage, rack. Below [COMPACT_BREAKPOINT]
 * the rail collapses to a bottom bar and the rack becomes a vertical list under the
 * stage. Landscape is the primary play orientation; both must work.
 */
@Composable
public fun LauncherShell(
    state: LauncherState,
    section: LauncherSection,
    onSection: (LauncherSection) -> Unit,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    statusLabel: String,
    settings: SettingsController,
    remoteShelf: RemoteShelfController,
    modifier: Modifier = Modifier,
    onAddMaps: (gateId: String) -> Unit = {},
    onRemoveAddOn: (gateId: String, name: String) -> Unit = { _, _ -> },
) {
    // Every surface under the shell draws in the focused gate's own accent: rail, chips, cards,
    // portraits. One provider, so the whole interface recolours with the selection.
    CompositionLocalProvider(LocalAccentRamp provides rampFor(state.current)) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(ColorTokens.Void)) {
            ShellGround(section = section, focusedGateId = state.current?.id)
            val compact = maxWidth < COMPACT_BREAKPOINT
            if (compact) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SectionContent(
                            section = section,
                            state = state,
                            onSelect = onSelect,
                            onEnter = onEnter,
                            settings = settings,
                            remoteShelf = remoteShelf,
                            statusLabel = statusLabel,
                            onAddMaps = onAddMaps,
                            onRemoveAddOn = onRemoveAddOn,
                            compact = true,
                        )
                    }
                    LauncherBottomBar(section, onSection, statusLabel)
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LauncherRail(section, onSection, statusLabel)
                    Box(modifier = Modifier.weight(1f)) {
                        SectionContent(
                            section = section,
                            state = state,
                            onSelect = onSelect,
                            onEnter = onEnter,
                            settings = settings,
                            remoteShelf = remoteShelf,
                            statusLabel = statusLabel,
                            onAddMaps = onAddMaps,
                            onRemoveAddOn = onRemoveAddOn,
                            compact = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the shell stands on: the painted backdrop, and the scrim that keeps text readable over it.
 *
 * The backdrop is the focused gate's own scene behind the rack, and a scene of its own behind
 * Settings and Credits. Width leads and height crops, centred, so the art survives every aspect.
 * Until the image is decoded the attract fire holds the ground, so the shell never sits on flat void.
 */
@Composable
private fun BoxScope.ShellGround(
    section: LauncherSection,
    focusedGateId: String?,
) {
    val backdropName =
        when (section) {
            LauncherSection.Gates -> Backdrops.forGate(focusedGateId)
            LauncherSection.Settings -> Backdrops.SETTINGS
            LauncherSection.Credits -> Backdrops.CREDITS
        }
    Crossfade(targetState = backdropName, modifier = Modifier.matchParentSize()) { name ->
        val backdrop = rememberBackdrop(name)
        if (backdrop == null) {
            AttractBackground(modifier = Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    // The panels and the rail have to stay readable over the art, so it sinks toward the void at the
    // bottom where the text lives.
    Box(
        modifier =
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            ColorTokens.Void.copy(alpha = SCRIM_TOP_ALPHA),
                            ColorTokens.Void.copy(alpha = SCRIM_BOTTOM_ALPHA),
                        ),
                ),
            ),
    )
}

@Composable
private fun SectionContent(
    section: LauncherSection,
    state: LauncherState,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    settings: SettingsController,
    remoteShelf: RemoteShelfController,
    statusLabel: String,
    onAddMaps: (gateId: String) -> Unit,
    onRemoveAddOn: (gateId: String, name: String) -> Unit,
    compact: Boolean,
) {
    when (section) {
        LauncherSection.Gates -> {
            GatesSection(state, onSelect, onEnter, compact)
        }

        LauncherSection.Settings -> {
            SettingsScreen(
                controller = settings,
                remoteShelf = remoteShelf,
                installedGates = state.cards.map { card -> card.dataStatus() },
                version = statusLabel,
                onAddMaps = onAddMaps,
                onRemoveAddOn = onRemoveAddOn,
            )
        }

        LauncherSection.Credits -> {
            CreditsScreen()
        }
    }
}

@Composable
private fun GatesSection(
    state: LauncherState,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    compact: Boolean,
) {
    val current = state.current
    if (current == null) {
        SectionPlaceholder(
            title = "No gates",
            body = "No gates are registered in this build.",
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SlipgateWordmark(modifier = Modifier.height(WORDMARK_HEIGHT))
        if (compact) {
            StagePanel(card = current, keepAspect = true)
            GateList(state, onSelect, onEnter, modifier = Modifier.weight(1f))
        } else {
            StagePanel(card = current, modifier = Modifier.weight(1f).fillMaxWidth())
            GateRack(state, onSelect, onEnter)
            Text(
                text = describe(current),
                style = TypeScale.Body,
                color = ColorTokens.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun SectionPlaceholder(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = TypeScale.Display, color = ColorTokens.Text)
        Text(text = body, style = TypeScale.Body, color = ColorTokens.Muted)
    }
}

private val COMPACT_BREAKPOINT = 600.dp
private val WORDMARK_HEIGHT = 14.dp
private const val SCRIM_TOP_ALPHA = 0.35f
private const val SCRIM_BOTTOM_ALPHA = 0.72f

/** What Settings says about one gate's files, in the words the rack already uses. */
private fun GateCard.dataStatus(): GateDataStatus =
    GateDataStatus(
        id = id,
        title = descriptor.title,
        summary =
            when (availability) {
                GateAvailability.Installed -> "installed"
                is GateAvailability.NeedsData -> "not installed"
                is GateAvailability.UserSuppliedOnly -> "waiting for your files"
            },
        addOns = addOns,
        // Maps load over a game; there has to be one there first.
        addOnsBlockedBecause = "install the game first".takeUnless { isPlayable },
    )
