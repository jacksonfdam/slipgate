package com.jacksonfdam.slipgate.host.backend.wasm

import com.jacksonfdam.slipgate.host.runtime.DisplayFormat
import com.jacksonfdam.slipgate.host.runtime.FrameResult
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputFrame
import com.jacksonfdam.slipgate.host.runtime.LogLevel
import com.jacksonfdam.slipgate.host.runtime.PixelFormat
import com.jacksonfdam.slipgate.host.runtime.SessionStatus

private const val FRAME_RENDERED = 0x01
private const val PALETTE_CHANGED = 0x02
private const val ENGINE_FINISHED = 0x04

private const val EVENT_KEY_DOWN = 1
private const val EVENT_KEY_UP = 2

// What the engine's own mixer produces. A sink that wants something else would need resampling,
// which belongs in the sink and not in the middle of a frame.
private const val ENGINE_SAMPLE_RATE = 44100
private const val ENGINE_CHANNELS = 2
private const val AUDIO_FRAME_BYTES = 4
private const val AUDIO_SLICE_FRAMES = 2048
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8

// Past this much of a stick's travel a direction counts as pressed. Engines of this age know only
// keys, so a smooth axis has to become one at some point, and a tenth of the way is early enough to
// feel immediate without a resting thumb walking the player into a wall.
private const val DIRECTION_THRESHOLD = 0.1f

private const val PALETTE_ENTRIES = 256
private const val PALETTE_ENTRY_BYTES = 3
private const val CHANNEL_MAX = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/**
 * A session backed by an engine compiled to WebAssembly.
 *
 * Gates construct this directly: the key bindings and the pixel aspect are the gate's knowledge,
 * because they belong to the engine behind it rather than to WebAssembly.
 *
 * The session owns the translation in both directions: normalised input becomes the key codes the
 * engine expects, and the engine's palette becomes the 0xAARRGGBB entries the renderer wants. What
 * it does not own is policy — it steps when told and reports what happened.
 */
