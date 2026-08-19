package com.jacksonfdam.slipgate.ui.launcher

import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.GateDescriptor

/** Whether a gate can be entered, and if not, what would change that. */
public sealed interface GateAvailability {
    /** Everything the gate needs is stored; it can be entered now. */
    public data object Installed : GateAvailability

    /** Data is missing and the gate offers somewhere to get it. */
    public data class NeedsData(
        val missing: DataEntry,
    ) : GateAvailability

    /**
     * Data is missing and only the player can supply it.
     *
     * Hexen's card says this rather than showing a download button that could never work: an honest
     * dead end reads better than a control that lies.
     */
    public data class UserSuppliedOnly(
        val missing: DataEntry,
    ) : GateAvailability
}

/** One gate as the select screen draws it. */
public data class GateCard(
    val descriptor: GateDescriptor,
    val availability: GateAvailability,
    /**
     * The colour the card is drawn in, as `0xAARRGGBB`.
     *
     * Resolved when the rack is read rather than when it is drawn, because a palette accent comes
     * from the game's own data and reading data is not something a composable should be doing.
     */
    val accentArgb: Int,
) {
    public val id: String get() = descriptor.id.value

    public val isPlayable: Boolean get() = availability is GateAvailability.Installed
}
