package com.jacksonfdam.slipgate.host.gamedata

/** How far a download has got. [total] is null when the server did not say how large the file is. */
public typealias DownloadProgress = (received: Long, total: Long?) -> Unit

/** What became of an attempt to install game data. */
public sealed interface AcquisitionResult {
    public data class Stored(
        val name: String,
        val identity: WadIdentity,
    ) : AcquisitionResult

    /** The file arrived intact and is not what this gate needs. */
    public data class Refused(
        val inspection: WadInspection.Rejected,
    ) : AcquisitionResult

    /** The file did not arrive, or is a game this gate does not run. */
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
    /**
     * Downloads [url] and stores it as [name] for [gate], provided it inspects as an IWAD whose
     * layout is one of [accepts].
     */
    public suspend fun acquire(
        gate: String,
        name: String,
        url: String,
        accepts: Set<GameFlavour>,
        onProgress: DownloadProgress = { _, _ -> },
    ): AcquisitionResult {
        val bytes =
            try {
                download.fetch(url, onProgress)
            } catch (failure: DataDownloadException) {
                return AcquisitionResult.Failed(failure.message ?: "the download did not finish")
            }
        return install(gate, name, bytes, accepts)
    }

    /** Stores [bytes] under [name] for [gate] if they inspect as game data this gate can run. */
    public suspend fun install(
        gate: String,
        name: String,
        bytes: ByteArray,
        accepts: Set<GameFlavour>,
    ): AcquisitionResult {
        val verdict = verdict(name, WadInspector.inspect(bytes), accepts)
        if (verdict is AcquisitionResult.Stored) {
            store.write(gate, name, bytes)
        }
        return verdict
    }

    private fun verdict(
        name: String,
        inspection: WadInspection,
        accepts: Set<GameFlavour>,
    ): AcquisitionResult =
        when {
            inspection is WadInspection.Rejected -> {
                AcquisitionResult.Refused(inspection)
            }

            inspection !is WadInspection.Recognised -> {
                AcquisitionResult.Failed("the file was not inspected")
            }

            inspection.identity.kind != WadKind.Iwad -> {
                AcquisitionResult.Failed("that is a patch, not a game; a gate needs an IWAD to boot")
            }

            inspection.identity.flavour !in accepts -> {
                AcquisitionResult.Failed(
                    "that is ${inspection.identity.flavour.label} data and this gate needs " +
                        accepts.joinToString(" or ") { it.label },
                )
            }

            else -> {
                AcquisitionResult.Stored(name, inspection.identity)
            }
        }
}
