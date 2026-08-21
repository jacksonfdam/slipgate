package com.jacksonfdam.slipgate.host.gamedata

// The two documents a home library speaks: where it is, and what it holds.
//
// Both are lines of tab separated fields rather than JSON. The host carries no serialisation library
// and adding one for six fields would be the largest dependency in the module; a format that a split
// can read is also a format that cannot half-parse, and an operator can write one by hand with
// `printf` when something has gone wrong.
//
// Unknown field names and unknown line kinds are ignored rather than refused, so a later version can
// add a line without every already-installed app rejecting the document.

/** Where a shelf is right now, and the key that opens it. */
public data class ShelfPointer(
    val url: String,
    val key: String?,
    /** When the shelf last announced itself, as it wrote it. Shown to a player, never parsed. */
    val updated: String?,
)

/** One file a shelf is offering. */
public data class ShelfFile(
    /**
     * The gate the shelf filed this under, which is a claim by whoever laid out the directory, and
     * empty when it filed it under none. A game always names one; an add-on need not.
     */
    val gate: String,
    val name: String,
    val role: WadRole,
    /** What the shelf says it weighs, or null when it did not say. */
    val size: Long?,
    /** Where to fetch it, relative to the shelf's own address. */
    val path: String,
)

internal const val POINTER_HEADER: String = "slipgate-beacon 1"
internal const val INDEX_HEADER: String = "slipgate-shelf 1"

private const val INDEX_FIELDS = 6

/** The pointer this document holds, or null when it is not a pointer at all. */
internal fun parsePointer(document: String): ShelfPointer? {
    val lines = document.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.firstOrNull() != POINTER_HEADER) {
        return null
    }
    val fields =
        lines
            .drop(1)
            .mapNotNull { line ->
                val name = line.substringBefore('\t')
                val value = line.substringAfter('\t', missingDelimiterValue = "")
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }.toMap()

    // A pointer with no url points nowhere, which is not a pointer.
    return fields["url"]?.trimEnd('/')?.let { url ->
        ShelfPointer(url = url, key = fields["key"], updated = fields["updated"])
    }
}

/**
 * The files this document lists, or null when it is not a manifest.
 *
 * A line that does not have every field is dropped rather than failing the whole document, because a
 * manifest that lost one entry to a truncated write is still worth the four gates it did list.
 */
internal fun parseIndex(document: String): List<ShelfFile>? {
    val lines = document.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.firstOrNull() != INDEX_HEADER) {
        return null
    }
    return lines.drop(1).mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size < INDEX_FIELDS || fields[0] != "file") {
            null
        } else {
            ShelfFile(
                gate = fields[1],
                name = fields[2],
                role = if (fields[3] == "addon") WadRole.AddOn else WadRole.Bootable,
                size = fields[4].toLongOrNull(),
                path = fields[5],
            )
        }
    }
}
