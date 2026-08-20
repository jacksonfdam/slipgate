package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.Motion
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

private const val SELECTED_SCALE = 1f
private const val RESTING_SCALE = 0.88f

/**
 * How far the selected card leans, in degrees, and how far its cover slides inside the frame.
 *
 * The lean is small on purpose: a card that turned to face the player would be a carousel, and this is
 * a rack. What sells it is the cover moving less than its frame does — parallax is a difference in
 * speed, not an amount of rotation.
 */
private const val SELECTED_TILT_DEGREES = -4f
private const val COVER_PARALLAX_DP = 6f

/** How far the aberration splits, and how far the colours go, when the selection lands. */
private const val ABERRATION_DP = 2f
private const val ABERRATION_ALPHA = 0.55f

/** A card whose data is missing arrives rather than appearing: it has nothing to be entered for. */
private const val DISSOLVE_MS = 240

/** Near the card rather than at the camera's default, so a small lean reads as a tilt. */
private const val CAMERA_DISTANCE = 14f

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
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(if (selected) SELECTED_SCALE else RESTING_SCALE, label = "card scale")
    val lean by
        animateFloatAsState(
            targetValue = if (selected && !reducedMotion) SELECTED_TILT_DEGREES else 0f,
            animationSpec = tween(durationMillis = Motion.duration(Motion.PANEL_MS, reducedMotion)),
            label = "card lean",
        )
    val ramp = accentRamp
    val dissolve = dissolveIn(card.isPlayable, reducedMotion)

    Column(
        modifier =
            modifier
                .scale(scale)
                .graphicsLayer {
                    rotationY = lean
                    // Held near the card rather than at the camera's default, so a four-degree lean
                    // reads as a tilt instead of a slide.
                    cameraDistance = CAMERA_DISTANCE
                }.clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardCover(card = card, selected = selected, dissolve = dissolve, reducedMotion = reducedMotion)
        Text(
            text = card.descriptor.engine.uppercase(),
            style = TypeScale.Label,
            color = ColorTokens.Muted,
        )
    }
}

/** The cover and the frame around it: the picture, the title over it, and the selection's own motion. */
@Composable
private fun CardCover(
    card: GateCard,
    selected: Boolean,
    dissolve: Float,
    reducedMotion: Boolean,
) {
    val ramp = accentRamp
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
        // The cover is the gate's own portrait, calmer on a resting card than on the stage. It
        // slides against its own frame as the card leans, which is the parallax: the frame moves
        // and the picture inside it lags.
        val slide = if (selected && !reducedMotion) COVER_PARALLAX_DP else 0f
        val parallax by
            animateFloatAsState(
                targetValue = slide,
                animationSpec = tween(durationMillis = Motion.duration(Motion.PANEL_MS, reducedMotion)),
                label = "cover parallax",
            )
        GatePortrait(
            card = card,
            focus = if (selected) SELECTED_FOCUS else RESTING_FOCUS,
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = parallax * density
                        alpha = dissolve
                    },
        )
        if (selected) {
            SelectionAberration(shape = RoundedCornerShape(12.dp))
        }
        Text(
            text = card.descriptor.title.uppercase(),
            style = TypeScale.Headline,
            color = ColorTokens.Text,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * The chromatic aberration the specification asks for when the selection lands.
 *
 * A runtime effect cannot sample what is under it here, so the split is drawn rather than filtered:
 * the card's own outline, twice, thrown a couple of points either way in red and cyan and gone inside
 * [Motion.FOCUS_PULSE_MS]. What the eye reads is the edge coming apart and closing again, which is
 * what an aberration is.
 */
@Composable
private fun BoxScope.SelectionAberration(shape: RoundedCornerShape) {
    val reducedMotion = LocalReducedMotion.current
    var pulse by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        // One shot on arrival: this composable exists only while the card is the selected one.
        pulse = 1f
        animate(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = tween(durationMillis = Motion.duration(Motion.FOCUS_PULSE_MS, reducedMotion)),
        ) { value, _ -> pulse = value }
    }
    if (pulse <= 0f) {
        return
    }

    val split = ABERRATION_DP * pulse
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .graphicsLayer { translationX = -split * density }
                .border(1.dp, Color.Red.copy(alpha = ABERRATION_ALPHA * pulse), shape),
    )
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .graphicsLayer { translationX = split * density }
                .border(1.dp, Color.Cyan.copy(alpha = ABERRATION_ALPHA * pulse), shape),
    )
}

/**
 * How present a card's cover is: solid for a gate that can be entered, dissolving in for one that
 * cannot.
 *
 * The specification asks for the dissolve on the cards whose data is not installed, and the reason is
 * legible once you see the rack: a card that fades up reads as something not finished arriving, which
 * is exactly what an uninstalled gate is.
 */
@Composable
private fun dissolveIn(
    playable: Boolean,
    reducedMotion: Boolean,
): Float {
    if (playable) {
        return 1f
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = Motion.duration(DISSOLVE_MS, reducedMotion)),
            label = "card dissolve",
        )
    return alpha
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
