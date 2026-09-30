package com.royalchance.domain.settings

import kotlinx.coroutines.flow.StateFlow

enum class ThemePreference { Dark, Light, System }

/** Preferencias del dispositivo. El modo oscuro es el predeterminado: encaja con el concepto de casino. */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.Dark,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)
}
