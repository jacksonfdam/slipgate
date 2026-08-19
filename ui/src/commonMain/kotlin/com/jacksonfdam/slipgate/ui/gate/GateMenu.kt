package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp
import com.jacksonfdam.slipgate.ui.settings.Amount
import com.jacksonfdam.slipgate.ui.settings.DisplaySection
import com.jacksonfdam.slipgate.ui.settings.Section
import com.jacksonfdam.slipgate.ui.settings.SettingsController

private const val SCRIM_ALPHA = 0.82f
private const val PANEL_WIDTH = 520
private const val BUTTON_ALPHA = 0.45f
private const val BUTTON_LABEL_ALPHA = 0.8f

/**
 * The menu a player opens over a running game: leave, or change how the game looks and sounds
 * without leaving it.
 *
 * It offers the display and audio controls rather than a copy of them, because a tube setting is
 * worth changing while looking at the picture it changes. What it does not offer is the game's own
 * menu — that belongs to the engine, and the pad's MENU button still reaches it.
 *
 * A scrim rather than a sheet: the frame behind stays visible, which is what tells the player the
 * game is waiting rather than gone.
 */
@Composable
internal fun GateMenu(
    gateTitle: String,
    settings: SettingsController,
    onResume: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorTokens.Void.copy(alpha = SCRIM_ALPHA))
                // Swallows taps that miss the panel, so a stray thumb cannot reach the game behind it.
                .clickable(onClick = onResume),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = PANEL_WIDTH.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ColorTokens.Surface)
                    .border(width = 1.dp, color = ColorTokens.Edge, shape = RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = gateTitle.uppercase(), style = TypeScale.Label, color = accentRamp.hot)
                Text(text = "Paused", style = TypeScale.Headline, color = ColorTokens.Text)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuAction(text = "Resume", emphasis = true, onClick = onResume)
                MenuAction(text = "Leave gate", emphasis = false, onClick = onLeave)
            }

            DisplaySection(settings)

            Section(title = "Audio") {
                Amount("Interface sounds", settings.settings.interfaceVolume) { level ->
                    settings.update { current -> current.copy(interfaceVolume = level) }
                }
            }
        }
    }
}

/** A menu choice, sized for a thumb rather than a cursor. */
@Composable
private fun MenuAction(
    text: String,
    emphasis: Boolean,
    onClick: () -> Unit,
) {
    val ramp = accentRamp
    Text(
        text = text.uppercase(),
        style = TypeScale.Label,
        color = if (emphasis) ramp.hot else ColorTokens.Text,
        modifier =
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(if (emphasis) ramp.dim else ColorTokens.Recess)
                .border(
                    width = 1.dp,
                    color = if (emphasis) ramp.base else ColorTokens.Edge,
                    shape = RoundedCornerShape(2.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/**
 * What opens the menu: one small button at the top left corner.
 *
 * Top left because the pad's own utility row hangs off the top right, and a button that opens the
 * shell has no business sitting next to the one that opens the game's own menu. It is drawn as
 * quietly as the pad is, for the same reason — it is on top of a picture somebody is looking at.
 */
@Composable
internal fun GateMenuButton(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "PAUSE",
        style = TypeScale.Label,
        color = ColorTokens.Text.copy(alpha = BUTTON_LABEL_ALPHA),
        modifier =
            modifier
                .clip(RoundedCornerShape(2.dp))
                .background(ColorTokens.Void.copy(alpha = BUTTON_ALPHA))
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
