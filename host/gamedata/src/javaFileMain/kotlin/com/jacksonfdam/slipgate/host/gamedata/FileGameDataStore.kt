package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Keeps each gate's files in its own folder under [root].
 *
 * Android's app-private directory and a desktop test directory behave the same way through
 * `java.io`, so both platforms share this. Reads and writes move tens of megabytes, which is why
 * they happen on the IO dispatcher rather than wherever the caller happened to be.
 */
public class FileGameDataStore(
    private val root: File,
) : GameDataStore {
    override suspend fun names(gate: String): Set<String> =
        withContext(Dispatchers.IO) {
            shelf(gate)
                .listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                ?.toSet() ?: emptySet()
        }

    override suspend fun write(
        gate: String,
        name: String,
        bytes: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val shelf = shelf(gate)
            if (!shelf.isDirectory && !shelf.mkdirs()) {
                throw IOException("could not create a folder for $gate at $shelf")
            }
            // Written beside the target and moved into place, so an interrupted write cannot leave a
            // half a file that would pass for game data.
            val target = File(shelf, safeStorageName(name))
            val partial = File(shelf, "${target.name}.part")
            partial.writeBytes(bytes)
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("could not store $name for $gate")
            }
        }
    }

    override suspend fun read(
        gate: String,
        name: String,
    ): ByteArray = withContext(Dispatchers.IO) { file(gate, name).readBytes() }

    override suspend fun size(
        gate: String,
        name: String,
    ): Long = withContext(Dispatchers.IO) { file(gate, name).length() }

    override suspend fun delete(
        gate: String,
        name: String?,
    ) {
        withContext(Dispatchers.IO) {
            if (name == null) {
                shelf(gate).deleteRecursively()
            } else {
                File(shelf(gate), safeStorageName(name)).delete()
            }
        }
    }

    private fun shelf(gate: String): File = File(root, safeStorageName(gate))

    private fun file(
        gate: String,
        name: String,
    ): File =
        File(shelf(gate), safeStorageName(name)).takeIf { it.isFile }
            ?: throw NoSuchElementException("$gate has no stored file named $name")
}
