package com.jacksonfdam.slipgate.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundTest {
    @Test
    fun aFocusedGateOnScreenWithNoMenuKeepsStepping() {
        assertFalse(gatePaused(menuOpen = false, windowFocused = true, inForeground = true))
    }

    @Test
    fun anOpenMenuPausesTheGate() {
        assertTrue(gatePaused(menuOpen = true, windowFocused = true, inForeground = true))
    }

    @Test
    fun aLostWindowFocusPausesTheGate() {
        assertTrue(gatePaused(menuOpen = false, windowFocused = false, inForeground = true))
    }

    @Test
    fun leavingTheScreenPausesTheGateEvenWhileTheWindowStillReadsAsFocused() {
        assertTrue(gatePaused(menuOpen = false, windowFocused = true, inForeground = false))
    }

    @Test
    fun theShellSpeaksWhileNoGateIsRunning() {
        assertFalse(interfaceQuiet(inForeground = true, gateRunning = false, gateMenuOpen = false))
    }

    @Test
    fun aRunningGateTakesTheDevice() {
        assertTrue(interfaceQuiet(inForeground = true, gateRunning = true, gateMenuOpen = false))
    }

    @Test
    fun aGatePausedBehindItsMenuGivesTheDeviceBack() {
        assertFalse(interfaceQuiet(inForeground = true, gateRunning = true, gateMenuOpen = true))
    }

    @Test
    fun goingOffScreenSilencesTheShell() {
        assertTrue(interfaceQuiet(inForeground = false, gateRunning = false, gateMenuOpen = false))
    }

    @Test
    fun goingOffScreenSilencesAGateMenuToo() {
        assertTrue(interfaceQuiet(inForeground = false, gateRunning = true, gateMenuOpen = true))
    }
}
