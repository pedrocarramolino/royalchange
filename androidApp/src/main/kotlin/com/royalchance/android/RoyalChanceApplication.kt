package com.royalchance.android

import android.app.Application
import com.royalchance.data.firebase.AndroidFirebase
import com.royalchance.data.firebase.FirebaseEnvironment
import com.royalchance.data.games.KeyValueGameSessionStore
import com.royalchance.data.history.LedgerHistoryRepository
import com.royalchance.data.settings.PersistentSettingsRepository
import com.royalchance.shared.AppGraph
import kotlinx.coroutines.MainScope

/** Raíz de dependencias de Android: una sola instancia durante toda la vida del proceso. */
class RoyalChanceApplication : Application() {

    private val appScope = MainScope()

    val graph: AppGraph by lazy {
        val firebase = AndroidFirebase.repositories(
            context = this,
            environment = FirebaseEnvironment.fromConfig(emulatorHost = ANDROID_EMULATOR_HOST),
            scope = appScope,
        )
        val preferences = SharedPreferencesKeyValueStore(this)
        AppGraph(
            authRepository = firebase.auth,
            economyRepository = firebase.economy,
            settingsRepository = PersistentSettingsRepository(preferences),
            gameSessions = KeyValueGameSessionStore(preferences),
            historyRepository = LedgerHistoryRepository(firebase.auth, firebase.ledger, preferences),
        )
    }

    private companion object {
        /** Desde el emulador de Android, 10.0.2.2 es el localhost del ordenador anfitrión. */
        const val ANDROID_EMULATOR_HOST = "10.0.2.2"
    }
}
