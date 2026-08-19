package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fetches a real URL through the platform's own client, which no fake can stand in for: redirects,
 * a content length that must match, and a server that may answer anything.
 *
 * It reaches the network, so it never runs unless a URL is supplied, and never in CI:
 *
 * ```
 * ./gradlew :host:gamedata:jvmTest -Pslipgate.downloadUrl=https://example.org/file
 * ```
 */
class PlatformDownloadTest {
    private val url: String? = System.getenv("SLIPGATE_DOWNLOAD_URL")?.takeIf { it.isNotBlank() }

    @Test
    fun aSuppliedUrlIsFetchedInFull() =
        runTest {
            val address =
                url ?: return@runTest run {
                    println("skipping: set -Pslipgate.downloadUrl to fetch something real")
                }
            var lastReceived = 0L
            var reportedTotal: Long? = null

            val bytes =
                platformDataDownload().fetch(address) { received, total ->
                    lastReceived = received
                    reportedTotal = total
                }

            assertTrue(bytes.isNotEmpty(), "nothing arrived from $address")
            assertTrue(lastReceived == bytes.size.toLong(), "progress ended at $lastReceived of ${bytes.size}")
            assertTrue(
                reportedTotal == null || reportedTotal == bytes.size.toLong(),
                "the server said $reportedTotal and ${bytes.size} arrived",
            )
        }
}
