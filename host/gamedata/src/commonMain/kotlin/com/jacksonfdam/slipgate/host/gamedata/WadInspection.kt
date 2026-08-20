package com.jacksonfdam.slipgate.host.gamedata

/** Whether a file is a game's own data or an add-on that patches one. */
public enum class WadKind {
    /** A complete game: the engine boots from it. */
    Iwad,

    /** A patch: it replaces parts of an IWAD and cannot be booted on its own. */
    Pwad,
}

/**
 * What a file can be used for, which is not always what its signature claims.
 *
 * The two are separate because the signature lies often enough to matter. Chex Quest ships a whole
 * game under a `PWAD` signature, and Hexen's Deathkings expansion ships an add-on under an `IWAD`
 * one; the engines ignore the claim and look at the contents, so this does too. What decides it is
 * the palette: a file carrying one supplies every colour the screen needs and can stand alone,
 * and a file without one is borrowing the colours of whatever it is loaded on top of.
 */
public enum class WadRole {
    /** Carries its own palette, so a gate can boot from it. */
    Bootable,

    /** Maps and resources meant to load over a game that is already there. */
    AddOn,
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

    /** Rogue's Strife: `MAPxx` with a translucency table of its own. */
    Strife("Strife"),
}

/**
 * What a recognised file is, and how much of a game it holds.
 *
 * [flavour] is null only for an add-on whose contents name no engine — a map replacement carries
 * maps and little else, and which game those maps are for is not written anywhere inside them.
 */
public data class WadIdentity(
    val kind: WadKind,
    val role: WadRole,
    val flavour: GameFlavour?,
    val lumpCount: Int,
    val episodes: Int,
    val maps: Int,
    /** The map lumps this file holds, in the order the engine would find them. */
    val mapNames: List<String> = emptyList(),
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

    /** A valid WAD holding no maps: nothing here is a game, and nothing here adds one. */
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
