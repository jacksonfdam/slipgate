package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.SlipgateIcon
import com.jacksonfdam.slipgate.ui.design.accentRamp

/** Persistent left rail: three destinations and the status dot. Nothing more, on purpose. */
@Composable
public fun LauncherRail(
    selected: LauncherSection,
    onSelect: (LauncherSection) -> Unit,
    statusLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(RAIL_WIDTH)
                .background(ColorTokens.Recess)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LauncherSection.entries.forEach { section ->
            RailItem(section, section == selected, onSelect)
        }
        Spacer(modifier = Modifier.weight(1f))
        StatusDot(statusLabel)
    }
}

/** Compact-width replacement for the rail: same items along the bottom edge. */
@Composable
public fun LauncherBottomBar(
    selected: LauncherSection,
    onSelect: (LauncherSection) -> Unit,
    statusLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RAIL_WIDTH)
                .background(ColorTokens.Recess),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherSection.entries.forEach { section ->
            RailItem(section, section == selected, onSelect)
        }
        StatusDot(statusLabel)
    }
}

@Composable
private fun RailItem(
    section: LauncherSection,
    selected: Boolean,
    onSelect: (LauncherSection) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(TOUCH_TARGET)
                .selectable(selected = selected, onClick = { onSelect(section) })
                .semantics { contentDescription = section.label },
        contentAlignment = Alignment.Center,
    ) {
        SlipgateIcon(
            glyph = section.glyph,
            tint = if (selected) accentRamp.base else ColorTokens.Muted,
        )
    }
}

/** Shows the active backend and quality tier once those exist; a quiet dot until then. */
@Composable
private fun StatusDot(label: String) {
    Box(
        modifier =
            Modifier
                .size(TOUCH_TARGET)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentRamp.dim),
        )
    }
}

private val RAIL_WIDTH = 64.dp
private val TOUCH_TARGET = 48.dp
