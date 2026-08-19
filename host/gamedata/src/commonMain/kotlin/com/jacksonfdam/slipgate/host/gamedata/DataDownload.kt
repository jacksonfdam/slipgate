package com.jacksonfdam.slipgate.host.gamedata

/** Anything that went wrong between asking for a file and holding all of it. */
public class DataDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Fetches a file over HTTP.
 *
 * An interface rather than a function so a test can hand the acquisition a file without a server,
 * and so each platform can use the client it already has instead of the app carrying one.
 */
public interface DataDownload {
    /**
     * Returns every byte of [url], reporting progress as they arrive.
     *
     * @throws DataDownloadException when the file cannot be fetched in full.
     */
    public suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray
}

/** The download this platform performs with its own networking. */
public expect fun platformDataDownload(): DataDownload
