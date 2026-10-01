package com.royalchance.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.data.games.KeyValueGameSessionStore
import com.royalchance.data.history.LedgerHistoryRepository
import com.royalchance.data.settings.JvmPreferencesKeyValueStore
import com.royalchance.data.settings.PersistentSettingsRepository
import com.royalchance.shared.App
import com.royalchance.shared.AppGraph
import kotlinx.coroutines.MainScope
import java.awt.Dimension

fun main() {
    // Entorno de desarrollo: cuentas y fichas en memoria (se pierden al cerrar) y preferencias persistentes.
    val appScope = MainScope()
    val authRepository = InMemoryAuthRepository()
    val preferences = JvmPreferencesKeyValueStore()
    val economyRepository = InMemoryEconomyRepository(authRepository, appScope)
    val graph = AppGraph(
        authRepository = authRepository,
        economyRepository = economyRepository,
        settingsRepository = PersistentSettingsRepository(preferences),
        gameSessions = KeyValueGameSessionStore(preferences),
        historyRepository = LedgerHistoryRepository(authRepository, economyRepository, preferences),
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Royal Chance",
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
        ) {
            // Mínimo de tamaño móvil: permite probar los layouts compacto, medio y expandido redimensionando.
            LaunchedEffect(Unit) { window.minimumSize = Dimension(360, 640) }
            App(graph)
        }
    }
}
