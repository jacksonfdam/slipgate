package com.jacksonfdam.slipgate.host.gamedata

private val SCHEMES = listOf("http://", "https://")

/**
 * Whether an address is one the app could fetch from, decided before it is stored.
 *
 * The platform clients answer a half-typed address by throwing, and one of them threw a checked
 * exception nothing caught: a stored `https:` crashed the app on every launch until the setting was
 * removed by hand. That hole is closed where it was opened, and this is the other half — the address
 * a player is still typing never reaches the settings file at all.
 *
 * Reachability is a separate and later question. A shelf that is switched off is the ordinary case,
 * and an address that parses is worth keeping so the app can try it again when the NAS comes back.
 */
public object ShelfAddress {
    /**
     * What is wrong with [address], or null when nothing is.
     *
     * Blank is not wrong: an empty field is a player who has no shelf, which the launcher already
     * says is the ordinary state rather than an error.
     */
    public fun problem(address: String): String? {
        val trimmed = address.trim()
        val scheme = SCHEMES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
        return when {
            trimmed.isEmpty() -> {
                null
            }

            scheme == null -> {
                "an address starts with http:// or https://"
            }

            // Everything after the scheme, up to the first slash or query, is the machine to ask.
            // Nothing there is the `https:` case: a scheme and no server behind it.
            trimmed.drop(scheme.length).takeWhile { it != '/' && it != '?' }.isEmpty() -> {
                "there is no server named after the ${scheme.trimEnd(':', '/')}://"
            }

            trimmed.any { it.isWhitespace() } -> {
                "an address holds no spaces"
            }

            else -> {
                null
            }
        }
    }

    /** Whether [address] is worth storing: usable, or empty because there is no shelf. */
    public fun usable(address: String): Boolean = problem(address) == null
}
