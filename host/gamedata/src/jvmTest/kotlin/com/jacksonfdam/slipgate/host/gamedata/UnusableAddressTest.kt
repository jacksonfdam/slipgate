package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Addresses the parser refuses must come back as a failed download, never as a crash. The first
 * three are what a half-typed shelf address in Settings actually produces; "https:" is the one
 * that reached a phone as a stored setting and took the app down on every launch, because
 * `URI` answers it with a checked `URISyntaxException` rather than `IllegalArgumentException`.
 */
class UnusableAddressTest {
    @Test
    fun anUnparseableAddressIsARefusedDownload() =
        runTest {
            for (address in listOf("https:", "http:", "not an address", "")) {
                assertFailsWith<DataDownloadException>("\"$address\" should be refused, not thrown") {
                    platformDataDownload().fetch(address) { _, _ -> }
                }
            }
        }
}
