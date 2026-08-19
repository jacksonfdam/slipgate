// The numbers below are the scales and the colour wheel: naming each interval would obscure them.
@file:Suppress("MagicNumber")

package com.jacksonfdam.slipgate.host.audio.synth

private const val SEMITONES = 12
private const val BYTE_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_SCALE = 255f
private const val SIXTH = 1f / 6f
private const val TWO_SIXTHS = 2f / 6f
private const val FOUR_SIXTHS = 4f / 6f

/**
 * A musical mode, as the interval pattern from its root.
 *
 * One per gate, chosen for the game rather than derived from it: a scale is a decision about what a
 * place sounds like, and four decisions are cheaper to read than a rule that pretends to make them.
 */
public enum class AmbientMode(
    public val semitones: IntArray,
) {
    /** mars: Phrygian dominant — the flattened second that makes anything sound like a threat. */
    PhrygianDominant(intArrayOf(0, 1, 4, 5, 7, 8, 10)),

    /** corvus: Dorian — minor with a raised sixth, which keeps it from sounding defeated. */
    Dorian(intArrayOf(0, 2, 3, 5, 7, 9, 10)),

    /** korax: Aeolian — the natural minor, cold and settled. */
    Aeolian(intArrayOf(0, 2, 3, 5, 7, 8, 10)),

    /** chthon: Locrian — a diminished fifth in the scale itself, so nothing ever resolves. */
    Locrian(intArrayOf(0, 1, 3, 5, 6, 8, 10)),
    ;

    /** The note [degree] steps up the scale, in semitones from the root, wrapping through octaves. */
    public fun semitoneAt(degree: Int): Int {
        val size = semitones.size
        val octave = if (degree >= 0) degree / size else (degree - size + 1) / size
        val step = degree - octave * size
        return semitones[step] + octave * SEMITONES
    }
}

/** What a gate's ambient bed is played in: a root note and the mode above it. */
public data class AmbientKey(
    /** Semitones above the reference pitch, 0 to 11. */
    public val rootSemitone: Int,
    public val mode: AmbientMode,
)

/**
 * Derives the key from the accent the gate's own palette produced.
 *
 * The hue picks the root, so a game that recolours the interface also transposes it: the same three
 * stops that make Doom red make its bed sit a fifth from Hexen's. The mode comes from the gate rather
 * than the colour, because a mode is a mood and colour is not reliable enough to choose one.
 */
public fun ambientKeyFor(
    accentArgb: Int,
    mode: AmbientMode,
): AmbientKey =
    AmbientKey(
        rootSemitone = (hueTurns(accentArgb) * SEMITONES).toInt().coerceIn(0, SEMITONES - 1),
        mode = mode,
    )

/**
 * Hue as a fraction of the colour wheel, by the standard piecewise formula written out in arithmetic
 * so the audio path stays free of platform math calls.
 */
private fun hueTurns(argb: Int): Float {
    val red = (argb shr RED_SHIFT and BYTE_MASK) / CHANNEL_SCALE
    val green = (argb shr GREEN_SHIFT and BYTE_MASK) / CHANNEL_SCALE
    val blue = (argb and BYTE_MASK) / CHANNEL_SCALE
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val spread = high - low
    if (spread == 0f) {
        return 0f
    }
    val turns =
        when (high) {
            red -> ((green - blue) / spread) * SIXTH
            green -> ((blue - red) / spread) * SIXTH + TWO_SIXTHS
            else -> ((red - green) / spread) * SIXTH + FOUR_SIXTHS
        }
    return if (turns < 0f) turns + 1f else turns
}
