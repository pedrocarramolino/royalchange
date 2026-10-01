package com.royalchance.android

import android.content.Context
import com.royalchance.data.settings.KeyValueStore

/** Preferencias del dispositivo en SharedPreferences. */
internal class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {
    private val preferences = context.getSharedPreferences("royalchance.settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
