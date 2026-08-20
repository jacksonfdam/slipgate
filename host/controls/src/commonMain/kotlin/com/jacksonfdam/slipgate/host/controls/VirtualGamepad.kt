package com.jacksonfdam.slipgate.host.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.InputExtension
import com.jacksonfdam.slipgate.host.runtime.InputProfile

private val PAD_SIZE = 160.dp
private val EDGE_PADDING = 20.dp

// The platform minimum for something a finger has to hit; the drawing inside it can be smaller.
private val EXTENSION_HEIGHT = 48.dp
private val EXTENSION_SPACING = 8.dp

// Clear of the movement wheel's arc and of the diagnostics label under it, so a row of five still
// leaves the middle of the picture alone.
private val EXTENSION_BOTTOM_PADDING = 28.dp
private const val IDLE_ALPHA = 0.30f
private const val PRESSED_ALPHA = 0.55f
private const val LABEL_ALPHA = 0.85f
private const val DEAD_ZONE = 12f

/**
 * Where one action's button sits and how big it is. Trigger actions cluster around the
 * right thumb's resting arc; the utility pair sits out of the way at the top edge.
 */
private class ButtonPlacement(
    val fromTop: Boolean,
    val end: Dp,
    val vertical: Dp,
    val size: Dp,
    val label: String,
)

// Distances are from the corner the button hangs off, so the cluster hugs the bezel the
// thumb already rests on instead of marching across the middle of the picture.
private val placements: Map<GateAction, ButtonPlacement> =
    mapOf(
        GateAction.Fire to
            ButtonPlacement(
                fromTop = false,
                end = 28.dp,
                vertical = 28.dp,
                size = 80.dp,
                label = "FIRE",
            ),
        GateAction.Use to
            ButtonPlacement(
                fromTop = false,
                end = 132.dp,
                vertical = 36.dp,
                size = 60.dp,
                label = "USE",
            ),
        GateAction.NextWeapon to
            ButtonPlacement(fromTop = false, end = 116.dp, vertical = 118.dp, size = 56.dp, label = "›"),
        GateAction.PreviousWeapon to
            ButtonPlacement(fromTop = false, end = 28.dp, vertical = 148.dp, size = 56.dp, label = "‹"),
        GateAction.Jump to
            ButtonPlacement(fromTop = false, end = 216.dp, vertical = 28.dp, size = 60.dp, label = "JUMP"),
        GateAction.Crouch to
            ButtonPlacement(fromTop = false, end = 208.dp, vertical = 112.dp, size = 52.dp, label = "DUCK"),
        GateAction.Map to ButtonPlacement(fromTop = true, end = 84.dp, vertical = 20.dp, size = 48.dp, label = "MAP"),
        GateAction.Menu to ButtonPlacement(fromTop = true, end = 24.dp, vertical = 20.dp, size = 48.dp, label = "MENU"),
        GateAction.Confirm to
            ButtonPlacement(fromTop = true, end = 144.dp, vertical = 20.dp, size = 48.dp, label = "ENTER"),
    )

private val labelStyle =
    TextStyle(
        color = Color.White.copy(alpha = LABEL_ALPHA),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )

/**
 * The touch controls, laid out from the gate's own input profile.
 *
 * A gate that needs four buttons gets four. That is the whole reason `InputProfile` exists: Doom and
 * Hexen do not deserve the same on-screen clutter, and neither should carry the other's.
 */
@Composable
public fun VirtualGamepad(
    profile: InputProfile,
    state: ControlState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MovementPad(
            state = state,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(EDGE_PADDING)
                    .size(PAD_SIZE),
        )
        // The controls this engine has and the shared set does not name: an inventory, flight. A row
        // along the bottom edge rather than in a thumb's arc, because they are chosen deliberately
        // between fights rather than held during one.
        if (profile.extensions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EXTENSION_SPACING),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = EXTENSION_BOTTOM_PADDING),
            ) {
                profile.extensions.forEach { extension ->
                    ExtensionButton(extension = extension, state = state)
                }
            }
        }
        profile.actions.forEach { action ->
            val placement = placements.getValue(action)
            ActionButton(
                action = action,
                state = state,
                placement = placement,
                modifier =
                    if (placement.fromTop) {
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = placement.end, top = placement.vertical)
                    } else {
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = placement.end, bottom = placement.vertical)
                    },
            )
        }
    }
}

/**
 * A thumb pad that reports direction rather than distance.
 *
 * The engines move at their own speed and read a direction, so an analogue magnitude would be
 * thrown away; what matters is that a small wobble does not count as movement.
 */
@Composable
private fun MovementPad(
    state: ControlState,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .background(Color.White.copy(alpha = if (active) PRESSED_ALPHA else IDLE_ALPHA), CircleShape)
                // A thumb wheel has no words of its own, so it says what it is for.
                .semantics { contentDescription = "Movement" }
                .pointerInput(state) {
                    detectDragGestures(
                        onDragStart = { active = true },
                        onDragEnd = {
                            active = false
                            state.moveTo(Axis2.Zero)
                        },
                        onDragCancel = {
                            active = false
                            state.moveTo(Axis2.Zero)
                        },
                    ) { change, _ ->
                        val centre = size.width / 2f
                        val offsetX = change.position.x - centre
                        val offsetY = change.position.y - size.height / 2f
                        state.moveTo(
                            Axis2(
                                x = direction(offsetX),
                                // Screen coordinates grow downwards; forward is up.
                                y = -direction(offsetY),
                            ),
                        )
                    }
                },
    )
}

private fun direction(offset: Float): Float =
    when {
        offset > DEAD_ZONE -> 1f
        offset < -DEAD_ZONE -> -1f
        else -> 0f
    }

/**
 * One engine-specific control, labelled with whatever the gate called it.
 *
 * A rectangle rather than a circle, and off the thumb arcs: the shape says this is not one of the
 * seven buttons every gate has. It reports travel rather than a boolean because that is what an
 * extension is — the session decides how much of it counts as a press.
 */
@Composable
private fun ExtensionButton(
    extension: InputExtension,
    state: ControlState,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(EXTENSION_HEIGHT)
                .background(
                    Color.White.copy(alpha = if (pressed) PRESSED_ALPHA else IDLE_ALPHA),
                    RectangleShape,
                ).padding(horizontal = 12.dp)
                .pointerInput(extension.key, state) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            val down = currentEvent.changes.any { it.pressed }
                            if (down != pressed) {
                                pressed = down
                                state.setExtension(extension.key, if (down) 1f else 0f)
                            }
                        }
                    }
                },
    ) {
        BasicText(text = extension.label, style = labelStyle)
    }
}

@Composable
private fun ActionButton(
    action: GateAction,
    state: ControlState,
    placement: ButtonPlacement,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(placement.size)
                .background(
                    Color.White.copy(alpha = if (pressed) PRESSED_ALPHA else IDLE_ALPHA),
                    CircleShape,
                ).semantics {
                    contentDescription = placement.label
                    role = Role.Button
                }.pointerInput(action, state) {
                    // A press has to survive the finger sliding: releasing on drag would make firing
                    // while turning impossible.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            val down = currentEvent.changes.any { it.pressed }
                            if (down != pressed) {
                                pressed = down
                                state.setHeld(action, down)
                            }
                        }
                    }
                },
    ) {
        BasicText(text = placement.label, style = labelStyle)
    }
}
