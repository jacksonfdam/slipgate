@file:OptIn(ExperimentalWasmJsInterop::class)
// The helpers below have JavaScript bodies, which static analysis cannot see read their parameters.
@file:Suppress("UnusedParameter")

package com.jacksonfdam.slipgate.ui.settings

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Local storage, scoped to the origin like the game data beside it.
 *
 * Strings all the way down, because that is what a browser stores; numbers and flags are parsed back
 * on the way out, and anything unparseable is treated as absent, so a corrupted entry falls back to
 * the default rather than failing the launch.
 */
public class LocalStorageSettingsStore : SettingsStore {
    override fun string(key: String): String? = readItem(prefixed(key))

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) removeItem(prefixed(key)) else writeItem(prefixed(key), value)
    }

    override fun float(key: String): Float? = string(key)?.toFloatOrNull()

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        putString(key, value.toString())
    }

    override fun boolean(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        putString(key, value.toString())
    }

    private fun prefixed(key: String): String = "slipgate.$key"
}

private fun readItem(key: String): String? = js("localStorage.getItem(key)")

private fun writeItem(
    key: String,
    value: String,
) {
    js("localStorage.setItem(key, value)")
}

private fun removeItem(key: String) {
    js("localStorage.removeItem(key)")
}
