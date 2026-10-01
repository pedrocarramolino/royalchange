package com.royalchance.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.royalchance.data.firebase.FirebaseEnvironment
import com.royalchance.data.firebase.WebFirebase
import com.royalchance.data.settings.BrowserKeyValueStore
import com.royalchance.data.settings.PersistentSettingsRepository
import com.royalchance.shared.App
import com.royalchance.shared.AppGraph
import kotlinx.coroutines.MainScope

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Ámbito de toda la vida de la página: mantiene viva la sesión de Firebase.
    val appScope = MainScope()
    val graph = AppGraph(
        authRepository = WebFirebase.authRepository(
            // Con emuladores, se buscan en el mismo equipo que sirve la página.
            environment = FirebaseEnvironment.fromConfig(emulatorHost = pageHostname()),
            scope = appScope,
        ),
        settingsRepository = PersistentSettingsRepository(BrowserKeyValueStore()),
    )
    ComposeViewport(viewportContainerId = "composeTarget") {
        App(graph)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun pageHostname(): String = js("globalThis.location.hostname || '127.0.0.1'")
