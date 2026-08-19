package com.jacksonfdam.slipgate.ui.settings

import android.content.Context

private const val FILE = "slipgate.settings"

/** Preferences: the store Android already has, and the one a player's backup already carries. */
public class AndroidSettingsStore(
    context: Context,
) : SettingsStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun string(key: String): String? = preferences.getString(key, null)

    override fun putString(
        key: String,
        value: String?,
    ) {
        preferences.edit().putString(key, value).apply()
    }

    override fun float(key: String): Float? = if (preferences.contains(key)) preferences.getFloat(key, 0f) else null

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        preferences.edit().putFloat(key, value).apply()
    }

    override fun boolean(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        preferences.edit().putBoolean(key, value).apply()
    }
}
