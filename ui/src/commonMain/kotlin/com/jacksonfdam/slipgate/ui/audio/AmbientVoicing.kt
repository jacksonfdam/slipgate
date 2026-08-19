package com.jacksonfdam.slipgate.ui.audio

import com.jacksonfdam.slipgate.host.audio.synth.AmbientKey
import com.jacksonfdam.slipgate.host.audio.synth.AmbientMode
import com.jacksonfdam.slipgate.host.audio.synth.ambientKeyFor
import com.jacksonfdam.slipgate.ui.launcher.GateCard

/** The steel accent's own colour, so an unvoiced gate still has a root rather than none. */
private const val FALLBACK_ROOT_ARGB = 0xFF7E8CA3.toInt()

/**
 * The mode each gate's bed is played in.
 *
 * By gate rather than derived from anything: a scale is a decision about what a place sounds like, and
 * four decisions read better than a rule that pretends to make them. A gate nobody has voiced yet gets
 * the natural minor, which is the least opinionated of the four.
 */
public fun modeFor(gateId: String): AmbientMode =
    when (gateId) {
        "mars" -> AmbientMode.PhrygianDominant
        "corvus" -> AmbientMode.Dorian
        "korax" -> AmbientMode.Aeolian
        "chthon" -> AmbientMode.Locrian
        else -> AmbientMode.Aeolian
    }

/**
 * The key the focused card is played in: its own palette picks the root, its identity picks the mode.
 *
 * A card with no data has no sampled accent, so its bed sits where the steel fallback puts it — the
 * same place the interface's colour sits until the player installs something.
 */
public fun ambientKeyOf(card: GateCard): AmbientKey =
    ambientKeyFor(
        accentArgb = card.accent?.baseArgb ?: FALLBACK_ROOT_ARGB,
        mode = modeFor(card.id),
    )
