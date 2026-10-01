package com.royalchance.domain.settings

import kotlinx.coroutines.flow.StateFlow

enum class ThemePreference { Dark, Light, System }

/** Preferencias del dispositivo. El modo oscuro es el predeterminado: encaja con el concepto de casino. */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.Dark,
    /** Efectos de sonido de las mesas. */
    val soundEnabled: Boolean = true,
    /** Animaciones reducidas: sin celebraciones ni movimientos decorativos. */
    val reducedMotion: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setReducedMotion(enabled: Boolean)
}
