package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier

/**
 * The quality tier the interface is drawing at.
 *
 * Provided by the shell so one decision reaches every portrait. It defaults to [QualityTier.Standard]
 * rather than to the best tier: a device that has not been measured yet should not be asked for six
 * octaves of noise on its first frame.
 */
public val LocalQualityTier: ProvidableCompositionLocal<QualityTier> =
    compositionLocalOf { QualityTier.Standard }

/**
 * Octaves the portraits may evaluate, as the shaders take it — a float, because every uniform in the
 * shared portrait block is a float for packing reasons.
 */
public val LocalPortraitOctaves: ProvidableCompositionLocal<Float> =
    compositionLocalOf { QualityTier.Standard.portraitOctaves.toFloat() }
