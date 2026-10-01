package com.royalchance.shared

import com.royalchance.core.audio.SoundPlayer
import com.royalchance.core.audio.platformSoundPlayer
import com.royalchance.core.common.random.ProductionRandomGenerator
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.history.HistoryRepository
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.feature.auth.navigation.AuthDependencies
import com.royalchance.feature.blackjack.BlackjackDependencies
import com.royalchance.feature.dice.DiceDependencies
import com.royalchance.feature.history.HistoryDependencies
import com.royalchance.feature.poker.PokerDependencies
import com.royalchance.feature.roulette.RouletteDependencies
import com.royalchance.feature.slots.SlotsDependencies
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
    val historyRepository: HistoryRepository,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val random: RandomGenerator = ProductionRandomGenerator(),
    val soundPlayer: SoundPlayer = platformSoundPlayer(),
) {
    internal val authDependencies = AuthDependencies(authRepository, clock, timeZone)
    internal val blackjackDependencies = BlackjackDependencies(authRepository, economyRepository, gameSessions, random)
    internal val rouletteDependencies = RouletteDependencies(authRepository, economyRepository, gameSessions, random)
    internal val slotsDependencies = SlotsDependencies(authRepository, economyRepository, gameSessions, random)
    internal val diceDependencies = DiceDependencies(authRepository, economyRepository, gameSessions, random)
    internal val pokerDependencies = PokerDependencies(authRepository, economyRepository, gameSessions, random)
    internal val historyDependencies = HistoryDependencies(historyRepository, economyRepository, timeZone)
}

object AppInfo {
    /** Debe coincidir con versionName de androidApp. */
    const val VERSION = "1.0.0"
}
