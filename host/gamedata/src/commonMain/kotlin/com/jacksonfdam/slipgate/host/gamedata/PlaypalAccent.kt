package com.jacksonfdam.slipgate.host.gamedata

/** Three accent stops derived from a game's own palette, as 0xAARRGGBB. */
public data class AccentExtraction(
    val dimArgb: Int,
    val baseArgb: Int,
    val hotArgb: Int,
)

/**
 * Samples the launcher accent from a mounted game's `PLAYPAL` lump. Nothing is hardcoded
 * per game: whatever the most saturated usable cluster of the palette is, that is the
 * accent, so the interface is literally recolored by the game the user owns.
 */
public object PlaypalAccent {
    private const val PALETTE_BYTES = WadPaletteReader.PALETTE_BYTES
    private const val BYTE_MASK = 0xFF

    /** Colours darker than this cannot carry an interface accent. */
    private const val LUMINANCE_FLOOR = 0.14f

    /** Greys say nothing about a game's identity. */
    private const val SATURATION_FLOOR = 0.25f

    private const val HUE_BINS = 12
    private const val TRIPLE = 3
    private const val CHANNEL_MAX = 255
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ROUNDING = 0.5f
    private const val DIM_SCALE = 0.5f
    private const val HOT_LIFT = 0.55f
    private const val OPAQUE = 0xFF shl 24

    /**
     * Derives the accent ramp from a WAD's first `PLAYPAL` palette, or null when the file
     * has no readable palette — the caller keeps its neutral fallback in that case.
     */
    public fun fromWad(bytes: ByteArray): AccentExtraction? {
        val palette = WadPaletteReader.readPlaypal(bytes)
        return palette?.let(::fromPalette)
    }

    /** [palette] is 256 RGB triples, 768 bytes. */
    public fun fromPalette(palette: ByteArray): AccentExtraction? {
        if (palette.size < PALETTE_BYTES) return null
        val binWeight = FloatArray(HUE_BINS)
        val binRed = FloatArray(HUE_BINS)
        val binGreen = FloatArray(HUE_BINS)
        val binBlue = FloatArray(HUE_BINS)
        for (entry in 0 until PALETTE_BYTES / TRIPLE) {
            val red = (palette[entry * TRIPLE].toInt() and BYTE_MASK).toFloat() / CHANNEL_MAX
            val green = (palette[entry * TRIPLE + 1].toInt() and BYTE_MASK).toFloat() / CHANNEL_MAX
            val blue = (palette[entry * TRIPLE + 2].toInt() and BYTE_MASK).toFloat() / CHANNEL_MAX
            val high = maxOf(red, green, blue)
            val low = minOf(red, green, blue)
            val luminance = (high + low) / 2f
            val saturation = if (high == 0f) 0f else (high - low) / high
            if (luminance < LUMINANCE_FLOOR || saturation < SATURATION_FLOOR) continue
            val bin = hueBin(red, green, blue, high, low)
            val weight = saturation * saturation
            binWeight[bin] += weight
            binRed[bin] += red * weight
            binGreen[bin] += green * weight
            binBlue[bin] += blue * weight
        }
        var best = -1
        for (bin in 0 until HUE_BINS) {
            if (binWeight[bin] > 0f && (best == -1 || binWeight[bin] > binWeight[best])) {
                best = bin
            }
        }
        return if (best == -1) {
            null
        } else {
            ramp(
                binRed[best] / binWeight[best],
                binGreen[best] / binWeight[best],
                binBlue[best] / binWeight[best],
            )
        }
    }

    @Suppress("MagicNumber") // The standard HSV sextant formula.
    private fun hueBin(
        red: Float,
        green: Float,
        blue: Float,
        high: Float,
        low: Float,
    ): Int {
        val chroma = high - low
        val hue =
            when {
                chroma == 0f -> 0f
                high == red -> ((green - blue) / chroma + 6f) % 6f
                high == green -> (blue - red) / chroma + 2f
                else -> (red - green) / chroma + 4f
            }
        val bin = (hue / 6f * HUE_BINS).toInt()
        return if (bin >= HUE_BINS) HUE_BINS - 1 else bin
    }

    private fun ramp(
        red: Float,
        green: Float,
        blue: Float,
    ): AccentExtraction =
        AccentExtraction(
            dimArgb = argb(red * DIM_SCALE, green * DIM_SCALE, blue * DIM_SCALE),
            baseArgb = argb(red, green, blue),
            hotArgb =
                argb(
                    red + (1f - red) * HOT_LIFT,
                    green + (1f - green) * HOT_LIFT,
                    blue + (1f - blue) * HOT_LIFT,
                ),
        )

    private fun argb(
        red: Float,
        green: Float,
        blue: Float,
    ): Int =
        OPAQUE or
            (channel(red) shl RED_SHIFT) or
            (channel(green) shl GREEN_SHIFT) or
            channel(blue)

    private fun channel(value: Float): Int {
        val scaled = (value * CHANNEL_MAX + ROUNDING).toInt()
        return if (scaled < 0) {
            0
        } else if (scaled > CHANNEL_MAX) {
            CHANNEL_MAX
        } else {
            scaled
        }
    }
}
