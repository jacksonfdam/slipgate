package com.jacksonfdam.slipgate.host.graphics.backend.skia

/**
 * The shared scene uniform block. Every scene shader — a gate portrait, the attract background —
 * declares exactly these uniforms in exactly this order, so one packer serves them all and the
 * quality tier changes parameters, never shader variants.
 */
@Suppress("LongParameterList") // The block is one flat set of named uniforms, by design.
public class SceneUniforms(
    public val widthPixels: Float,
    public val heightPixels: Float,
    public val timeSeconds: Float,
    public val accentDim: Int,
    public val accentBase: Int,
    public val accentHot: Int,
    public val focusAmount: Float,
    public val audioLevel: Float,
    public val octaves: Float,
) {
    /** Values in shader declaration order, ready to upload as plain float uniforms. */
    @Suppress("MagicNumber") // The indices are the declaration order itself.
    public fun pack(into: FloatArray = FloatArray(FLOAT_COUNT)): FloatArray {
        require(into.size >= FLOAT_COUNT) { "uniform buffer holds ${into.size} of $FLOAT_COUNT floats" }
        into[0] = widthPixels
        into[1] = heightPixels
        into[2] = timeSeconds
        writeColor(into, offset = 3, argb = accentDim)
        writeColor(into, offset = 6, argb = accentBase)
        writeColor(into, offset = 9, argb = accentHot)
        into[12] = focusAmount
        into[13] = audioLevel
        into[14] = octaves
        return into
    }

    private fun writeColor(
        into: FloatArray,
        offset: Int,
        argb: Int,
    ) {
        into[offset] = (argb shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_SCALE
        into[offset + 1] = (argb shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_SCALE
        into[offset + 2] = (argb and CHANNEL_MASK) / CHANNEL_SCALE
    }

    public companion object {
        /** Plain floats in the block: size, time, three colours, focus, audio, octaves. */
        public const val FLOAT_COUNT: Int = 15

        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val CHANNEL_MASK = 0xFF
        private const val CHANNEL_SCALE = 255f
    }
}

/** Source of the scene shader named [shaderName], or null when no shader of that name is embedded. */
internal fun sceneShaderSource(shaderName: String): String? = skslSources[shaderName]
