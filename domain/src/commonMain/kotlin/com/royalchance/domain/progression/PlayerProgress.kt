package com.royalchance.domain.progression

import com.royalchance.domain.economy.Chips
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Progresión del jugador. Se guarda junto al saldo (en el mismo documento) porque cada ronda los
 * cambia a la vez: así una ronda sigue siendo una única escritura atómica.
 *
 * @property winStreak victorias seguidas ahora mismo: un empate no la rompe ni la alarga.
 * @property dailyStreak días seguidos cobrando el bono diario (sin tope; el premio sí lo tiene).
 * @property lastDailyDay día (en la zona horaria del dispositivo) del último bono cobrado.
 * @property unlocked logros desbloqueados; [claimed] los que ya han pagado su recompensa.
 */
data class PlayerProgress(
    val xp: Long = 0,
    val roundsPlayed: Long = 0,
    val wins: Long = 0,
    val losses: Long = 0,
    val pushes: Long = 0,
    val winStreak: Long = 0,
    val bestWinStreak: Long = 0,
    val highestBalance: Chips = Chips.ZERO,
    val dailyStreak: Long = 0,
    val lastDailyDay: LocalDate? = null,
    val lastDailyAt: Instant? = null,
    val unlocked: Set<AchievementId> = emptySet(),
    val claimed: Set<AchievementId> = emptySet(),
) {
    val level: Int get() = Levels.levelFor(xp)

    val levelProgress: LevelProgress get() = Levels.progress(xp)

    /** Logros desbloqueados cuya recompensa falta por recoger. */
    val claimable: Set<AchievementId> get() = unlocked - claimed
}

/** Progreso tras cerrar una ronda: experiencia, contadores y rachas. */
internal fun PlayerProgress.afterRound(stake: Chips, payout: Chips): PlayerProgress {
    val outcome = RoundOutcome.of(stake, payout)
    val streak = when (outcome) {
        RoundOutcome.Win -> winStreak + 1
        RoundOutcome.Loss -> 0
        RoundOutcome.Push -> winStreak
    }
    return copy(
        xp = xp + ExperienceRules.xpForRound(stake),
        roundsPlayed = roundsPlayed + 1,
        wins = wins + if (outcome == RoundOutcome.Win) 1 else 0,
        losses = losses + if (outcome == RoundOutcome.Loss) 1 else 0,
        pushes = pushes + if (outcome == RoundOutcome.Push) 1 else 0,
        winStreak = streak,
        bestWinStreak = maxOf(bestWinStreak, streak),
    )
}

/** Resultado de una ronda desde el punto de vista del jugador. */
enum class RoundOutcome {
    Win,
    Loss,
    Push,
    ;

    companion object {
        /** [payout] incluye la apuesta devuelta: ganar es cobrar más de lo apostado. */
        fun of(stake: Chips, payout: Chips): RoundOutcome = when {
            payout > stake -> Win
            payout < stake -> Loss
            else -> Push
        }
    }
}

/** Avisos para la interfaz tras una operación: subida de nivel y logros desbloqueados. */
sealed interface ProgressEvent {
    data class LevelUp(val level: Int) : ProgressEvent

    data class AchievementUnlocked(val id: AchievementId) : ProgressEvent
}
