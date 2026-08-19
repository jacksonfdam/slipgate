package com.jacksonfdam.slipgate.host.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.InputProfile

private val PAD_SIZE = 132.dp
private val BUTTON_SIZE = 64.dp
private val EDGE_PADDING = 20.dp
private const val IDLE_ALPHA = 0.18f
private const val PRESSED_ALPHA = 0.38f
private const val DEAD_ZONE = 12f

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
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(EDGE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(EDGE_PADDING),
        ) {
            // Ordered so the actions a thumb reaches for most sit nearest the edge.
            profile.actions.sortedBy { it.ordinal }.forEach { action ->
                ActionButton(action = action, state = state)
            }
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

@Composable
private fun ActionButton(
    action: GateAction,
    state: ControlState,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .size(BUTTON_SIZE)
                .background(
                    Color.White.copy(alpha = if (pressed) PRESSED_ALPHA else IDLE_ALPHA),
                    CircleShape,
                ).pointerInput(action, state) {
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
    )
}
