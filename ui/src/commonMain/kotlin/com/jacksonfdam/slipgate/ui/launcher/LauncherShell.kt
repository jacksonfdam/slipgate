package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.SlipgateWordmark
import com.jacksonfdam.slipgate.ui.design.TypeScale

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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ColorTokens.Void)) {
        val compact = maxWidth < COMPACT_BREAKPOINT
        if (compact) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionContent(section, state, onSelect, onEnter, compact = true)
                }
                LauncherBottomBar(section, onSection, statusLabel)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                LauncherRail(section, onSection, statusLabel)
                Box(modifier = Modifier.weight(1f)) {
                    SectionContent(section, state, onSelect, onEnter, compact = false)
                }
            }
        }
    }
}

@Composable
private fun SectionContent(
    section: LauncherSection,
    state: LauncherState,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    compact: Boolean,
) {
    when (section) {
        LauncherSection.Gates -> {
            GatesSection(state, onSelect, onEnter, compact)
        }

        LauncherSection.Settings -> {
            SectionPlaceholder(
                title = "Settings",
                body = "Display, audio, controls and game files arrive with the settings build.",
            )
        }

        LauncherSection.Credits -> {
            SectionPlaceholder(
                title = "Credits",
                body = "Full attribution and licence text arrive with the credits scroller.",
            )
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