@Suppress("TooManyFunctions") // Each one answers a call in the session contract or a step of one.
public class WasmGateSession(
    private val engine: WasmEngine,
    private val host: GateHost,
    private val keyBindings: Map<GateAction, Int>,
    private val directionBindings: DirectionBindings,
    pixelAspect: Float,
) : GateSession {
    override val display: DisplayFormat =
        DisplayFormat(
            width = engine.framebufferWidth(),
            height = engine.framebufferHeight(),
            pixelFormat = PixelFormat.Indexed8,
            pixelAspect = pixelAspect,
        )

    private val colours = IntArray(PALETTE_ENTRIES)
    private val audioBytes = ByteArray(AUDIO_SLICE_FRAMES * AUDIO_FRAME_BYTES)
    private val audioSamples = ShortArray(AUDIO_SLICE_FRAMES * ENGINE_CHANNELS)
    private val audioPlayable =
        host.audio.sampleRate == ENGINE_SAMPLE_RATE && host.audio.channels == ENGINE_CHANNELS
    private var pendingFrames = 0
    private var pendingOffset = 0
    private var frame = ByteArray(display.frameSizeBytes)
    private var heldActions = 0
    private var heldDirections = emptySet<Direction>()
    private var finished = false
    private var paletteRead = false

    override fun palette(): IntArray {
        if (!paletteRead) {
            readPalette()
        }
        return colours
    }

    override fun framebuffer(): ByteArray = frame

    override fun step(
        input: InputFrame,
        elapsedMillis: Long,
    ): FrameResult {
        if (!finished) {
            sendInput(input)
            val status = engine.step(elapsedMillis.toInt())
            if (status and ENGINE_FINISHED != 0) {
                finished = true
                host.logger.log(LogLevel.Info, "the engine finished")
            } else {
                return running(status)
            }
        }
        return stopped()
    }

    private fun running(status: Int): FrameResult {
        val rendered = status and FRAME_RENDERED != 0
        val paletteChanged = status and PALETTE_CHANGED != 0

        if (rendered) {
            frame = engine.framebuffer()
        }
        pumpAudio()
        if (paletteChanged || !paletteRead) {
            readPalette()
        }

        return FrameResult(SessionStatus.Running, rendered, paletteChanged)
    }

    private fun stopped(): FrameResult =
        FrameResult(SessionStatus.Finished, frameRendered = false, paletteChanged = false)

    override fun snapshot(): ByteArray = ByteArray(0)

    override fun close() {
        finished = true
    }

    /**
     * Sends only what changed. The engine tracks key state itself, so repeating a held action every
     * frame would make it think the key was struck again.
     */
    private fun sendInput(input: InputFrame) {
        sendDirections(input)
        val actions = input.actions.mask
        val pressed = actions and heldActions.inv()
        val released = heldActions and actions.inv()

        keyBindings.forEach { (action, code) ->
            if (pressed and action.bit != 0) {
                engine.pushEvent(EVENT_KEY_DOWN, code, 0)
            }
            if (released and action.bit != 0) {
                engine.pushEvent(EVENT_KEY_UP, code, 0)
            }
        }

        heldActions = actions
    }

    /**
     * Moves whatever the engine has mixed into the sink, and stops as soon as either side is empty.
     *
     * Audio the sink refuses stays where it is rather than being dropped: the engine keeps its own
     * budget, so a sink that is briefly full costs a step of latency instead of a gap in the sound.
     *
     * Both halves are local because they are two steps of one pump and share its buffers; neither
     * means anything to the rest of the session.
     */
    private fun pumpAudio() {
        // The engine writes little-endian signed 16-bit frames; a sink wants them as samples.
        fun decode(frames: Int) {
            for (sample in 0 until frames * ENGINE_CHANNELS) {
                val low = audioBytes[sample * Short.SIZE_BYTES].toInt() and BYTE_MASK
                val high = audioBytes[sample * Short.SIZE_BYTES + 1].toInt()
                audioSamples[sample] = ((high shl BYTE_BITS) or low).toShort()
            }
        }

        // Whether everything buffered reached the sink.
        fun flush(): Boolean {
            while (pendingFrames > 0) {
                val slice =
                    if (pendingOffset == 0) {
                        audioSamples
                    } else {
                        // Only a sink that accepted part of a slice pays for this copy.
                        audioSamples.copyOfRange(
                            pendingOffset * ENGINE_CHANNELS,
                            (pendingOffset + pendingFrames) * ENGINE_CHANNELS,
                        )
                    }
                val accepted = host.audio.submit(slice, pendingFrames)
                if (accepted <= 0) {
                    return false
                }
                pendingOffset += accepted
                pendingFrames -= accepted
            }
            return true
        }

        if (!audioPlayable) {
            return
        }
        var drainable = flush()
        while (drainable) {
            val drained = engine.drainAudio(audioBytes, AUDIO_SLICE_FRAMES)
            if (drained == 0) {
                break
            }
            decode(drained)
            pendingFrames = drained
            pendingOffset = 0
            drainable = flush()
        }
    }

    /**
     * Turns the movement axis into the direction keys the engine expects.
     *
     * The engine has no notion of an axis: it reads keys, and a key is either down or up. Sending only
     * the changes matters more here than for the buttons, because a thumb resting on a pad produces a
     * new value every frame while meaning the same thing.
     */
    private fun sendDirections(input: InputFrame) {
        val pressed = mutableSetOf<Direction>()
        if (input.movement.y > DIRECTION_THRESHOLD) pressed += Direction.Forward
        if (input.movement.y < -DIRECTION_THRESHOLD) pressed += Direction.Backward
        if (input.movement.x < -DIRECTION_THRESHOLD) pressed += Direction.Left
        if (input.movement.x > DIRECTION_THRESHOLD) pressed += Direction.Right

        (pressed - heldDirections).forEach { direction ->
            engine.pushEvent(EVENT_KEY_DOWN, directionBindings.codeFor(direction), 0)
        }
        (heldDirections - pressed).forEach { direction ->
            engine.pushEvent(EVENT_KEY_UP, directionBindings.codeFor(direction), 0)
        }
        heldDirections = pressed
    }

    /** The engine keeps a palette of red-green-blue triples; the renderer wants opaque colours. */
    private fun readPalette() {
        val bytes = engine.palette()
        for (entry in 0 until PALETTE_ENTRIES) {
            val offset = entry * PALETTE_ENTRY_BYTES
            val red = bytes[offset].toInt() and CHANNEL_MAX
            val green = bytes[offset + 1].toInt() and CHANNEL_MAX
            val blue = bytes[offset + 2].toInt() and CHANNEL_MAX
            colours[entry] =
                (CHANNEL_MAX shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }
        paletteRead = true
    }
}
