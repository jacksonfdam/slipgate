package com.jacksonfdam.slipgate.host.gamedata

/**
 * A player's own game data, served from somewhere they own, reached over the network.
 *
 * The launcher has always been able to fetch a file over HTTP and validate it by contents before
 * storing it. What it had no way to say was "my files are over there": the two free replacements are
 * fixed public downloads, and everything else had to be picked by hand on each device. A library is
 * the missing third route, and it is the only one that serves data the player already owns.
 *
 * One address configures it, and that address may be either of two things:
 *
 * - a beacon, which answers with a pointer saying where the library is at the moment. A home tunnel
 *   changes hostname whenever it restarts, so a device configured with the hostname is a device that
 *   has to be reconfigured after every power cut. A beacon is the indirection that fixes that.
 * - the library itself, which is what a player on their own network wants: no site involved.
 *
 * Which one it is, is decided by what the address answers rather than by asking the player to say.
 */
public class GameLibrary(
    private val download: DataDownload = platformDataDownload(),
) {
    /**
     * Asks [address] what it is and what it holds.
     *
     * Never throws. Everything that can go wrong here is somebody's home server being off, and a
     * screen that has to say so needs a sentence rather than an exception.
     */
    public suspend fun open(address: String): LibraryListing {
        val configured = Address.of(address) ?: return LibraryListing.Unreachable("no library address is set")

        // The address itself first, because a beacon only answers there. A library answers 404 to it,
        // which costs one request and saves asking the player which kind of address they typed.
        val answered = (read(url(configured.url, configured.key)) as? Answer.Text)?.body
        val pointer = answered?.let(::parsePointer)
        val listed = answered?.let(::parseManifest)

        return when {
            pointer != null -> {
                follow(pointer)
            }

            // An address that answers with a manifest is the library itself, whether or not the
            // player wrote `/manifest` on the end of what they typed.
            listed != null -> {
                LibraryListing.Open(
                    base = configured.url.removeSuffix(MANIFEST_PATH),
                    key = configured.key,
                    files = listed,
                )
            }

            else -> {
                openDirect(configured)
            }
        }
    }

    /** Fetches one file from an open library, as bytes, reporting progress while it arrives. */
    public suspend fun fetch(
        listing: LibraryListing.Open,
        file: LibraryFile,
        onProgress: DownloadProgress = { _, _ -> },
    ): ByteArray = download.fetch(listing.urlFor(file), onProgress)

    private suspend fun follow(pointer: LibraryPointer): LibraryListing {
        val base = pointer.url
        val manifest = read(url(base + MANIFEST_PATH, pointer.key))
        return when (manifest) {
            is Answer.Failed -> {
                LibraryListing.Unreachable(
                    "the beacon points at $base, which did not answer: ${manifest.message}",
                )
            }

            is Answer.Text -> {
                val files = parseManifest(manifest.body)
                if (files == null) {
                    LibraryListing.Unreachable("$base answered, but not with a library manifest")
                } else {
                    LibraryListing.Open(base = base, key = pointer.key, files = files, publishedAt = pointer.updated)
                }
            }
        }
    }

    private suspend fun openDirect(configured: Address): LibraryListing {
        val base = configured.url.removeSuffix(MANIFEST_PATH)
        return when (val manifest = read(url(base + MANIFEST_PATH, configured.key))) {
            is Answer.Failed -> {
                LibraryListing.Unreachable(manifest.message)
            }

            is Answer.Text -> {
                val files = parseManifest(manifest.body)
                if (files == null) {
                    LibraryListing.Unreachable("$base answered, but not with a library manifest")
                } else {
                    LibraryListing.Open(base = base, key = configured.key, files = files)
                }
            }
        }
    }

    private suspend fun read(url: String): Answer =
        try {
            // Both documents are a few hundred bytes, so there is nothing to stream and no progress
            // worth reporting: what a player waits for is the game data, not the list of it.
            Answer.Text(download.fetch(url) { _, _ -> }.decodeToString())
        } catch (failure: DataDownloadException) {
            Answer.Failed(failure.message ?: "it did not answer")
        }

    private sealed interface Answer {
        data class Text(
            val body: String,
        ) : Answer

        data class Failed(
            val message: String,
        ) : Answer
    }
}

/** What came of asking an address for a library. */
public sealed interface LibraryListing {
    /** The library answered, and this is what it holds. */
    public data class Open(
        /** The library's own address, with no trailing slash and no query. */
        val base: String,
        /** The key every request to it has to carry, or null for a library that asked for none. */
        val key: String?,
        val files: List<LibraryFile>,
        /** When the library last announced itself, when a beacon said. */
        val publishedAt: String? = null,
    ) : LibraryListing {
        /** Everything this library filed under [gate] that a gate could boot from. */
        public fun bootable(gate: String): List<LibraryFile> =
            files.filter { it.gate == gate && it.role == WadRole.Bootable }

        /** The map packs this library filed under [gate]. */
        public fun addOns(gate: String): List<LibraryFile> =
            files.filter { it.gate == gate && it.role == WadRole.AddOn }

        /** Where one of these files can be fetched from. */
        public fun urlFor(file: LibraryFile): String = url(base + file.path, key)
    }

    /** Nothing usable answered, and this is the sentence a player is owed. */
    public data class Unreachable(
        val message: String,
    ) : LibraryListing
}

private const val MANIFEST_PATH = "/manifest"

/**
 * The key as a query parameter rather than a header.
 *
 * Three platform HTTP clients sit behind one `fetch(url)`, and none of them carries a header today.
 * Putting the key in the query is the version of this that works on all three, and it travels inside
 * TLS with the rest of the request line. It does end up in a server log, which is the honest cost:
 * the library it opens is the player's own, and the key can be rotated by restarting the NAS script.
 */
private fun url(
    address: String,
    key: String?,
): String =
    when {
        key.isNullOrEmpty() -> address
        '?' in address -> "$address&key=$key"
        else -> "$address?key=$key"
    }

/** A configured address, split into what to ask and what to prove. */
private class Address(
    val url: String,
    val key: String?,
) {
    companion object {
        fun of(address: String): Address? {
            val trimmed = address.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            // Split rather than kept whole, so that appending `/manifest` to what a player typed
            // cannot land after a query string and produce an address nothing will answer.
            val key =
                trimmed
                    .substringAfter('?', missingDelimiterValue = "")
                    .split('&')
                    .firstOrNull { it.startsWith("key=") }
                    ?.removePrefix("key=")
                    ?.takeIf { it.isNotEmpty() }
            val withoutQuery = trimmed.substringBefore('?').trimEnd('/')
            return if (withoutQuery.isEmpty()) null else Address(url = withoutQuery, key = key)
        }
    }
}
