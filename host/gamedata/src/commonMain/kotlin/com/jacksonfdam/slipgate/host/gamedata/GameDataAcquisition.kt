package com.jacksonfdam.slipgate.host.gamedata

/** How far a download has got. [total] is null when the server did not say how large the file is. */
public typealias DownloadProgress = (received: Long, total: Long?) -> Unit

/** What to fetch, what to call it, and what would make it acceptable. */
public data class AcquisitionRequest(
    val gate: String,
    val name: String,
    val url: String,
    val accepts: Set<GameFlavour>,
    /**
     * When the download is an archive, the file inside it to take, matched by how its name ends.
     * Freedoom and Blasphemer both publish their WADs inside a zip.
     */
    val archiveEntry: String? = null,
    /** Whether this is the game the gate boots, or maps to load over one it already has. */
    val role: WadRole = WadRole.Bootable,
)

/** What became of an attempt to install game data. */
public sealed interface AcquisitionResult {
    public data class Stored(
        val name: String,
        val identity: WadIdentity,
    ) : AcquisitionResult

    /** The file arrived intact and is not game data at all. */
    public data class Refused(
        val inspection: WadInspection.Rejected,
    ) : AcquisitionResult

    /** The file did not arrive, could not be unpacked, or is a game this gate does not run. */
    public data class Failed(
        val message: String,
    ) : AcquisitionResult
}

/**
 * Installs game data into a store, from a download or from a file the player supplied.
 *
 * Nothing reaches the store unvalidated, and nothing is validated after being stored: a file that
 * fails inspection is never written at all, so a shelf never holds something a gate cannot boot.
 */
public class GameDataAcquisition(
    private val store: GameDataStore,
    private val download: DataDownload = platformDataDownload(),
) {
    /** Fetches what [request] describes, unpacks it if it is an archive, and stores what passes. */
    public suspend fun acquire(
        request: AcquisitionRequest,
        onProgress: DownloadProgress = { _, _ -> },
    ): AcquisitionResult {
        val fetched =
            try {
                download.fetch(request.url, onProgress)
            } catch (failure: DataDownloadException) {
                return AcquisitionResult.Failed(failure.message ?: "the download did not finish")
            }
        return unpack(request, fetched)
    }

    /** Stores [bytes] under [name] for [gate] if they inspect as game data this gate can run. */
    public suspend fun install(
        gate: String,
        name: String,
        bytes: ByteArray,
        accepts: Set<GameFlavour>,
        role: WadRole = WadRole.Bootable,
    ): AcquisitionResult {
        val verdict = verdict(name, WadInspector.inspect(bytes), accepts, role)
        if (verdict is AcquisitionResult.Stored) {
            store.write(gate, verdict.name, bytes)
        }
        return verdict
    }

    /**
     * Stores maps a player supplied to load over the game [gate] already has.
     *
     * The name is kept, marked as an add-on, because a shelf holding four of these is only navigable
     * if each one is still called what the player downloaded.
     */
    public suspend fun installAddOn(
        gate: String,
        name: String,
        bytes: ByteArray,
    ): AcquisitionResult =
        install(
            gate = gate,
            name = addOnStorageName(name),
            bytes = bytes,
            // An add-on names no engine, so there is nothing for a flavour to be checked against; what
            // makes it acceptable is that it is an add-on at all, which the role below decides.
            accepts = GameFlavour.entries.toSet(),
            role = WadRole.AddOn,
        )

    private suspend fun unpack(
        request: AcquisitionRequest,
        fetched: ByteArray,
    ): AcquisitionResult {
        val entryName =
            request.archiveEntry
                ?: return install(request.gate, request.name, fetched, request.accepts, request.role)

        return try {
            val archive = ZipArchive(fetched)
            val entry = archive.find(entryName)
            if (entry == null) {
                AcquisitionResult.Failed("the archive holds no $entryName")
            } else {
                install(request.gate, request.name, archive.read(entry), request.accepts, request.role)
            }
        } catch (damaged: ZipException) {
            AcquisitionResult.Failed(damaged.message ?: "the archive is damaged")
        } catch (damaged: InflateException) {
            AcquisitionResult.Failed(damaged.message ?: "the archive could not be expanded")
        }
    }

    private fun verdict(
        name: String,
        inspection: WadInspection,
        accepts: Set<GameFlavour>,
        role: WadRole,
    ): AcquisitionResult =
        when {
            inspection is WadInspection.Rejected -> {
                AcquisitionResult.Refused(inspection)
            }

            inspection !is WadInspection.Recognised -> {
                AcquisitionResult.Failed("the file was not inspected")
            }

            inspection.identity.role != role -> {
                AcquisitionResult.Failed(wrongRole(inspection.identity.role))
            }

            role == WadRole.Bootable && inspection.identity.flavour !in accepts -> {
                AcquisitionResult.Failed(
                    "that is ${inspection.identity.flavour?.label} data and this gate needs " +
                        accepts.joinToString(" or ") { it.label },
                )
            }

            else -> {
                AcquisitionResult.Stored(name, inspection.identity)
            }
        }

    /**
     * Both directions are worth a sentence of their own.
     *
     * A player choosing a map pack where the game belongs and a player choosing a game where the maps
     * belong have made opposite mistakes, and "wrong kind of file" would leave either of them guessing
     * which one.
     */
    private fun wrongRole(found: WadRole): String =
        when (found) {
            WadRole.AddOn -> "that is an add-on, not a game; a gate needs a game to boot before maps can load over it"
            WadRole.Bootable -> "that is a whole game rather than maps to add; install it as this gate's game instead"
        }
}
