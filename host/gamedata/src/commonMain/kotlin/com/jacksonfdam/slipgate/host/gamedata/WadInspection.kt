package com.jacksonfdam.slipgate.host.gamedata

/** Whether a file is a game's own data or an add-on that patches one. */
public enum class WadKind {
    /** A complete game: the engine boots from it. */
    Iwad,

    /** A patch: it replaces parts of an IWAD and cannot be booted on its own. */
    Pwad,
}

/**
 * What the file's contents say it is.
 *
 * Named for the layout rather than for a brand, because that is all the contents can honestly
 * support: Freedoom and Doom share a structure, and a gate needs the structure. Anything else would
 * be a guess dressed up as identification.
 */
public enum class GameFlavour(
    /** What to call this in something a player reads. */
    public val label: String,
) {
    /** Doom's engine, episodes and maps named `ExMy`. */
    DoomEpisodic("Doom in episodes"),

    /** Doom's engine, maps named `MAPxx`. */
    DoomMapped("Doom in maps"),

    /** Raven's Heretic: Doom's map naming with Raven's tint table. */
    Heretic("Heretic"),

    /** Raven's Hexen: `MAPxx` with Raven's tint table. */
    Hexen("Hexen"),
}

/** What a recognised file is, and how much of a game it holds. */
public data class WadIdentity(
    val kind: WadKind,
    val flavour: GameFlavour,
    val lumpCount: Int,
    val episodes: Int,
    val maps: Int,
)

/** Why a file cannot be used. Each reason is something a person can act on. */
public enum class RejectionReason {
    /** Smaller than a header, so there is nothing to read. */
    TooSmall,

    /** No `IWAD` or `PWAD` signature: not a WAD at all. */
    NotAWad,

    /** The lump directory is missing, empty, or points past the end of the file. */
    DirectoryUnreadable,

    /** A lump claims bytes the file does not contain, which is what a truncated download looks like. */
    LumpOutOfRange,

    /** A WAD without a palette cannot be a game, only a patch. */
    NoPalette,

    /** A valid WAD whose contents match no engine this app runs. */
    UnknownGame,
}

/** The outcome of looking at a file the user supplied. */
public sealed interface WadInspection {
    public data class Recognised(
        val identity: WadIdentity,
    ) : WadInspection

    public data class Rejected(
        val reason: RejectionReason,
        val detail: String,
    ) : WadInspection
}
