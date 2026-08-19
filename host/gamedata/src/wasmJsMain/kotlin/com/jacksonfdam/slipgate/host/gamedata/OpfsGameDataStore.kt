@file:OptIn(ExperimentalWasmJsInterop::class)
// The helpers below have JavaScript bodies, which static analysis cannot see reads their parameters.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.host.gamedata

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.get

/**
 * Keeps each gate's files in the origin's private file system.
 *
 * The private file system is the browser's own storage for an origin: it is not the download folder,
 * no page can read another origin's copy, and it survives a reload. The work is done in small
 * asynchronous helpers rather than in declared interfaces because the API is built on async
 * iteration, which reads far better as the few lines of JavaScript it actually is.
 */
public class OpfsGameDataStore : GameDataStore {
    override suspend fun names(gate: String): Set<String> {
        val listed = opfsNames(safeStorageName(gate)).await()
        return (0 until listed.length)
            .mapNotNull { index -> listed[index]?.toString() }
            .filter { !it.endsWith(PARTIAL_SUFFIX) }
            .toSet()
    }

    override suspend fun write(
        gate: String,
        name: String,
        bytes: ByteArray,
    ) {
        opfsWrite(safeStorageName(gate), safeStorageName(name), bytes.toInt8Array()).await()
    }

    override suspend fun read(
        gate: String,
        name: String,
    ): ByteArray {
        val stored =
            opfsRead(safeStorageName(gate), safeStorageName(name)).await()
                ?: throw NoSuchElementException("$gate has no stored file named $name")
        return stored.toByteArray()
    }

    override suspend fun size(
        gate: String,
        name: String,
    ): Long {
        val reported = opfsSize(safeStorageName(gate), safeStorageName(name)).await()
        return reported.toDouble().toLong()
    }

    override suspend fun delete(
        gate: String,
        name: String?,
    ) {
        opfsDelete(safeStorageName(gate), name?.let(::safeStorageName)).await()
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}

/**
 * Returns a store backed by the browser's private file system, or one held in memory when the
 * browser does not offer it. A player in a private window can still supply a file and play; the file
 * simply does not outlive the tab.
 */
public fun openWebGameDataStore(): GameDataStore = if (opfsAvailable()) OpfsGameDataStore() else InMemoryGameDataStore()

private fun opfsAvailable(): Boolean =
    js("typeof navigator !== 'undefined' && !!(navigator.storage && navigator.storage.getDirectory)")

private fun opfsNames(gate: String): Promise<JsArray<JsString>> =
    js(
        """(async () => {
             const root = await navigator.storage.getDirectory();
             let directory;
             try {
               directory = await root.getDirectoryHandle(gate, { create: false });
             } catch (missing) {
               return [];
             }
             const names = [];
             for await (const name of directory.keys()) { names.push(name); }
             return names;
           })()""",
    )

// Written beside the target and moved into place, so an interrupted write cannot leave half a file
// that would pass for game data. The private file system has no rename, so the move is a copy of the
// finished bytes followed by a delete.
private fun opfsWrite(
    gate: String,
    name: String,
    bytes: Int8Array,
): Promise<JsAny?> =
    js(
        """(async () => {
             const root = await navigator.storage.getDirectory();
             const directory = await root.getDirectoryHandle(gate, { create: true });
             const partial = await directory.getFileHandle(name + '.part', { create: true });
             const writable = await partial.createWritable();
             await writable.write(bytes);
             await writable.close();
             const target = await directory.getFileHandle(name, { create: true });
             const finished = await target.createWritable();
             await finished.write(await (await partial.getFile()).arrayBuffer());
             await finished.close();
             await directory.removeEntry(name + '.part');
             return null;
           })()""",
    )

private fun opfsRead(
    gate: String,
    name: String,
): Promise<Int8Array?> =
    js(
        """(async () => {
             const root = await navigator.storage.getDirectory();
             try {
               const directory = await root.getDirectoryHandle(gate, { create: false });
               const handle = await directory.getFileHandle(name, { create: false });
               return new Int8Array(await (await handle.getFile()).arrayBuffer());
             } catch (missing) {
               return null;
             }
           })()""",
    )

private fun opfsSize(
    gate: String,
    name: String,
): Promise<JsNumber> =
    js(
        """(async () => {
             const root = await navigator.storage.getDirectory();
             try {
               const directory = await root.getDirectoryHandle(gate, { create: false });
               const handle = await directory.getFileHandle(name, { create: false });
               return (await handle.getFile()).size;
             } catch (missing) {
               return 0;
             }
           })()""",
    )

private fun opfsDelete(
    gate: String,
    name: String?,
): Promise<JsAny?> =
    js(
        """(async () => {
             const root = await navigator.storage.getDirectory();
             try {
               if (name === null) {
                 await root.removeEntry(gate, { recursive: true });
               } else {
                 const directory = await root.getDirectoryHandle(gate, { create: false });
                 await directory.removeEntry(name);
               }
             } catch (missing) {
               // Deleting what is not there is the state the caller asked for.
             }
             return null;
           })()""",
    )
