package com.jacksonfdam.slipgate.host.backend.wasm

/** The four ways a player can travel, before an engine's own key codes are involved. */
public enum class Direction {
    Forward,
    Backward,
    Left,
    Right,
}

/**
 * The key codes an engine reads for travel.
 *
 * Separate from the action bindings because these are not actions: they come from an axis a player
 * holds rather than a button they press, and only the gate knows which codes its engine reads.
 */
public data class DirectionBindings(
    val forward: Int,
    val backward: Int,
    val left: Int,
    val right: Int,
) {
    internal fun codeFor(direction: Direction): Int =
        when (direction) {
            Direction.Forward -> forward
            Direction.Backward -> backward
            Direction.Left -> left
            Direction.Right -> right
        }
}
