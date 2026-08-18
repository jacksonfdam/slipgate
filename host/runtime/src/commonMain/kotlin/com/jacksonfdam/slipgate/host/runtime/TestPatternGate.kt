package com.jacksonfdam.slipgate.host.runtime

private const val PATTERN_WIDTH = 320
private const val PATTERN_HEIGHT = 200
private const val PALETTE_ENTRIES = 256
private const val RAMP_SIZE = 64
private const val CELL_SIZE = 16
private const val SCROLL_PIXELS_PER_SECOND = 48
private const val MILLIS_PER_SECOND = 1000
private const val SNAPSHOT_BYTES = 8
private const val BYTE_MASK = 0xFF
private const val CHANNEL_MAX = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val QUARTER = 4
private const val THIRD = 3
private const val HALF = 2
private const val GREY_RAMP = 0
private const val RED_RAMP = 1
private const val GREEN_RAMP = 2

/**
 * A gate that needs no engine and no data: it draws a scrolling test pattern from its own
 * palette. It exists so the framebuffer path, the palette path and the session lifecycle can
 * be exercised end to end on every platform before a real engine is ported.
 *
 * Backend-independent by construction, so it is offered under every [BackendId] rather than
 * pretending to be a WebAssembly module.
 */
public class TestPatternGate(
    private val id: GateId = GateId("pattern"),
) : Gate {
    override val descriptor: GateDescriptor =
        GateDescriptor(
            id = id,
            title = "Test Pattern",
            engine = "none",
            artwork = GateArtwork(coverKey = "pattern/cover"),
            accent = AccentSource.PaletteEntry(index = RAMP_SIZE + RAMP_SIZE / 2),
        )

    override fun requirements(): DataRequirements = DataRequirements(entries = emptyList())

    override fun sessionFactories(): Map<BackendId, GateSessionFactory> =
        BackendId.entries.associateWith {
            GateSessionFactory { _, _ -> TestPatternSession() }
        }

    override fun inputProfile(): InputProfile =
        InputProfile(
            actions = setOf(GateAction.Menu),
            usesLookAxis = false,
        )
}

/** Session behind [TestPatternGate]. Deterministic: the same step sequence draws the same frames. */
public class TestPatternSession internal constructor() : GateSession {
    override val display: DisplayFormat =
        DisplayFormat(
            width = PATTERN_WIDTH,
            height = PATTERN_HEIGHT,
            pixelFormat = PixelFormat.Indexed8,
        )

    private val pixels = ByteArray(display.frameSizeBytes)
    private val colours = IntArray(PALETTE_ENTRIES) { paletteEntry(it) }
    private var elapsed = 0L
    private var closed = false

    override fun palette(): IntArray = colours

    override fun framebuffer(): ByteArray = pixels

    override fun step(
        input: InputFrame,
        elapsedMillis: Long,
    ): FrameResult {
        if (closed) {
            return FrameResult(
                status = SessionStatus.Finished,
                frameRendered = false,
                paletteChanged = false,
            )
        }
        elapsed += elapsedMillis
        val scroll = (elapsed * SCROLL_PIXELS_PER_SECOND / MILLIS_PER_SECOND).toInt()
        val bias = (input.movement.x * CELL_SIZE).toInt()
        draw(scroll + bias)
        return FrameResult(
            status = SessionStatus.Running,
            frameRendered = true,
            paletteChanged = false,
        )
    }

    override fun snapshot(): ByteArray =
        ByteArray(SNAPSHOT_BYTES) { index ->
            (elapsed shr (index * Byte.SIZE_BITS) and BYTE_MASK.toLong()).toByte()
        }

    override fun close() {
        closed = true
    }

    private fun draw(scroll: Int) {
        for (y in 0 until display.height) {
            val row = y * display.width
            val band = y * RAMP_SIZE / display.height
            for (x in 0 until display.width) {
                val cell = ((x + scroll) / CELL_SIZE + y / CELL_SIZE) and 1
                val ramp = (x + scroll) * RAMP_SIZE / display.width and (RAMP_SIZE - 1)
                val index = if (cell == 0) ramp else band + RAMP_SIZE
                pixels[row + x] = index.toByte()
            }
        }
    }
}

/**
 * Four ramps of [RAMP_SIZE] entries: grey, red, green, blue. Keeping the palette on the
 * session rather than in the renderer is what proves the indexed path works.
 */
private fun paletteEntry(index: Int): Int {
    val level = (index % RAMP_SIZE) * (CHANNEL_MAX / (RAMP_SIZE - 1))
    return when (index / RAMP_SIZE) {
        GREY_RAMP -> argb(level, level, level)
        RED_RAMP -> argb(level, level / QUARTER, 0)
        GREEN_RAMP -> argb(0, level, level / THIRD)
        else -> argb(level / QUARTER, level / HALF, level)
    }
}

private fun argb(
    red: Int,
    green: Int,
    blue: Int,
): Int = (CHANNEL_MAX shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
