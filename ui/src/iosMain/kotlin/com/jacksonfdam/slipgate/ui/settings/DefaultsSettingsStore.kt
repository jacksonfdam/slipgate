package com.jacksonfdam.slipgate.ui.settings

import platform.Foundation.NSUserDefaults

/** User defaults, which is where an iOS app's preferences belong and what the system backs up. */
public class DefaultsSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsStore {
    override fun string(key: String): String? = defaults.stringForKey(key)

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, key)
        }
    }

    override fun float(key: String): Float? =
        if (defaults.objectForKey(key) == null) null else defaults.floatForKey(key)

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        defaults.setFloat(value, key)
    }

    override fun boolean(key: String): Boolean? =
        if (defaults.objectForKey(key) == null) null else defaults.boolForKey(key)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        defaults.setBool(value, key)
    }
}
