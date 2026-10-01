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
    fun unknownStoredValueFallsBackToDefault() {
        val store = InMemoryKeyValueStore().apply { putString("settings.theme", "Neon") }

        assertEquals(ThemePreference.Dark, PersistentSettingsRepository(store).settings.value.theme)
    }
}
