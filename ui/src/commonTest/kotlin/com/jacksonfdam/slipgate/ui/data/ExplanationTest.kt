package com.jacksonfdam.slipgate.ui.data

import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataSource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the data screen says about where a game's files come from.
 *
 * Two claims worth pinning, because both are about honesty rather than layout: a free replacement is
 * named as a replacement, and a game without one says so instead of leaving a player hunting for a
 * download button that was never there.
 */
class ExplanationTest {
    @Test
    fun aGateWithAFreeReplacementSaysItIsAReplacement() {
        val entry =
            DataEntry(
                key = "heretic.wad",
                displayName = "Heretic IWAD",
                sources =
                    listOf(
                        DataSource.FreeDownload(displayName = "Blasphemer", url = "https://example.test/b.zip"),
                        DataSource.UserSupplied,
                    ),
            )

        val explanation = explain(entry, "Heretic")

        assertTrue(explanation.startsWith("Blasphemer is a freely licensed replacement"), explanation)
        assertTrue(explanation.contains("rather than Heretic itself"), explanation)
    }

    @Test
    fun aGateWithoutOneSaysThereIsNone() {
        val entry =
            DataEntry(
                key = "hexen.wad",
                displayName = "Hexen IWAD",
                sources = listOf(DataSource.UserSupplied),
            )

        val explanation = explain(entry, "Hexen")

        assertTrue(explanation.startsWith("Hexen has no freely licensed replacement"), explanation)
        assertTrue(explanation.contains("Hexen IWAD"), explanation)
    }

    @Test
    fun theFileNeverLeavesTheDevice() {
        val entry =
            DataEntry(
                key = "hexen.wad",
                displayName = "Hexen IWAD",
                sources = listOf(DataSource.UserSupplied),
            )

        assertTrue(explain(entry, "Hexen").contains("stays on this device"), "the promise is not made")
    }
}
