package com.jacksonfdam.slipgate.ui.settings

import com.jacksonfdam.slipgate.host.graphics.core.GraphicsBackendId
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode
import com.jacksonfdam.slipgate.host.graphics.core.TierDecision
import com.jacksonfdam.slipgate.host.graphics.core.TierSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun measurement(tier: QualityTier) =
    TierDecision(
        tier = tier,
        medianFrameMicros = 7_400,
        signals = TierSignals(backend = GraphicsBackendId.Skia),
    )

class SettingsControllerTest {
    private val store = InMemorySettingsStore()
    private val controller = SettingsController(store)

    @Test
    fun aChangeIsKeptAndWrittenThrough() {
        controller.update { it.copy(reducedMotion = true) }

        assertTrue(controller.settings.reducedMotion)
        assertTrue(SettingsController(store).settings.reducedMotion, "the change did not survive")
    }

    @Test
    fun aChangeThatChangesNothingIsNotWritten() {
        controller.update { it }

        assertEquals(SlipgateSettings.Default, controller.settings)
        assertFalse(store.boolean("motion.reduced") == true)
    }

    /** The measurement decides only while the player has not: an override is a decision, not a hint. */
    @Test
    fun theMeasuredTierIsUsedUntilThePlayerChooses() {
        controller.recordMeasurement(measurement(QualityTier.Enhanced))
        assertEquals(QualityTier.Enhanced, controller.activeTier)

        controller.update { it.copy(qualityOverride = QualityTier.Minimal) }
        assertEquals(QualityTier.Minimal, controller.activeTier)

        controller.update { it.copy(qualityOverride = null) }
        assertEquals(QualityTier.Enhanced, controller.activeTier)
    }

    @Test
    fun withoutAMeasurementOrAChoiceTheMiddleTierIsUsed() {
        assertEquals(QualityTier.Standard, controller.activeTier)
    }

    @Test
    fun everyFieldSurvivesARestart() {
        controller.update {
            it.copy(
                qualityOverride = QualityTier.Maximum,
                scaling = ScalingMode.IntegerScale,
                reducedMotion = true,
                crt = it.crt.copy(enabled = false, scanlines = 0.75f),
            )
        }

        val reloaded = SettingsController(store).settings

        assertEquals(QualityTier.Maximum, reloaded.qualityOverride)
        assertEquals(ScalingMode.IntegerScale, reloaded.scaling)
        assertTrue(reloaded.reducedMotion)
        assertFalse(reloaded.crt.enabled)
        assertEquals(0.75f, reloaded.crt.scanlines)
    }

    @Test
    fun anUnrecognisedStoredTierFallsBackToAutomatic() {
        store.putString("quality.tier", "ULTRA")

        assertEquals(null, SettingsController(store).settings.qualityOverride)
    }
}
