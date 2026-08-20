package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

private const val BUFFER_BYTES = 64 * 1024
private const val TIMEOUT_MILLIS = 30_000
private const val HTTP_OK = 200

/** Fetches with the JDK's own client, which Android also ships. */
public actual fun platformDataDownload(): DataDownload = UrlConnectionDownload()

internal class UrlConnectionDownload : DataDownload {
    override suspend fun fetch(
        url: String,
        onProgress: DownloadProgress,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val connection = open(url)
            try {
                if (connection.responseCode != HTTP_OK) {
                    throw DataDownloadException("the server answered ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 }
                read(connection, total, onProgress)
            } catch (failure: IOException) {
                throw DataDownloadException("the download did not finish: ${failure.message}", failure)
            } catch (refused: SecurityException) {
                // Android answers a request the app has no permission for by throwing this, and a
                // download that cannot start is a download that failed rather than a crash.
                throw DataDownloadException("this build may not use the network: ${refused.message}", refused)
            } finally {
                connection.disconnect()
            }
        }

    private fun open(url: String): HttpURLConnection {
        val address: URL =
            try {
                URI(url).toURL()
            } catch (malformed: URISyntaxException) {
                // URI answers a bare "https:" with this checked exception, not with
                // IllegalArgumentException — and unhandled it took the whole app down at start-up.
                unusable(url, malformed)
            } catch (malformed: MalformedURLException) {
                unusable(url, malformed)
            } catch (malformed: IllegalArgumentException) {
                unusable(url, malformed)
            }
        val connection = address.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun unusable(
        url: String,
        cause: Exception,
    ): Nothing = throw DataDownloadException("$url is not a usable address", cause)

    private fun read(
        connection: HttpURLConnection,
        total: Long?,
        onProgress: DownloadProgress,
    ): ByteArray {
        val collected = ByteArrayOutputStream(total?.toInt() ?: BUFFER_BYTES)
        val buffer = ByteArray(BUFFER_BYTES)
        connection.inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) {
                    break
                }
                collected.write(buffer, 0, read)
                onProgress(collected.size().toLong(), total)
            }
        }
        if (total != null && collected.size().toLong() != total) {
            throw DataDownloadException("the file stopped at ${collected.size()} of $total bytes")
        }
        return collected.toByteArray()
    }
}
