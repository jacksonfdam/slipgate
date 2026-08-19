package com.jacksonfdam.slipgate.ui.data

/** What the data screen is doing, which is all it needs to know to draw itself. */
public sealed interface AcquisitionState {
    public data object Waiting : AcquisitionState

    public data class Working(
        val received: Long,
        val total: Long?,
    ) : AcquisitionState {
        /** Null when the server did not say how large the file is, so the bar stays indeterminate. */
        public val fraction: Float?
            get() = total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
    }

    public data class Problem(
        val message: String,
    ) : AcquisitionState

    public data object Installed : AcquisitionState
}
