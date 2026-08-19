package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithURL
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val HTTP_OK = 200L

/** Fetches with `NSURLSession`, the client the system already runs. */
public actual fun platformDataDownload(): DataDownload = UrlSessionDownload()

@OptIn(ExperimentalForeignApi::class)
internal class UrlSessionDownload : DataDownload {
    private val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.defaultSessionConfiguration)

    /**
     * Progress is reported once, when the whole file has arrived: a data task hands over its bytes
     * in one piece, and inventing intermediate numbers would be a lie the progress bar tells.
     */
    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray {
        val address = NSURL.URLWithString(url) ?: throw DataDownloadException("$url is not a usable address")
        val data =
            suspendCancellableCoroutine { continuation ->
                val task =
                    session.dataTaskWithURL(address) { data, response, error ->
                        val status = (response as? NSHTTPURLResponse)?.statusCode
                        when {
                            error != null -> {
                                continuation.resumeWithException(
                                    DataDownloadException("the download did not finish: ${error.localizedDescription}"),
                                )
                            }

                            status != null && status != HTTP_OK -> {
                                continuation.resumeWithException(
                                    DataDownloadException("the server answered $status"),
                                )
                            }

                            data == null -> {
                                continuation.resumeWithException(DataDownloadException("the file arrived empty"))
                            }

                            else -> {
                                continuation.resume(data)
                            }
                        }
                    }
                continuation.invokeOnCancellation { task.cancel() }
                task.resume()
            }
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return bytes
    }
}
