package com.royalchance.shared

import com.royalchance.core.common.random.ProductionRandomGenerator
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.feature.auth.navigation.AuthDependencies
import com.royalchance.feature.blackjack.BlackjackDependencies
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Raíz de composición (inyección de dependencias manual): aquí, y solo aquí, se eligen las
 * implementaciones concretas. Cada plataforma crea una única instancia al arrancar: Firebase en
 * Android y la web, y repositorios en memoria en el escritorio de desarrollo.
 */
class AppGraph(
    val authRepository: AuthRepository,
    val economyRepository: EconomyRepository,
    val settingsRepository: SettingsRepository,
    val gameSessions: GameSessionStore,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val random: RandomGenerator = ProductionRandomGenerator(),
) {
    internal val authDependencies = AuthDependencies(authRepository, clock, timeZone)
    internal val blackjackDependencies = BlackjackDependencies(authRepository, economyRepository, gameSessions, random)
}

object AppInfo {
    const val VERSION = "0.1.0"
}
