package com.jacksonfdam.slipgate.ui.data

/**
 * A file the player chose, with its bytes already read.
 *
 * Bytes rather than a path or a handle: the platforms disagree about what a chosen file even is — a
 * content URI, a security-scoped URL, a browser object — and none of those survive being handed to a
 * gate. Reading it once, where the permission still applies, is what makes the rest of the app
 * platform-agnostic.
 */
public class PickedFile(
    public val name: String,
    public val bytes: ByteArray,
)
