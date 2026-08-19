package com.jacksonfdam.slipgate.ui.credits

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The credits are a licence obligation, not a courtesy, so what they must contain is asserted rather
 * than trusted: a refactor that quietly dropped the GPL notice would be a violation nobody noticed.
 */
class CreditsTest {
    private val lines = credits().flatMap { entry -> entry.lines }.joinToString(" ")

    @Test
    fun theEngineLicenceIsStatedInFull() {
        assertTrue("GNU General Public License" in lines, "no GPL notice")
        assertTrue("version 2" in lines, "the GPL version is not stated")
        assertTrue("WITHOUT ANY WARRANTY" in lines, "the warranty disclaimer is missing")
        assertTrue("gnu.org/licenses" in lines, "the licence is not linked")
    }

    @Test
    fun whereToGetTheEngineSourceIsStated() {
        assertTrue("SOURCES.lock" in lines, "the source of the engine modules is not named")
    }

    @Test
    fun everyOneItOwesIsCredited() {
        listOf(
            "mood",
            "Chasm",
            "Chocolate Doom",
            "id Software",
            "Raven Software",
            "Freedoom",
            "Blasphemer",
        ).forEach { name ->
            assertTrue(name in lines, "$name is not credited")
        }
    }

    @Test
    fun theHostsOwnTermsAreStated() {
        assertTrue("MIT" in lines && "Apache 2.0" in lines, "the host licence is not stated")
    }

    @Test
    fun itSaysNoGameDataShips() {
        assertTrue("No commercial game data" in lines, "the data position is not stated")
    }

    @Test
    fun theLicenceTextIsSetInTheMachineFace() {
        val licence = credits().filter { entry -> entry.heading.contains("licence", ignoreCase = true) }

        assertTrue(licence.isNotEmpty(), "no licence block at all")
        assertTrue(licence.all { entry -> entry.monospaced }, "licence text is not set as data")
    }
}
