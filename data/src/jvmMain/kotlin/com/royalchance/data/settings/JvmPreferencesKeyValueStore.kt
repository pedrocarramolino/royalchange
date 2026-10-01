package com.royalchance.data.settings

import java.util.prefs.Preferences

/** Preferencias del escritorio mediante `java.util.prefs` (registro de Windows, plist en macOS). */
class JvmPreferencesKeyValueStore(nodePath: String = "/com/royalchance") : KeyValueStore {
    private val preferences: Preferences = Preferences.userRoot().node(nodePath)

    override fun getString(key: String): String? = preferences.get(key, null)

    override fun putString(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }

    override fun remove(key: String) {
        preferences.remove(key)
        preferences.flush()
    }
}
