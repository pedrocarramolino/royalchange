package com.royalchance.domain.economy

import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Operaciones con intención que admite el monedero. No existe "fijar el saldo". */
sealed interface EconomyOperation {

    /** Abre una ronda por turnos o añade fichas a la ronda abierta del mismo juego. */
    data class PlaceBet(val game: GameType, val stake: Chips) : EconomyOperation

    /** Cierra la ronda abierta. [payout] incluye la apuesta devuelta; 0 si se pierde. */
    data class SettleRound(val payout: Chips) : EconomyOperation

    /** Ronda instantánea: el resultado ya se conoce y se contabiliza de una vez, antes de animarlo. */
    data class InstantRound(val game: GameType, val stake: Chips, val payout: Chips) : EconomyOperation

    data object ClaimRescue : EconomyOperation

    /** Bono diario de [today], el día actual en la zona horaria del dispositivo. */
    data class ClaimDailyBonus(val today: LocalDate) : EconomyOperation

    /** Cobra la recompensa de un logro ya desbloqueado. */
    data class ClaimAchievement(val id: AchievementId) : EconomyOperation
}

sealed interface EconomyError {
    /** Monedero aún sin cargar, sin sesión o sin acceso a los datos. */
    data object WalletUnavailable : EconomyError

    data object BelowMinimumBet : EconomyError

    /** La ronda superaría [EconomyRules.MAXIMUM_ROUND_STAKE]. */
    data object AboveMaximumStake : EconomyError

    data object InsufficientFunds : EconomyError

    /** Hay una ronda abierta de otro juego: debe liquidarse antes. */
    data class RoundInProgress(val game: GameType) : EconomyError

    data object NoOpenRound : EconomyError

    /** El pago supera el máximo posible para la apuesta: indica un error del juego. */
    data object PayoutTooHigh : EconomyError

    /** Hay fichas suficientes (o en juego): no procede la recarga. */
    data object RescueNotNeeded : EconomyError

    data class RescueCoolingDown(val availableAt: Instant) : EconomyError

    data object DailyBonusAlreadyClaimed : EconomyError

    /** El reloj del dispositivo marca una hora anterior al último cobro. */
    data object DailyBonusClockMovedBack : EconomyError

    data object AchievementLocked : EconomyError

    data object AchievementAlreadyClaimed : EconomyError
}
