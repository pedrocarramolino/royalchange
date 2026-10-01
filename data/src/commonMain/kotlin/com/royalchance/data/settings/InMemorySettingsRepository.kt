package com.royalchance.data.settings

import com.royalchance.domain.settings.AppSettings
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.domain.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Preferencias en memoria (tests). */
class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun setTheme(theme: ThemePreference) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        state.update { it.copy(soundEnabled = enabled) }
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        state.update { it.copy(reducedMotion = enabled) }
    }
}
