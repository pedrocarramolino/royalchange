package com.royalchance.domain.progression

import com.royalchance.domain.economy.Chips

/**
 * Logros. Los nombres son ids estables: se guardan en la base de datos y las reglas de seguridad
 * comprueban la condición de cada uno al desbloquearlo. Añadir un logro es añadir su entrada al
 * catálogo, su condición en `firestore.rules` y sus textos.
 */
enum class AchievementId {
    FirstWin,
    Rounds10,
    Rounds100,
    Rounds1000,
    Wins50,
    WinStreak5,
    WinStreak10,
    Level5,
    Level10,
    Level25,
    Balance50k,
    Balance250k,
    DailyStreak7,
    DailyStreak30,
}

/** Condición tipada de un logro: se evalúa sobre el progreso del jugador. */
sealed interface AchievementCondition {
    /** Valor actual del jugador y valor objetivo, para mostrar el avance. */
    fun progressOf(progress: PlayerProgress): Pair<Long, Long>

    fun isMet(progress: PlayerProgress): Boolean = progressOf(progress).let { (current, target) -> current >= target }

    data class RoundsPlayed(val rounds: Long) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.roundsPlayed to rounds
    }

    data class Wins(val wins: Long) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.wins to wins
    }

    /** Mejor racha de victorias seguidas alcanzada. */
    data class WinStreak(val streak: Long) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.bestWinStreak to streak
    }

    data class ReachLevel(val level: Int) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.level.toLong() to level.toLong()
    }

    /** Saldo máximo alcanzado alguna vez. */
    data class ReachBalance(val balance: Chips) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.highestBalance.amount to balance.amount
    }

    /** Días seguidos cobrando el bono diario. */
    data class DailyStreak(val days: Long) : AchievementCondition {
        override fun progressOf(progress: PlayerProgress) = progress.dailyStreak to days
    }
}

data class Achievement(
    val id: AchievementId,
    val condition: AchievementCondition,
    val reward: Chips,
)

object Achievements {

    val catalog: List<Achievement> = listOf(
        Achievement(AchievementId.FirstWin, AchievementCondition.Wins(1), Chips(250)),
        Achievement(AchievementId.Rounds10, AchievementCondition.RoundsPlayed(10), Chips(250)),
        Achievement(AchievementId.Rounds100, AchievementCondition.RoundsPlayed(100), Chips(1_000)),
        Achievement(AchievementId.Rounds1000, AchievementCondition.RoundsPlayed(1_000), Chips(5_000)),
        Achievement(AchievementId.Wins50, AchievementCondition.Wins(50), Chips(1_000)),
        Achievement(AchievementId.WinStreak5, AchievementCondition.WinStreak(5), Chips(1_000)),
        Achievement(AchievementId.WinStreak10, AchievementCondition.WinStreak(10), Chips(5_000)),
        Achievement(AchievementId.Level5, AchievementCondition.ReachLevel(5), Chips(500)),
        Achievement(AchievementId.Level10, AchievementCondition.ReachLevel(10), Chips(2_000)),
        Achievement(AchievementId.Level25, AchievementCondition.ReachLevel(25), Chips(10_000)),
        Achievement(AchievementId.Balance50k, AchievementCondition.ReachBalance(Chips(50_000)), Chips(2_500)),
        Achievement(AchievementId.Balance250k, AchievementCondition.ReachBalance(Chips(250_000)), Chips(10_000)),
        Achievement(AchievementId.DailyStreak7, AchievementCondition.DailyStreak(7), Chips(1_000)),
        Achievement(AchievementId.DailyStreak30, AchievementCondition.DailyStreak(30), Chips(5_000)),
    )

    private val byId: Map<AchievementId, Achievement> = catalog.associateBy { it.id }

    fun of(id: AchievementId): Achievement = byId.getValue(id)

    /** Logros cuya condición se cumple y aún no estaban desbloqueados, en orden de catálogo. */
    fun newlyUnlocked(progress: PlayerProgress): List<AchievementId> =
        catalog.filter { it.id !in progress.unlocked && it.condition.isMet(progress) }.map { it.id }
}
