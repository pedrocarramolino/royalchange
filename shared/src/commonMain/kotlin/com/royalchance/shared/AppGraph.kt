package com.royalchance.shared

import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.settings.InMemorySettingsRepository
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.feature.auth.navigation.AuthDependencies
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Raíz de composición (inyección de dependencias manual): aquí, y solo aquí, se eligen las
 * implementaciones concretas. Cada plataforma crea una única instancia al arrancar.
 */
class AppGraph(
    val authRepository: AuthRepository,
    val settingsRepository: SettingsRepository,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    internal val authDependencies = AuthDependencies(authRepository, clock, timeZone)

    companion object {
        /**
         * Datos en memoria: Fase 3, escritorio de desarrollo y pruebas manuales.
         * En la Fase 4, Android y la web usarán Firebase.
         */
        fun inMemory(): AppGraph = AppGraph(
            authRepository = InMemoryAuthRepository(),
            settingsRepository = InMemorySettingsRepository(),
        )
    }
}

object AppInfo {
    const val VERSION = "0.1.0"
}
