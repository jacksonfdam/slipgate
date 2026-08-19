package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Keeps each gate's files in a folder inside the app's Documents directory.
 *
 * Documents rather than a caches folder for two reasons: the system may empty caches whenever it
 * likes, and the app declares `UIFileSharingEnabled`, which makes exactly this directory the place a
 * player drops an IWAD into from the Files app.
 */
@OptIn(ExperimentalForeignApi::class)
public class DocumentsGameDataStore(
    private val folder: String = "gamedata",
) : GameDataStore {
    private val manager = NSFileManager.defaultManager

    override suspend fun names(gate: String): Set<String> {
        val contents = manager.contentsOfDirectoryAtPath(shelf(gate), null) ?: return emptySet()
        return contents.mapNotNull { it as? String }.filter { !it.endsWith(PARTIAL_SUFFIX) }.toSet()
    }

    override suspend fun write(
        gate: String,
        name: String,
        bytes: ByteArray,
    ) {
        val shelf = shelf(gate)
        manager.createDirectoryAtPath(shelf, true, null, null)
        val target = "$shelf/${safeStorageName(name)}"
        val partial = "$target$PARTIAL_SUFFIX"
        // Written beside the target and moved into place, so an interrupted write cannot leave half
        // a file that would pass for game data.
        val data =
            memScoped {
                NSData.dataWithBytes(allocArrayOf(bytes), bytes.size.toULong())
            }
        if (!data.writeToFile(partial, true)) {
            error("could not store $name for $gate")
        }
        manager.removeItemAtPath(target, null)
        if (!manager.moveItemAtPath(partial, target, null)) {
            manager.removeItemAtPath(partial, null)
            error("could not store $name for $gate")
        }
    }

    override suspend fun read(
        gate: String,
        name: String,
    ): ByteArray {
        val data = NSData.dataWithContentsOfFile(path(gate, name)) ?: missing(gate, name)
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
        return bytes
    }

    override suspend fun size(
        gate: String,
        name: String,
    ): Long {
        val attributes = manager.attributesOfItemAtPath(path(gate, name), null) ?: missing(gate, name)
        return (attributes[NSFileSize] as? Number)?.toLong() ?: 0L
    }

    override suspend fun delete(
        gate: String,
        name: String?,
    ) {
        if (name == null) {
            manager.removeItemAtPath(shelf(gate), null)
        } else {
            manager.removeItemAtPath(path(gate, name), null)
        }
    }

    private fun documents(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
            ?: error("the app has no documents directory")

    private fun shelf(gate: String): String = "${documents()}/$folder/${safeStorageName(gate)}"

    private fun path(
        gate: String,
        name: String,
    ): String = "${shelf(gate)}/${safeStorageName(name)}"

    private fun missing(
        gate: String,
        name: String,
    ): Nothing = throw NoSuchElementException("$gate has no stored file named $name")

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}
