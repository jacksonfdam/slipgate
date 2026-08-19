package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipArchiveTest {
    @Test
    fun everyEntryIsListed() {
        val archive =
            ZipArchive(
                syntheticZip(
                    listOf("readme.txt" to "hello".encodeToByteArray(), "freedoom1.wad" to byteArrayOf(1, 2)),
                ),
            )

        assertEquals(listOf("readme.txt", "freedoom1.wad"), archive.entries.map { it.name })
    }

    @Test
    fun anEntryIsFoundByHowItsNameEnds() {
        val archive = ZipArchive(syntheticZip(listOf("freedoom-0.13.0/freedoom1.wad" to byteArrayOf(9))))

        val found = assertNotNull(archive.find("freedoom1.wad"))
        assertEquals("freedoom-0.13.0/freedoom1.wad", found.name)
    }

    @Test
    fun aStoredEntryComesBackAsItWentIn() =
        runTest {
            val content = ByteArray(300) { (it % 251).toByte() }
            val archive = ZipArchive(syntheticZip(listOf("data.bin" to content)))

            assertContentEquals(content, archive.read(assertNotNull(archive.find("data.bin"))))
        }

    /** The index is searched backwards from the end, so a comment after it must not hide it. */
    @Test
    fun anArchiveWithACommentIsStillReadable() =
        runTest {
            val archive =
                ZipArchive(syntheticZip(listOf("a.wad" to byteArrayOf(4, 5)), comment = "packed by hand"))

            assertContentEquals(byteArrayOf(4, 5), archive.read(archive.entries.single()))
        }

    @Test
    fun anEntryThatIsNotThereIsNotFound() {
        val archive = ZipArchive(syntheticZip(listOf("readme.txt" to byteArrayOf(1))))

        assertNull(archive.find(".wad"))
    }

    @Test
    fun somethingThatIsNotAnArchiveSaysSo() {
        val failure =
            assertFailsWith<ZipException> { ZipArchive("this is a photograph, honestly".encodeToByteArray()) }

        assertTrue("zip" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun aFileTooSmallToBeAnArchiveSaysSo() {
        assertFailsWith<ZipException> { ZipArchive(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun aDamagedFileListSaysWhere() =
        runTest {
            val bytes = syntheticZip(listOf("a.wad" to byteArrayOf(1)))
            val directoryStart = bytes.size - 22 - 46 - "a.wad".length
            bytes[directoryStart] = 0

            assertFailsWith<ZipException> { ZipArchive(bytes) }
        }
}
