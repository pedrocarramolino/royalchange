package com.royalchance.data.settings

import com.royalchance.domain.settings.AppSettings
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.domain.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Almacén clave-valor del dispositivo. Cada plataforma aporta el suyo:
 * SharedPreferences (Android), localStorage (web) y java.util.prefs (escritorio).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Preferencias guardadas en el dispositivo. Se leen de forma síncrona al arrancar para que el
 * primer frame ya use el tema correcto (sin destello del tema equivocado).
 */
class PersistentSettingsRepository(private val store: KeyValueStore) : SettingsRepository {

    private val state = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun setTheme(theme: ThemePreference) {
        store.putString(KEY_THEME, theme.name)
        state.update { it.copy(theme = theme) }
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            // Un valor desconocido (p. ej. de una versión futura) no debe romper el arranque.
            theme = store.getString(KEY_THEME)?.let { name -> ThemePreference.entries.firstOrNull { it.name == name } }
                ?: defaults.theme,
        )
    }

    private companion object {
        const val KEY_THEME = "settings.theme"
    }
}

/** Almacén volátil: tests y entornos sin persistencia. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
