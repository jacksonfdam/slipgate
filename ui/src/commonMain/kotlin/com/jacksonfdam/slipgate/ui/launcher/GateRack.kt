package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

private const val SELECTED_SCALE = 1f
private const val RESTING_SCALE = 0.88f
private const val SELECTED_COVER_ALPHA = 1f
private const val RESTING_COVER_ALPHA = 0.45f
private const val COVER_ASPECT = 3f / 4f
private const val CARD_WIDTH_DP = 220

/** Compact cards are laid out as rows so several fit on a phone screen at once. */
private const val LIST_COVER_HEIGHT_DP = 96

/**
 * The rack of gates. Not a list view: cards sit in a row the selection walks along, the
 * selected one stands taller and brighter, and the rest recede. The portrait shaders land
 * on top of this layout rather than replacing it — a card with no cover art still has to
 * read as a card.
 */
@Composable
internal fun GateRack(
    state: LauncherState,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // The selection can move without a touch — a key, a gamepad — so the rack follows it
    // rather than the other way round.
    LaunchedEffect(state.selected) {
        if (state.cards.isNotEmpty()) {
            listState.animateScrollToItem(state.selected)
        }
    }

    LazyRow(
        state = listState,
        // Centred so a rack of one or two gates sits in the middle of the screen rather
        // than clinging to the left edge, which is how a short rack reads as deliberate.
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(state.cards) { index, card ->
            GateCardView(
                card = card,
                selected = index == state.selected,
                onClick = { if (index == state.selected) onEnter(card) else onSelect(index) },
                modifier = Modifier.width(CARD_WIDTH_DP.dp),
            )
        }
    }
}

/** The rack as a vertical list, for widths where a row of cards cannot breathe. */
@Composable
internal fun GateList(
    state: LauncherState,
    onSelect: (Int) -> Unit,
    onEnter: (GateCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(state.cards) { index, card ->
            GateListRow(
                card = card,
                selected = index == state.selected,
                onClick = { if (index == state.selected) onEnter(card) else onSelect(index) },
            )
        }
    }
}

/** One card as a row: cover, then identity beside it, so a phone shows several at once. */
@Composable
private fun GateListRow(
    card: GateCard,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ramp = accentRamp
    val tint by animateColorAsState(if (selected) ramp.hot else ramp.dim)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ColorTokens.Surface)
                .border(
                    width = 1.dp,
                    color = if (selected) ramp.base else ColorTokens.Edge,
                    shape = RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick)
                .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .height(LIST_COVER_HEIGHT_DP.dp)
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cover(tint, card.isPlayable)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = card.descriptor.title.uppercase(),
                style = TypeScale.Headline,
                color = ColorTokens.Text,
            )
            Text(
                text = card.descriptor.engine.uppercase(),
                style = TypeScale.Label,
                color = ColorTokens.Muted,
            )
            Text(
                text = state(card),
                style = TypeScale.Label,
                color = if (card.isPlayable) ramp.hot else ColorTokens.Muted,
            )
        }
    }
}

@Composable
private fun GateCardView(
    card: GateCard,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (selected) SELECTED_SCALE else RESTING_SCALE)
    val ramp = accentRamp
    val target = if (selected) ramp.hot else ramp.dim
    val tint by animateColorAsState(target)

    Column(
        modifier =
            modifier
                .scale(scale)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cover(tint, card.isPlayable)),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = card.descriptor.title.uppercase(),
                style = TypeScale.Headline,
                color = ColorTokens.Text,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = card.descriptor.engine.uppercase(),
                style = TypeScale.Label,
                color = ColorTokens.Muted,
            )
            Text(
                text = state(card),
                style = TypeScale.Label,
                color = if (card.isPlayable) ramp.hot else ColorTokens.Muted,
            )
        }
    }
}

/**
 * The stand-in for cover art, which no gate ships: a gradient in the gate's own accent,
 * dimmed for a gate that cannot be entered yet. Artwork replaces the brush and nothing else.
 */
private fun cover(
    accent: Color,
    playable: Boolean,
): Brush {
    val top = if (playable) accent else accent.copy(alpha = RESTING_COVER_ALPHA)
    return Brush.verticalGradient(listOf(top, Color.Black.copy(alpha = SELECTED_COVER_ALPHA)))
}

/** The card's own one-word state, so a rack reads without the stage above it. */
private fun state(card: GateCard): String =
    when (card.availability) {
        GateAvailability.Installed -> "READY"
        is GateAvailability.NeedsData -> "ADD FILES"
        is GateAvailability.UserSuppliedOnly -> "YOUR FILES"
    }

/** What the line under the rack says about the selected gate. */
internal fun describe(card: GateCard): String =
    when (val availability = card.availability) {
        GateAvailability.Installed -> {
            "ready — tap again to enter"
        }

        is GateAvailability.NeedsData -> {
            "needs ${availability.missing.displayName}"
        }

        is GateAvailability.UserSuppliedOnly -> {
            "needs ${availability.missing.displayName}, which only you can supply"
        }
    }
