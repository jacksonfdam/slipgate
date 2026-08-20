package com.jacksonfdam.slipgate.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The painted interface art: full-screen backdrops and gate covers, shipped with the shell.
 *
 * Every name resolves to one committed WebP under `files/backdrops/`. How the bytes are read
 * differs by platform because how they are packaged differs: Compose resources carry them on iOS
 * and the web, and Android reads them as plain java resources, which is what an AAR carries — the
 * same arrangement the mars gate uses for its engine module.
 */
internal object Backdrops {
    /**
     * The gates that have painted art. Anything else falls back to the neutral select backdrop.
     *
     * Art arrives before the gate it belongs to — `chthon` and `macil` are both here with no module
     * behind them yet. An entry with no gate is inert, because nothing asks for a backdrop by a name
     * the registry never produced.
     */
    private val painted = setOf("mars", "chthon", "corvus", "korax", "macil")

    const val SPLASH: String = "bg_splash"
    const val SELECT: String = "bg_select"
    const val SETTINGS: String = "bg_settings"
    const val CREDITS: String = "bg_credits"

    /** The full-screen backdrop behind the rack while [gateId] is the focused card. */
    fun forGate(gateId: String?): String = if (gateId in painted) "bg_$gateId" else SELECT

    /** The painted cover for a gate card, or null when the gate has none. */
    fun coverFor(gateId: String): String? = if (gateId in painted) "cover_$gateId" else null
}

/**
 * The decoded image for [name], or null while it loads and forever if the art is missing.
 * Decoded once per name for the life of the process; the shell reuses a handful of images
 * across every recomposition, so the cache is never evicted.
 */
@Composable
internal fun rememberBackdrop(name: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = BackdropCache.peek(name), key1 = name) {
        value = if (name == null) null else BackdropCache.load(name)
    }
    return image
}

private object BackdropCache {
    private val lock = Mutex()
    private val images = mutableMapOf<String, ImageBitmap?>()

    fun peek(name: String?): ImageBitmap? = name?.let { images[it] }

    suspend fun load(name: String): ImageBitmap? =
        lock.withLock {
            images.getOrPut(name) {
                backdropBytes("files/backdrops/$name.webp")?.decodeToImageBitmap()
            }
        }
}

/** The raw bytes of one backdrop, or null when the build does not carry it. */
internal expect suspend fun backdropBytes(path: String): ByteArray?
