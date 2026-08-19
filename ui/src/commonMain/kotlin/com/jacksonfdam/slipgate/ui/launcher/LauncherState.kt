package com.jacksonfdam.slipgate.ui.launcher

/**
 * What the select screen shows and which card the player is on.
 *
 * A value rather than a mutable object: navigation returns the next state, so a test can walk the
 * rack without a screen and the screen has nothing to get out of step with.
 */
public data class LauncherState(
    val cards: List<GateCard>,
    val selected: Int = 0,
) {
    init {
        require(selected == 0 || selected in cards.indices) {
            "selected $selected is outside a rack of ${cards.size}"
        }
    }

    public val current: GateCard? get() = cards.getOrNull(selected)

    /**
     * Moves along the rack, wrapping at each end.
     *
     * Wrapping rather than stopping because the rack is a carousel: with three gates, stopping means
     * a player who overshoots has to reverse, and a rack that comes round again never traps anyone.
     */
    public fun moveBy(steps: Int): LauncherState {
        if (cards.isEmpty()) {
            return this
        }
        val size = cards.size
        val next = ((selected + steps) % size + size) % size
        return copy(selected = next)
    }

    public fun next(): LauncherState = moveBy(1)

    public fun previous(): LauncherState = moveBy(-1)

    /** Selects the card at [index], or stays put when the rack has no such card. */
    public fun select(index: Int): LauncherState = if (index in cards.indices) copy(selected = index) else this

    /** Selects [id] if the rack holds it, and stays put if it does not. */
    public fun select(id: String): LauncherState {
        val index = cards.indexOfFirst { card -> card.id == id }
        return if (index < 0) this else copy(selected = index)
    }

    /** Replaces one card's availability, which is what installing data changes. */
    public fun withAvailability(
        id: String,
        availability: GateAvailability,
    ): LauncherState =
        copy(
            cards =
                cards.map { card ->
                    if (card.id == id) card.copy(availability = availability) else card
                },
        )
}
