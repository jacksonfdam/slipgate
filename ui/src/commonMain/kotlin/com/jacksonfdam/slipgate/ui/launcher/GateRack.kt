package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

private const val SELECTED_SCALE = 1f
private const val RESTING_SCALE = 0.88f

/** How much the portrait tightens its core: the selected card is the loud one. */
private const val SELECTED_FOCUS = 1f
private const val RESTING_FOCUS = 0.35f
private const val COVER_ASPECT = 16f / 10f
private const val CARD_WIDTH_DP = 156

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
            GateCardView(
                card = card,
                selected = index == state.selected,
                onClick = { if (index == state.selected) onEnter(card) else onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
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

    Column(
        modifier =
            modifier
                .scale(scale)
                .clickable(onClick = onClick)
                // One label rather than the three texts inside it: read aloud, "Mars Doom needs Doom
                // IWAD" is a card describing itself, and the fragments are not.
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken(card)
                    // Whether this is the card the rack is on, which is the other half of what a
                    // listener needs: what it is, and whether it is the one selected.
                    this.selected = selected
                }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorTokens.Recess)
                    .border(
                        width = 1.dp,
                        color = if (selected) ramp.base else ColorTokens.Edge,
                        shape = RoundedCornerShape(12.dp),
                    ),
            contentAlignment = Alignment.BottomStart,
        ) {
            // The cover is the gate's own portrait, calmer on a resting card than on the stage.
            GatePortrait(
                card = card,
                focus = if (selected) SELECTED_FOCUS else RESTING_FOCUS,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                text = card.descriptor.title.uppercase(),
                style = TypeScale.Headline,
                color = ColorTokens.Text,
                modifier = Modifier.padding(12.dp),
            )
        }
        Text(
            text = card.descriptor.engine.uppercase(),
            style = TypeScale.Label,
            color = ColorTokens.Muted,
        )
    }
}

/**
 * What a screen reader says about a card: its name, its engine, and whether it can be entered.
 *
 * The same three facts the card shows, in the order somebody listening needs them — the name first,
 * because that is what they are looking for, and the state last, because that is what stops them.
 */
internal fun spoken(card: GateCard): String =
    listOf(
        card.descriptor.title,
        card.descriptor.engine,
        when (val availability = card.availability) {
            GateAvailability.Installed -> {
                "ready to play"
            }

            is GateAvailability.NeedsData -> {
                "needs ${availability.missing.displayName}"
            }

            is GateAvailability.UserSuppliedOnly -> {
                "needs ${availability.missing.displayName}, which only you can supply"
            }
        },
    ).joinToString(", ")

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
