package com.royalchance.data.settings

import com.royalchance.domain.settings.ThemePreference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistentSettingsRepositoryTest {

    @Test
    fun darkThemeByDefault() {
        assertEquals(ThemePreference.Dark, PersistentSettingsRepository(InMemoryKeyValueStore()).settings.value.theme)
    }

    @Test
    fun themeSurvivesRestart() = runTest {
        val store = InMemoryKeyValueStore()
        PersistentSettingsRepository(store).setTheme(ThemePreference.Light)

        val reopened = PersistentSettingsRepository(store)

        assertEquals(ThemePreference.Light, reopened.settings.value.theme)
    }

    @Test
    fun soundAndMotionPreferencesSurviveRestart() = runTest {
        val store = InMemoryKeyValueStore()
        val repository = PersistentSettingsRepository(store)
        assertEquals(true, repository.settings.value.soundEnabled)
        assertEquals(false, repository.settings.value.reducedMotion)

        repository.setSoundEnabled(false)
        repository.setReducedMotion(true)
        val reopened = PersistentSettingsRepository(store).settings.value

        assertEquals(false, reopened.soundEnabled)
        assertEquals(true, reopened.reducedMotion)
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() {
        val store = InMemoryKeyValueStore().apply { putString("settings.theme", "Neon") }

        assertEquals(ThemePreference.Dark, PersistentSettingsRepository(store).settings.value.theme)
    }
}
