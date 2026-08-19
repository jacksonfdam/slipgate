package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import java.util.zip.ZipEntry as JavaZipEntry

/**
 * Reads a genuinely compressed archive, which the synthetic fixtures cannot produce: they store their
 * entries so the tests need no compressor of their own. This is where the deflate path is exercised.
 */
class DeflatedArchiveTest {
    @Test
    fun aDeflatedEntryIsExpanded() =
        runTest {
            // Repetitive on purpose: random bytes would compress to something larger than themselves.
            val content = ByteArray(64 * 1024) { (it % 16).toByte() }
            val archive = ZipArchive(deflatedZip("freedoom-0.13.0/freedoom1.wad", content))

            val entry = assertNotNull(archive.find("freedoom1.wad"))
            assertContentEquals(content, archive.read(entry))
        }

    @Test
    fun aTruncatedDeflatedEntryIsReported() =
        runTest {
            val content = ByteArray(4096) { (it % 7).toByte() }
            val bytes = deflatedZip("data.bin", content)
            val entry = assertNotNull(ZipArchive(bytes).find("data.bin"))
            // Half the compressed bytes: what a download that stopped would leave behind.
            val damaged =
                bytes.copyOf(bytes.size).also { whole ->
                    val start = whole.size - whole.size / 2
                    for (index in start until whole.size) {
                        whole[index] = 0
                    }
                }

            runCatching { ZipArchive(damaged).read(entry) }.also { outcome ->
                assertNotNull(outcome.exceptionOrNull(), "reading damaged bytes reported nothing")
            }
        }
}

private fun deflatedZip(
    name: String,
    content: ByteArray,
): ByteArray {
    val collected = ByteArrayOutputStream()
    ZipOutputStream(collected).use { zip ->
        zip.putNextEntry(JavaZipEntry(name).apply { method = java.util.zip.ZipEntry.DEFLATED })
        zip.write(content)
        zip.closeEntry()
    }
    return collected.toByteArray()
}
