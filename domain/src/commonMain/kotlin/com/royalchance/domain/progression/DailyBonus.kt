package com.royalchance.domain.progression

import com.royalchance.domain.economy.Chips
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Instant

/**
 * Bono diario por día natural en la zona horaria del dispositivo: se cobra una vez al día. Si el
 * último cobro fue ayer, la racha sube en uno; si se salta un día, vuelve a empezar. El premio es
 * 500 + 200 · (día − 1), con tope a partir del día 7 (1.700).
 *
 * Las reglas de seguridad de Firestore repiten el cálculo y además comprueban el día con la hora
 * del servidor.
 */
object DailyBonusRules {

    const val MAX_REWARD_DAY: Long = 7

    fun rewardFor(streakDay: Long): Chips {
        require(streakDay >= 1) { "Día de racha no válido: $streakDay" }
        return Chips(500 + 200 * (minOf(streakDay, MAX_REWARD_DAY) - 1))
    }

    /** Día de racha que se cobraría hoy. */
    fun streakDayFor(progress: PlayerProgress, today: LocalDate): Long =
        if (progress.lastDailyDay == today.minus(1, DateTimeUnit.DAY)) progress.dailyStreak + 1 else 1
}

sealed interface DailyBonusStatus {
    /** Se puede cobrar hoy: [streakDay] es el día de racha que se alcanzaría. */
    data class Available(val streakDay: Long, val reward: Chips) : DailyBonusStatus

    /** Ya cobrado hoy. Mañana tocará [nextStreakDay] y [nextReward] si no se falta. */
    data class ClaimedToday(val streakDay: Long, val nextStreakDay: Long, val nextReward: Chips) : DailyBonusStatus

    /** El reloj del dispositivo marca una hora anterior al último cobro: bloqueado hasta corregirlo. */
    data object ClockMovedBack : DailyBonusStatus
}

fun PlayerProgress.dailyBonusStatus(today: LocalDate, now: Instant): DailyBonusStatus {
    val lastDay = lastDailyDay
    return when {
        lastDailyAt != null && now < lastDailyAt -> DailyBonusStatus.ClockMovedBack
        lastDay != null && today < lastDay -> DailyBonusStatus.ClockMovedBack
        lastDay == today -> DailyBonusStatus.ClaimedToday(
            streakDay = dailyStreak,
            nextStreakDay = dailyStreak + 1,
            nextReward = DailyBonusRules.rewardFor(dailyStreak + 1),
        )
        else -> DailyBonusRules.streakDayFor(this, today).let { day ->
            DailyBonusStatus.Available(streakDay = day, reward = DailyBonusRules.rewardFor(day))
        }
    }
}
