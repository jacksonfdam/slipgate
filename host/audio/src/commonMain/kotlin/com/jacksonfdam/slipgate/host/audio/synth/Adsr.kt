package com.jacksonfdam.slipgate.host.audio.synth

/**
 * Linear ADSR over a fixed total length: interface cues are one-shot, so the release is
 * scheduled from the end rather than waiting for a note-off.
 */
internal class Adsr {
    private var attackFrames = 0
    private var decayFrames = 0
    private var sustainLevel = 0f
    private var releaseFrames = 0
    private var totalFrames = 0
    private var position = 0

    val finished: Boolean
        get() = position >= totalFrames

    fun start(
        attackFrames: Int,
        decayFrames: Int,
        sustainLevel: Float,
        releaseFrames: Int,
        totalFrames: Int,
    ) {
        this.attackFrames = attackFrames
        this.decayFrames = decayFrames
        this.sustainLevel = sustainLevel
        this.releaseFrames = releaseFrames
        this.totalFrames = totalFrames
        position = 0
    }

    fun next(): Float {
        if (finished) return 0f
        val releaseStart = totalFrames - releaseFrames
        val value =
            when {
                position < attackFrames -> {
                    position.toFloat() / attackFrames
                }

                position < attackFrames + decayFrames -> {
                    val through = (position - attackFrames).toFloat() / decayFrames
                    1f - (1f - sustainLevel) * through
                }

                position >= releaseStart -> {
                    val through = (position - releaseStart).toFloat() / releaseFrames
                    sustainLevel * (1f - through)
                }

                else -> {
                    sustainLevel
                }
            }
        position += 1
        return value
    }
}
