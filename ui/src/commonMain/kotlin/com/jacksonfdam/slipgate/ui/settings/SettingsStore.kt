package com.jacksonfdam.slipgate.ui.settings

import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.graphics.core.QualityTier
import com.jacksonfdam.slipgate.host.graphics.core.ScalingMode

/**
 * Where a player's choices survive a restart.
 *
 * Keys and primitives rather than a serialised object: every platform already has a small
 * key-and-value store of its own — preferences, defaults, local storage — and a settings file would
 * mean a serialisation library for four numbers and three flags.
 */
public interface SettingsStore {
    public fun string(key: String): String?

    public fun putString(
        key: String,
        value: String?,
    )

    public fun float(key: String): Float?

    public fun putFloat(
        key: String,
        value: Float,
    )

    public fun boolean(key: String): Boolean?

    public fun putBoolean(
        key: String,
        value: Boolean,
    )
}

/** A store that forgets: what a platform falls back to, and what the tests run against. */
public class InMemorySettingsStore : SettingsStore {
    private val values = mutableMapOf<String, Any?>()

    override fun string(key: String): String? = values[key] as? String

    override fun putString(
        key: String,
        value: String?,
    ) {
        values[key] = value
    }

    override fun float(key: String): Float? = values[key] as? Float

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        values[key] = value
    }

    override fun boolean(key: String): Boolean? = values[key] as? Boolean

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }
}

private const val KEY_QUALITY = "quality.tier"
private const val KEY_SCALING = "display.scaling"
private const val KEY_REDUCED_MOTION = "motion.reduced"
private const val KEY_CRT_ENABLED = "crt.enabled"
private const val KEY_CRT_CURVATURE = "crt.curvature"
private const val KEY_CRT_SCANLINES = "crt.scanlines"
private const val KEY_CRT_GRILLE = "crt.grille"
private const val KEY_CRT_BLOOM = "crt.bloom"
private const val KEY_CRT_VIGNETTE = "crt.vignette"
private const val KEY_INTERFACE_VOLUME = "audio.interface"
private const val KEY_LIBRARY_ADDRESS = "library.address"

/** Reads what was stored, falling back to the default for anything absent or no longer recognised. */
public fun SettingsStore.read(): SlipgateSettings {
    val defaults = SlipgateSettings.Default
    return SlipgateSettings(
        qualityOverride = string(KEY_QUALITY)?.let { name -> QualityTier.entries.firstOrNull { it.name == name } },
        scaling =
            string(KEY_SCALING)?.let { name -> ScalingMode.entries.firstOrNull { it.name == name } }
                ?: defaults.scaling,
        reducedMotion = boolean(KEY_REDUCED_MOTION) ?: defaults.reducedMotion,
        interfaceVolume = float(KEY_INTERFACE_VOLUME) ?: defaults.interfaceVolume,
        libraryAddress = string(KEY_LIBRARY_ADDRESS)?.takeIf { it.isNotBlank() },
        crt =
            CrtSettings(
                enabled = boolean(KEY_CRT_ENABLED) ?: defaults.crt.enabled,
                curvature = float(KEY_CRT_CURVATURE) ?: defaults.crt.curvature,
                scanlines = float(KEY_CRT_SCANLINES) ?: defaults.crt.scanlines,
                grille = float(KEY_CRT_GRILLE) ?: defaults.crt.grille,
                bloom = float(KEY_CRT_BLOOM) ?: defaults.crt.bloom,
                vignette = float(KEY_CRT_VIGNETTE) ?: defaults.crt.vignette,
            ),
    )
}

/** Writes every field, so a store never holds half of one decision. */
public fun SettingsStore.write(settings: SlipgateSettings) {
    putString(KEY_QUALITY, settings.qualityOverride?.name)
    putString(KEY_SCALING, settings.scaling.name)
    putBoolean(KEY_REDUCED_MOTION, settings.reducedMotion)
    putFloat(KEY_INTERFACE_VOLUME, settings.interfaceVolume)
    putString(KEY_LIBRARY_ADDRESS, settings.libraryAddress)
    putBoolean(KEY_CRT_ENABLED, settings.crt.enabled)
    putFloat(KEY_CRT_CURVATURE, settings.crt.curvature)
    putFloat(KEY_CRT_SCANLINES, settings.crt.scanlines)
    putFloat(KEY_CRT_GRILLE, settings.crt.grille)
    putFloat(KEY_CRT_BLOOM, settings.crt.bloom)
    putFloat(KEY_CRT_VIGNETTE, settings.crt.vignette)
}
