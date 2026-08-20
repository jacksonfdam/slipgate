package com.jacksonfdam.slipgate.host.controls

import androidx.compose.ui.input.key.Key
import com.jacksonfdam.slipgate.host.runtime.Axis2
import com.jacksonfdam.slipgate.host.runtime.GateAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardControlsTest {
    private val state = ControlState()
    private val keyboard = KeyboardControls(state)

    @Test
    fun aBoundKeyBecomesAHeldAction() {
        assertTrue(keyboard.onKeyDown(ControlKey.Control))

        assertTrue(GateAction.Fire in state.frame().actions)
    }

    @Test
    fun releasingAKeyReleasesItsAction() {
        keyboard.onKeyDown(ControlKey.Control)
        keyboard.onKeyUp(ControlKey.Control)

        assertFalse(GateAction.Fire in state.frame().actions)
    }

    /** A key the bindings do not claim must be reported as unhandled, so the shell can use it. */
    @Test
    fun anUnboundKeyIsLeftAlone() {
        val sparse =
            KeyboardControls(
                state,
                KeyboardBindings(
                    actions = mapOf(ControlKey.Control to GateAction.Fire),
                    forward = emptySet(),
                    backward = emptySet(),
                    left = emptySet(),
                    right = emptySet(),
                ),
            )

        assertFalse(sparse.onKeyDown(ControlKey.Tab), "Tab is not bound here")
        assertTrue(sparse.onKeyDown(ControlKey.Control), "Control is bound here")
    }

    @Test
    fun holdingForwardMovesForward() {
        keyboard.onKeyDown(ControlKey.W)

        assertEquals(Axis2(x = 0f, y = 1f), state.frame().movement)
    }

    /** The point of tracking keys rather than an axis: opposed keys have to cancel and recover. */
    @Test
    fun opposedKeysCancelAndReleasingOneRestoresTheOther() {
        keyboard.onKeyDown(ControlKey.W)
        keyboard.onKeyDown(ControlKey.S)

        assertEquals(Axis2(x = 0f, y = 0f), state.frame().movement)

        keyboard.onKeyUp(ControlKey.S)

        assertEquals(Axis2(x = 0f, y = 1f), state.frame().movement)
    }

    @Test
    fun arrowsAndLettersAreTheSameMovement() {
        keyboard.onKeyDown(ControlKey.ArrowRight)
        val arrows = state.frame().movement
        keyboard.releaseAll()
        keyboard.onKeyDown(ControlKey.D)

        assertEquals(arrows, state.frame().movement)
    }

    @Test
    fun releasingEverythingClearsTheFrame() {
        keyboard.onKeyDown(ControlKey.Control)
        keyboard.onKeyDown(ControlKey.W)

        keyboard.releaseAll()

        val frame = state.frame()
        assertEquals(Axis2.Zero, frame.movement)
        assertEquals(0, frame.actions.mask)
    }

    /**
     * The keys Compose reports, mapped onto the layer's own vocabulary.
     *
     * Worth pinning because it is the join nobody sees fail: an unmapped key is a key that does
     * nothing, silently, on one platform.
     */
    @Test
    fun everyBoundKeyHasAComposeKeyBehindIt() {
        val mapped =
            listOf(
                Key.DirectionUp,
                Key.DirectionDown,
                Key.DirectionLeft,
                Key.DirectionRight,
                Key.W,
                Key.A,
                Key.S,
                Key.D,
                Key.CtrlLeft,
                Key.CtrlRight,
                Key.Spacebar,
                Key.Comma,
                Key.Period,
                Key.Tab,
                Key.Escape,
                Key.Enter,
                Key.NumPadEnter,
            ).mapNotNull(::controlKeyFor).toSet()

        assertEquals(ControlKey.entries.toSet(), mapped, "a key in the vocabulary reaches nothing")
    }

    @Test
    fun aKeyNobodyBoundIsLeftAlone() {
        assertNull(controlKeyFor(Key.F5))
    }
}
